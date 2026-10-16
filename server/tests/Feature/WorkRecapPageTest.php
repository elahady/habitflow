<?php

namespace Tests\Feature;

use App\Livewire\FollowUps;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class WorkRecapPageTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        Carbon::setTestNow(Carbon::parse('2026-10-15 07:30', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        $this->actingAs($this->user);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function done(string $title, string $when = '2026-10-15 06:00'): void
    {
        $this->user->followUps()->create(['title' => $title, 'status' => 'DONE', 'done_at' => Carbon::parse($when, 'Asia/Jakarta')]);
    }

    public function test_halaman_menampilkan_rekap_hari_ini(): void
    {
        $this->done('Draft Confluence');
        $this->user->followUps()->create(['title' => 'Jatuh tempo', 'status' => 'ACTIVE', 'date' => '2026-10-15']);

        $this->get('/follow-up')->assertOk()
            ->assertSee('Rekap hari ini')
            ->assertSee('Kamis, 15 Oktober 2026')
            ->assertSee('1 dari 2 follow-up selesai')
            ->assertSee('Tingkat selesai')
            ->assertSee('50%')
            ->assertSee('Riwayat rekap')
            ->assertSee('Hari ini · belum EOD')
            ->assertSee('Bagikan ringkasan');
    }

    public function test_kosong_menjelaskan(): void
    {
        Livewire::test(FollowUps::class)
            ->assertSee('Belum ada follow-up hari ini')
            ->assertSee('Tidak ada yang tertunda.')
            ->assertSee('Tidak ada follow-up di daftar ini.');
    }

    public function test_filter_mengubah_daftar(): void
    {
        $this->user->followUps()->create(['title' => 'Item inbox', 'status' => 'INBOX']);
        $this->user->followUps()->create(['title' => 'Item menunggu', 'status' => 'WAITING']);
        $this->done('Item selesai');

        $ids = $this->user->followUps()->pluck('id', 'title');
        $row = fn (string $title) => 'wire:key="row-'.$ids[$title].'"';

        // Judul juga muncul di teks bagikan (JS), jadi yang diperiksa adalah baris daftarnya.
        Livewire::test(FollowUps::class)
            ->assertSeeHtml($row('Item inbox'))->assertSeeHtml($row('Item menunggu'))->assertSeeHtml($row('Item selesai'))
            ->call('setFilter', 'waiting')
            ->assertSeeHtml($row('Item menunggu'))->assertDontSeeHtml($row('Item inbox'))->assertDontSeeHtml($row('Item selesai'))
            ->call('setFilter', 'done')
            ->assertSeeHtml($row('Item selesai'))->assertDontSeeHtml($row('Item menunggu'))
            ->call('setFilter', 'bukan-filter')
            ->assertSet('filter', 'all');
    }

    public function test_pilih_tanggal_lampau_menampilkan_yang_selesai_hari_itu(): void
    {
        $this->done('Selesai kemarin', '2026-10-14 10:00');
        $this->done('Selesai hari ini');
        $this->user->workDays()->create(['date' => '2026-10-14', 'eod_note' => 'Catatan kemarin', 'eod_done_at' => Carbon::parse('2026-10-14 09:03', 'UTC')]);

        Livewire::test(FollowUps::class)
            ->call('selectDate', '2026-10-14')
            ->assertSee('Rabu, 14 Oktober 2026')
            ->assertSee('Rekap harian')
            ->assertSee('Selesai kemarin')->assertDontSee('Selesai hari ini')
            ->assertSee('1 follow-up selesai')
            ->assertSet('eodNote', 'Catatan kemarin')
            ->assertSee('EOD · 16.03')
            ->assertDontSee('Rencana besok');
    }

    public function test_tanggal_tidak_sah_atau_di_masa_depan_jatuh_ke_hari_ini(): void
    {
        foreach (['bukan-tanggal', '2026-13-45', '2026-10-16', '2099-01-01', ''] as $input) {
            Livewire::test(FollowUps::class)
                ->call('selectDate', $input)
                ->assertSet('date', '2026-10-15');
        }
    }

    public function test_tanggal_di_url_dipakai(): void
    {
        $this->done('Selesai kemarin', '2026-10-14 10:00');

        $this->get('/follow-up?tanggal=2026-10-14')->assertOk()->assertSee('Selesai kemarin')->assertSee('Rabu, 14 Oktober 2026');
        $this->get('/follow-up?tanggal=ngawur')->assertOk()->assertSee('Kamis, 15 Oktober 2026');
    }

    public function test_simpan_catatan_eod_membuat_baris_lalu_memperbarui(): void
    {
        $component = Livewire::test(FollowUps::class)
            ->set('eodNote', '  Modul supplier dipindah ke besok.  ')
            ->call('saveEodNote')
            ->assertSet('eodNote', 'Modul supplier dipindah ke besok.');

        $this->assertSame('Modul supplier dipindah ke besok.', $this->user->workDays()->where('date', '2026-10-15')->value('eod_note'));

        $component->set('eodNote', 'Revisi')->call('saveEodNote');

        $this->assertSame(1, $this->user->workDays()->count());
        $this->assertSame('Revisi', $this->user->workDays()->first()->eod_note);
    }

    public function test_catatan_kosong_tanpa_baris_tidak_membuat_baris(): void
    {
        Livewire::test(FollowUps::class)->set('eodNote', '   ')->call('saveEodNote');

        $this->assertSame(0, $this->user->workDays()->count());
    }

    public function test_menyimpan_catatan_tidak_menghapus_waktu_eod_dari_hp(): void
    {
        $this->user->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'lama', 'eod_done_at' => Carbon::parse('2026-10-15 09:03', 'UTC')]);

        Livewire::test(FollowUps::class)->set('eodNote', 'baru dari web')->call('saveEodNote');

        $row = $this->user->workDays()->first();
        $this->assertSame('baru dari web', $row->eod_note);
        $this->assertSame('16.03', $row->eod_done_at->copy()->setTimezone('Asia/Jakarta')->format('H.i'));
    }

    public function test_catatan_eod_berubah_di_hp_ditampilkan_sebagai_bentrok_bukan_ditimpa(): void
    {
        $this->user->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'versi awal']);
        $component = Livewire::test(FollowUps::class)->assertSet('eodNote', 'versi awal');

        // HP mengubah catatan setelah halaman dibuka.
        $this->user->workDays()->first()->forceFill(['eod_note' => 'versi dari HP', 'updated_at' => now()->addMinute()])->saveQuietly();

        $component->set('eodNote', 'versi dari web')->call('saveEodNote')
            ->assertSet('eodConflict', 'versi dari HP')
            ->assertSee('Catatan ini berubah di tempat lain');

        $this->assertSame('versi dari HP', $this->user->workDays()->first()->eod_note);
    }

    public function test_bentrok_timpa_dengan_punyaku(): void
    {
        $this->user->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'versi awal']);
        $component = Livewire::test(FollowUps::class);
        $this->user->workDays()->first()->forceFill(['eod_note' => 'versi dari HP', 'updated_at' => now()->addMinute()])->saveQuietly();

        $component->set('eodNote', 'versi dari web')->call('saveEodNote')->call('overwriteEodNote')
            ->assertSet('eodConflict', null)
            ->assertSet('eodNote', 'versi dari web');

        $this->assertSame('versi dari web', $this->user->workDays()->first()->eod_note);
    }

    public function test_bentrok_pakai_yang_terbaru(): void
    {
        $this->user->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'versi awal']);
        $component = Livewire::test(FollowUps::class);
        $this->user->workDays()->first()->forceFill(['eod_note' => 'versi dari HP', 'updated_at' => now()->addMinute()])->saveQuietly();

        $component->set('eodNote', 'versi dari web')->call('saveEodNote')->call('useLatestEodNote')
            ->assertSet('eodConflict', null)
            ->assertSet('eodNote', 'versi dari HP');

        $this->assertSame('versi dari HP', $this->user->workDays()->first()->eod_note);
    }

    public function test_catatan_eod_tanggal_lain_tidak_tertukar(): void
    {
        $this->user->workDays()->create(['date' => '2026-10-14', 'eod_note' => 'kemarin']);

        Livewire::test(FollowUps::class)
            ->assertSet('eodNote', '')
            ->call('selectDate', '2026-10-14')
            ->assertSet('eodNote', 'kemarin')
            ->set('eodNote', 'kemarin diubah')->call('saveEodNote');

        $this->assertSame('kemarin diubah', $this->user->workDays()->where('date', '2026-10-14')->value('eod_note'));
        $this->assertSame(1, $this->user->workDays()->count());
    }

    public function test_catatan_meeting_disimpan_di_daily_briefs_tanpa_menyentuh_ringkasan_claude(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => '3 email belum dibaca']);

        Livewire::test(FollowUps::class)->set('meetingNote', 'Keputusan: kategori Level 1.')->call('saveMeetingNote');

        $brief = $this->user->dailyBriefs()->first();
        $this->assertSame('Keputusan: kategori Level 1.', $brief->meeting_note);
        $this->assertSame('3 email belum dibaca', $brief->email_digest);
        $this->assertSame(1, $this->user->dailyBriefs()->count());
    }

    public function test_catatan_meeting_membuat_baris_tanpa_email_dan_kartu_claude_tidak_menampilkannya(): void
    {
        Livewire::test(FollowUps::class)->set('meetingNote', 'Catatan saja')->call('saveMeetingNote');

        $this->assertNull($this->user->dailyBriefs()->first()->email_digest);
        // Kartu Claude hanya menampilkan baris yang punya ringkasan email; catatan meeting saja tidak dihitung.
        Livewire::test(\App\Livewire\DailyBriefCard::class)->assertSee('Belum ada ringkasan')->assertDontSee('Catatan saja');
    }

    public function test_catatan_meeting_kosong_tanpa_baris_tidak_membuat_baris(): void
    {
        Livewire::test(FollowUps::class)->set('meetingNote', ' ')->call('saveMeetingNote');

        $this->assertSame(0, $this->user->dailyBriefs()->count());
    }

    public function test_rencana_besok_menambah_dari_inbox_dan_merapikannya_jadi_aktif(): void
    {
        $item = $this->user->followUps()->create(['title' => 'Dari inbox', 'status' => 'INBOX']);

        Livewire::test(FollowUps::class)
            ->set('pickTomorrowId', (string) $item->id)
            ->call('addToTomorrow')
            ->assertSet('pickTomorrowId', '')
            ->assertSee('Lanjut besok');

        $item->refresh();
        $this->assertSame('ACTIVE', $item->status);
        $this->assertSame('2026-10-16', $item->picked_date->toDateString());
    }

    public function test_rencana_besok_mempertahankan_status_menunggu_dan_bisa_dibatalkan(): void
    {
        $item = $this->user->followUps()->create(['title' => 'Balasan tim', 'status' => 'WAITING', 'date' => '2026-10-20']);

        $component = Livewire::test(FollowUps::class)->set('pickTomorrowId', (string) $item->id)->call('addToTomorrow');
        $this->assertSame('WAITING', $item->fresh()->status);
        $this->assertSame('2026-10-16', $item->fresh()->picked_date->toDateString());

        $component->call('removeFromTomorrow', $item->id);
        $this->assertNull($item->fresh()->picked_date);
        $this->assertSame('WAITING', $item->fresh()->status);
    }

    public function test_rencana_besok_tidak_bisa_memilih_milik_orang_lain_atau_yang_selesai(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $theirs = $other->followUps()->create(['title' => 'Milik lain', 'status' => 'ACTIVE']);
        $done = $this->user->followUps()->create(['title' => 'Sudah selesai', 'status' => 'DONE', 'done_at' => now()]);

        Livewire::test(FollowUps::class)
            ->set('pickTomorrowId', (string) $theirs->id)->call('addToTomorrow')
            ->set('pickTomorrowId', (string) $done->id)->call('addToTomorrow');

        $this->assertNull($theirs->fresh()->picked_date);
        $this->assertNull($done->fresh()->picked_date);
    }

    public function test_teks_bagikan_ada_di_halaman(): void
    {
        $this->done('Draft Confluence');

        $this->get('/follow-up')->assertOk()->assertSee('EOD Kamis, 15 Oktober 2026', false)->assertSee('Draft Confluence');
    }

    public function test_riwayat_bisa_dimuat_lebih_lama(): void
    {
        for ($d = 1; $d <= 14; $d++) {
            $this->user->workDays()->create(['date' => sprintf('2026-09-%02d', $d), 'eod_note' => 'x']);
        }

        Livewire::test(FollowUps::class)
            ->assertSee('Muat lebih lama')
            ->assertDontSeeHtml('wire:key="hist-2026-09-01"')
            ->call('loadOlder')
            ->assertSeeHtml('wire:key="hist-2026-09-01"')
            ->assertDontSee('Muat lebih lama');
    }
    public function test_riwayat_dan_judul_tanggal_lampau_memakai_angka_ringkasan_eod_dari_hp(): void
    {
        $this->done('Selesai kemarin', '2026-10-14 10:00');
        $this->user->workDays()->create([
            'date' => '2026-10-14', 'eod_done_at' => Carbon::parse('2026-10-14 09:03', 'UTC'), 'eod_done_count' => 7, 'eod_total_count' => 9,
        ]);

        Livewire::test(FollowUps::class)
            ->assertSee('7 dari 9 selesai')
            ->call('selectDate', '2026-10-14')
            ->assertSee('7 dari 9 follow-up selesai');
    }

    public function test_tanpa_angka_ringkasan_atau_total_nol_kembali_ke_jumlah_selesai(): void
    {
        $this->done('Selesai kemarin', '2026-10-14 10:00');
        $this->user->workDays()->create(['date' => '2026-10-14', 'eod_done_count' => 0, 'eod_total_count' => 0]);
        $this->user->workDays()->create(['date' => '2026-10-13', 'eod_done_at' => Carbon::parse('2026-10-13 09:03', 'UTC')]);

        Livewire::test(FollowUps::class)
            ->assertSee('1 selesai')
            ->assertDontSee('0 dari 0')
            ->call('selectDate', '2026-10-14')
            ->assertSee('1 follow-up selesai')
            ->assertDontSee('0 dari 0');
    }
}
