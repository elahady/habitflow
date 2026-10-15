<?php

namespace Tests\Feature;

use App\Models\FollowUp;
use App\Models\User;
use App\Support\EodSummary;
use App\Support\FollowUpSections;
use App\Support\WorkRecap;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

class WorkRecapTest extends TestCase
{
    use RefreshDatabase;

    private const TODAY = '2026-10-15'; // Kamis

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        Carbon::setTestNow(Carbon::parse('2026-10-15 07:30', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function fu(array $attrs): FollowUp
    {
        return $this->user->followUps()->create($attrs + ['title' => 'x', 'status' => 'ACTIVE']);
    }

    private function doneAt(string $title, string $when): FollowUp
    {
        return $this->fu(['title' => $title, 'status' => 'DONE', 'done_at' => Carbon::parse($when, 'Asia/Jakarta')]);
    }

    public function test_aturan_bagian_sama_dengan_android(): void
    {
        $section = fn (array $a) => FollowUpSections::for($this->fu($a), self::TODAY);

        $this->assertSame('INBOX', $section(['status' => 'INBOX']));
        $this->assertSame('INBOX', $section(['status' => 'INBOX', 'date' => '2026-10-01'])); // Inbox menang
        $this->assertSame('TODAY', $section(['date' => '2026-10-15']));
        $this->assertSame('TODAY', $section(['status' => 'WAITING', 'date' => '2026-10-15'])); // menunggu dan dicek hari ini
        $this->assertSame('TODAY', $section(['date' => '2026-10-30', 'picked_date' => '2026-10-15']));
        $this->assertSame('OVERDUE', $section(['date' => '2026-10-10']));
        $this->assertSame('TODAY', $section(['date' => '2026-10-10', 'picked_date' => '2026-10-15'])); // dipilih: bukan lewat tanggal
        $this->assertSame('WAITING', $section(['status' => 'WAITING', 'date' => '2026-10-30']));
        $this->assertSame('WAITING', $section(['status' => 'WAITING']));
        $this->assertSame('LATER', $section(['date' => '2026-10-30']));
        $this->assertSame('LATER', $section([]));
        $this->assertNull($section(['status' => 'DONE']));
    }

    public function test_rekap_hari_ini_menghitung_yang_dikerjakan_bukan_inbox(): void
    {
        $this->doneAt('selesai 1', '2026-10-15 06:00');
        $this->doneAt('selesai 2', '2026-10-15 07:00');
        $this->doneAt('kemarin', '2026-10-14 10:00');                       // bukan hari ini
        $this->fu(['title' => 'jatuh tempo', 'date' => '2026-10-15']);
        $this->fu(['title' => 'lewat', 'date' => '2026-10-10']);
        $this->fu(['title' => 'menunggu', 'status' => 'WAITING', 'date' => '2026-10-15']);
        $this->fu(['title' => 'inbox', 'status' => 'INBOX']);
        $this->fu(['title' => 'nanti', 'date' => '2026-10-30']);
        $this->fu(['title' => 'besok', 'date' => '2026-10-30', 'picked_date' => '2026-10-16']);

        $recap = WorkRecap::for($this->user, self::TODAY, self::TODAY);

        $this->assertTrue($recap['isToday']);
        $this->assertSame(2, $recap['done']);
        $this->assertSame(5, $recap['total']);      // 2 selesai + jatuh tempo + lewat + menunggu; Inbox dan Nanti tidak
        $this->assertSame(40, $recap['rate']);
        $this->assertSame(1, $recap['waiting']);
        $this->assertSame(1, $recap['inbox']);
        $this->assertSame(['besok'], $recap['tomorrow']->pluck('title')->all());
        $this->assertSame(['selesai 1', 'selesai 2'], $recap['items']['done']->pluck('title')->all());
        $this->assertSame(['inbox'], $recap['items']['inbox']->pluck('title')->all());
    }

    public function test_semua_memuat_seluruh_yang_terbuka_dengan_yang_dikerjakan_hari_ini_di_atas(): void
    {
        $this->fu(['title' => 'nanti tanpa tanggal']);
        $this->fu(['title' => 'jatuh tempo', 'date' => '2026-10-15']);
        $this->fu(['title' => 'inbox', 'status' => 'INBOX']);
        $this->doneAt('selesai', '2026-10-15 06:00');

        $titles = WorkRecap::for($this->user, self::TODAY, self::TODAY)['items']['all']->pluck('title')->all();

        $this->assertSame(['jatuh tempo', 'nanti tanpa tanggal', 'inbox', 'selesai'], $titles);
    }

    public function test_tanpa_apa_apa_tingkat_selesai_nol_bukan_error(): void
    {
        $recap = WorkRecap::for($this->user, self::TODAY, self::TODAY);

        $this->assertSame(0, $recap['total']);
        $this->assertSame(0, $recap['rate']);
        $this->assertCount(0, $recap['items']['all']);
    }

    public function test_tanggal_lampau_hanya_menampilkan_yang_selesai_pada_hari_itu(): void
    {
        $this->doneAt('selesai kemarin', '2026-10-14 10:00');
        $this->doneAt('selesai hari ini', '2026-10-15 06:00');
        $this->fu(['title' => 'terbuka', 'date' => '2026-10-14']);
        $this->fu(['title' => 'inbox', 'status' => 'INBOX']);

        $recap = WorkRecap::for($this->user, '2026-10-14', self::TODAY);

        $this->assertFalse($recap['isToday']);
        $this->assertSame(['selesai kemarin'], $recap['items']['all']->pluck('title')->all());
        $this->assertSame(1, $recap['done']);
        $this->assertCount(0, $recap['items']['inbox']);
        $this->assertCount(0, $recap['tomorrow']);
        $this->assertSame(0, $recap['inbox']);
    }

    public function test_tanggal_selesai_dihitung_di_zona_waktu_pengguna(): void
    {
        // 14 Oktober 18.00 UTC = 15 Oktober 01.00 WIB.
        $this->fu(['title' => 'lewat tengah malam WIB', 'status' => 'DONE', 'done_at' => Carbon::parse('2026-10-14 18:00', 'UTC')]);

        $this->assertSame(1, WorkRecap::for($this->user, '2026-10-15', self::TODAY)['done']);
        $this->assertSame(0, WorkRecap::for($this->user, '2026-10-14', self::TODAY)['done']);
    }

    public function test_data_pengguna_lain_tidak_ikut(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->followUps()->create(['title' => 'orang lain', 'status' => 'ACTIVE', 'date' => '2026-10-15']);

        $this->assertCount(0, WorkRecap::for($this->user, self::TODAY, self::TODAY)['items']['all']);
    }

    public function test_riwayat_memuat_hari_ini_dan_tanggal_berisi_terbaru_dulu(): void
    {
        $this->doneAt('a', '2026-10-13 10:00');
        $this->doneAt('b', '2026-10-13 11:00');
        $this->user->workDays()->create(['date' => '2026-10-10', 'eod_note' => 'x', 'eod_done_at' => Carbon::parse('2026-10-10 09:03', 'UTC')]);
        $this->user->workDays()->create(['date' => '2026-10-20', 'eod_note' => 'masa depan']); // diabaikan

        $history = WorkRecap::history($this->user, self::TODAY, 10);

        $this->assertSame(['2026-10-15', '2026-10-13', '2026-10-10'], array_column($history, 'date'));
        $this->assertSame(2, $history[1]['done']);
        $this->assertSame('16.03', $history[2]['eodAt']->format('H.i')); // 09.03 UTC = 16.03 WIB
        $this->assertNull($history[0]['eodAt']);
        $this->assertCount(2, WorkRecap::history($this->user, self::TODAY, 2));
        $this->assertSame(3, WorkRecap::historyCount($this->user, self::TODAY));
    }

    public function test_teks_bagikan_memuat_hanya_kelompok_yang_berisi(): void
    {
        $done = collect([$this->doneAt('Draft Confluence', '2026-10-15 06:00')]);
        $tomorrow = collect([$this->fu(['title' => 'Lanjut modul', 'picked_date' => '2026-10-16'])]);
        $waiting = collect([$this->fu(['title' => 'Balasan tim', 'status' => 'WAITING', 'date' => '2026-10-16'])]);

        $text = EodSummary::build(self::TODAY, $done, $tomorrow, $waiting, "  Catatan penutup.  ");

        $this->assertSame(
            "EOD Kamis, 15 Oktober 2026\n\nSelesai:\n- Draft Confluence\n\nLanjut besok:\n- Lanjut modul\n\nMenunggu:\n- Balasan tim (cek 16 Okt)\n\nCatatan:\nCatatan penutup.",
            $text,
        );
        $this->assertSame("EOD Kamis, 15 Oktober 2026", EodSummary::build(self::TODAY, collect(), collect(), collect(), '  '));
    }
}
