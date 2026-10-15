<?php

namespace Tests\Feature;

use App\Livewire\DailyBriefCard;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class DailyBriefCardTest extends TestCase
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

    public function test_kartu_tampil_di_halaman_kerja(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => '3 email belum dibaca']);

        $this->get('/follow-up')->assertOk()->assertSee('Ringkasan dari Claude')->assertSee('3 email belum dibaca');
    }

    public function test_kosong_menjelaskan_cara_mengisinya(): void
    {
        Livewire::test(DailyBriefCard::class)->assertSee('Belum ada ringkasan')->assertSee('habitflow-morning-routine');
    }

    public function test_ringkasan_hari_ini_ditampilkan_dengan_penanda_hari_ini(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => 'ringkasan hari ini']);
        $this->user->dailyBriefs()->create(['date' => '2026-10-14', 'email_digest' => 'ringkasan kemarin']);

        Livewire::test(DailyBriefCard::class)
            ->assertSee('ringkasan hari ini')
            ->assertDontSee('ringkasan kemarin')
            ->assertSee('Hari ini')
            ->assertDontSee('Belum ada ringkasan hari ini');
    }

    public function test_belum_ada_hari_ini_menampilkan_yang_terakhir_dengan_tanggalnya(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-13', 'email_digest' => 'ringkasan lama']);

        Livewire::test(DailyBriefCard::class)
            ->assertSee('ringkasan lama')
            ->assertSee('Belum ada ringkasan hari ini')
            ->assertSee('13 Oktober 2026');
    }

    public function test_hari_ini_dihitung_di_zona_waktu_hp_bukan_utc(): void
    {
        // 15 Oktober 23.30 UTC masih 15 Oktober di UTC, tapi sudah 16 Oktober 06.30 di Jakarta.
        Carbon::setTestNow(Carbon::parse('2026-10-15 23:30', 'UTC'));
        $this->user->prayerDays()->create(['date' => '2026-10-16', 'timezone' => 'Asia/Jakarta']);
        $this->user->dailyBriefs()->create(['date' => '2026-10-16', 'email_digest' => 'ringkasan jakarta']);

        Livewire::test(DailyBriefCard::class)->assertSee('ringkasan jakarta')->assertDontSee('Belum ada ringkasan hari ini');
    }

    public function test_ringkasan_tanggal_depan_tidak_dianggap_hari_ini(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-20', 'email_digest' => 'dari masa depan']);

        Livewire::test(DailyBriefCard::class)->assertDontSee('dari masa depan')->assertSee('Belum ada ringkasan');
    }

    public function test_ringkasan_orang_lain_tidak_tampil(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => 'rahasia orang lain']);

        Livewire::test(DailyBriefCard::class)->assertDontSee('rahasia orang lain');
    }

    public function test_isi_di_escape_bukan_html(): void
    {
        $this->user->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => '<script>alert(1)</script>']);

        Livewire::test(DailyBriefCard::class)->assertDontSeeHtml('<script>alert(1)</script>')->assertSee('<script>alert(1)</script>');
    }
}
