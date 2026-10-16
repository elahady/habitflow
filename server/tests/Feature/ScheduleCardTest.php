<?php

namespace Tests\Feature;

use App\Livewire\ScheduleCard;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class ScheduleCardTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        $this->actingAs($this->user);
        // Senin 2026-10-12 09.30 WIB = 02.30 UTC (zona server).
        Carbon::setTestNow(Carbon::parse('2026-10-12 02:30:00', 'UTC'));
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function block(array $attrs): void
    {
        $this->user->scheduleBlocks()->create($attrs + ['start_value' => 540, 'duration_minutes' => 60]);
    }

    private function prayerDay(string $date = '2026-10-12', string $tz = 'Asia/Jakarta'): void
    {
        $this->user->prayerDays()->create([
            'date' => $date, 'timezone' => $tz, 'subuh' => '04:40', 'dzuhur' => '11:55',
            'ashar' => '15:10', 'maghrib' => '17:58', 'isya' => '19:08',
        ]);
    }

    public function test_dashboard_menampilkan_kartu(): void
    {
        $this->get('/')->assertOk()->assertSee('Jadwal hari ini');
    }

    public function test_tanpa_blok_menjelaskan_belum_ada_jadwal(): void
    {
        Livewire::test(ScheduleCard::class)->assertSee('Belum ada jadwal di server');
    }

    public function test_sekarang_dan_berikutnya_memakai_zona_waktu_hp_bukan_utc(): void
    {
        $this->prayerDay();
        $this->block(['name' => 'Kerja fokus', 'start_value' => 9 * 60, 'duration_minutes' => 120]);
        $this->block(['name' => 'Makan siang', 'start_value' => 12 * 60, 'duration_minutes' => 30]);

        // 09.30 WIB: Kerja fokus berjalan (sisa 90 menit), Makan siang 150 menit lagi. Kalau dihitung dengan UTC (02.30) tidak ada yang berjalan.
        Livewire::test(ScheduleCard::class)
            ->assertSee('Kerja fokus')
            ->assertSee('09:00')
            ->assertSee('11:00')
            ->assertSee('sisa 90 menit')
            ->assertSee('Makan siang')
            ->assertSee('150 menit lagi')
            ->assertSee('Asia/Jakarta');
    }

    public function test_blok_berpatokan_sholat_memakai_cache_hari_ini(): void
    {
        $this->prayerDay();
        $this->block(['name' => 'Dzuhur', 'start_type' => 1, 'prayer' => 'DZUHUR', 'start_value' => 0, 'duration_minutes' => 20]);

        Livewire::test(ScheduleCard::class)
            ->assertSee('Dzuhur')
            ->assertSee('11:55')
            ->assertSee('145 menit lagi')
            ->assertDontSee('belum dikirim dari HP');
    }

    public function test_cache_kemarin_tidak_dipakai_dan_pengguna_diberi_tahu(): void
    {
        $this->prayerDay('2026-10-11');
        $this->block(['name' => 'Dzuhur', 'start_type' => 1, 'prayer' => 'DZUHUR', 'start_value' => 0]);

        Livewire::test(ScheduleCard::class)
            ->assertDontSee('11:55')
            ->assertSee('Waktu sholat hari ini belum dikirim dari HP');
    }

    public function test_tanpa_cache_blok_jam_tetap_tetap_tampil_dengan_zona_cadangan(): void
    {
        $this->block(['name' => 'Kerja fokus', 'start_value' => 9 * 60, 'duration_minutes' => 120]);

        // Belum pernah ada kiriman HP: pakai Asia/Jakarta, jadi 09.30 WIB tetap terbaca.
        Livewire::test(ScheduleCard::class)
            ->assertSee('Kerja fokus')
            ->assertSee('sisa 90 menit')
            ->assertSee('Asia/Jakarta');
    }

    public function test_di_luar_jam_blok_menampilkan_pesan_tenang(): void
    {
        $this->block(['name' => 'Subuh pagi', 'start_value' => 5 * 60, 'duration_minutes' => 30]);

        Livewire::test(ScheduleCard::class)
            ->assertSee('Tidak ada blok yang berjalan.')
            ->assertSee('Tidak ada lagi hari ini.');
    }

    public function test_hari_nonaktif_tidak_tampil(): void
    {
        // Hanya Sabtu dan Minggu; hari uji Senin.
        $this->block(['name' => 'Santai', 'active_days' => 0b1100000]);

        Livewire::test(ScheduleCard::class)
            ->assertDontSee('Santai')
            ->assertSee('Tidak ada blok aktif untuk hari ini.');
    }

    public function test_hari_libur_menyembunyikan_blok_hari_kerja_dan_memberi_keterangan(): void
    {
        $this->prayerDay();
        $this->block(['name' => 'Rapat kantor', 'active_days' => 0b0011111]);
        $this->block(['name' => 'Ngaji', 'active_days' => 127, 'start_value' => 10 * 60]);
        $this->user->daysOff()->create(['date' => '2026-10-12']);

        Livewire::test(ScheduleCard::class)
            ->assertDontSee('Rapat kantor')
            ->assertSee('Ngaji')
            ->assertSee('Hari ini libur');
    }

    public function test_libur_tanggal_lain_atau_milik_user_lain_tidak_berlaku(): void
    {
        $this->prayerDay();
        $this->block(['name' => 'Rapat kantor', 'active_days' => 0b0011111]);
        $this->user->daysOff()->create(['date' => '2026-10-13']);
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->daysOff()->create(['date' => '2026-10-12']);

        Livewire::test(ScheduleCard::class)
            ->assertSee('Rapat kantor')
            ->assertDontSee('Hari ini libur');
    }

    public function test_libur_dihitung_menurut_tanggal_di_zona_waktu_hp(): void
    {
        // 18.30 UTC hari Minggu 11 Okt = 01.30 WIB Senin 12 Okt: yang berlaku libur tanggal 12, bukan 11.
        Carbon::setTestNow(Carbon::parse('2026-10-11 18:30:00', 'UTC'));
        $this->prayerDay('2026-10-12');
        $this->block(['name' => 'Rapat kantor', 'start_value' => 60, 'duration_minutes' => 120, 'active_days' => 0b0011111]);
        $this->user->daysOff()->create(['date' => '2026-10-12']);

        Livewire::test(ScheduleCard::class)->assertDontSee('Rapat kantor')->assertSee('Hari ini libur');
    }

    public function test_tidak_menampilkan_jadwal_user_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->scheduleBlocks()->create(['name' => 'Rahasia B', 'start_value' => 540, 'duration_minutes' => 60]);

        Livewire::test(ScheduleCard::class)->assertDontSee('Rahasia B');
    }

    public function test_zona_waktu_lain_menggeser_jam(): void
    {
        // WITA (UTC+8): 02.30 UTC = 10.30.
        $this->prayerDay('2026-10-12', 'Asia/Makassar');
        $this->block(['name' => 'Kerja fokus', 'start_value' => 9 * 60, 'duration_minutes' => 120]);

        Livewire::test(ScheduleCard::class)->assertSee('sisa 30 menit')->assertSee('Asia/Makassar');
    }
}
