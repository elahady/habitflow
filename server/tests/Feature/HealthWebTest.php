<?php

namespace Tests\Feature;

use App\Livewire\HealthFocusCard;
use App\Livewire\HealthSection;
use App\Models\HealthFocus;
use App\Models\User;
use App\Support\Health\HealthSummary;
use App\Support\Health\WeightChart;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

/** Tahap 30 langkah 5: kartu fokus dan bagian Kesehatan di Beranda web, hanya membaca data dari Android. */
class HealthWebTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        // Jumat 16 Oktober 2026, 11.00 WIB (04.00 UTC).
        Carbon::setTestNow(Carbon::parse('2026-10-16 11:00', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'Roziq']);
        $this->actingAs($this->user);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function millis(string $when): int
    {
        return Carbon::parse($when, 'Asia/Jakarta')->getTimestampMs();
    }

    private function seedFocus(array $override = []): HealthFocus
    {
        return $this->user->healthFocus()->create($override + [
            'timezone' => 'Asia/Jakarta',
            'height_cm' => 170,
            'target_kg' => 65,
            'steps_target' => 8000,
            'rule' => 'LOW_STEPS',
            'walk_minutes' => 30,
            'title' => 'Jalan santai 30 menit sore ini',
            'sentence' => 'Langkah baru 2.400 dari 8.000.',
            'note' => 'Dipilih karena langkah di bawah separuh target.',
            'reasons' => [['label' => 'Berat', 'value' => '5,4 kg di atas target'], ['label' => 'Tensi', 'value' => '128/82 · Normal']],
            // Eloquent menyimpan jam dinding tanpa konversi zona: selalu UTC.
            'computed_at' => Carbon::parse('2026-10-16 10:50', 'Asia/Jakarta')->utc(),
        ]);
    }

    // --- Kartu fokus ---

    public function test_beranda_menampilkan_kartu_fokus_dan_bagian_kesehatan(): void
    {
        $this->seedFocus();

        $this->get('/')->assertOk()->assertSee('Fokus hari ini')->assertSee('Jalan santai 30 menit sore ini')->assertSee('Kesehatan');
    }

    public function test_fokus_menampilkan_teks_persis_dari_android_beserta_alasannya(): void
    {
        $this->seedFocus();

        Livewire::test(HealthFocusCard::class)
            ->assertSee('Jalan santai 30 menit sore ini')
            ->assertSee('Langkah baru 2.400 dari 8.000.')
            ->assertSee('Kenapa ini?')
            ->assertSee('5,4 kg di atas target')
            ->assertSee('128/82 · Normal')
            ->assertSee('Dipilih karena langkah di bawah separuh target.');
    }

    public function test_fokus_kosong_menjelaskan_cara_mengisinya(): void
    {
        Livewire::test(HealthFocusCard::class)->assertSee('Fokus belum ada')->assertSee('app Android');
    }

    public function test_baris_tanpa_fokus_yang_hanya_berisi_zona_waktu_dianggap_belum_ada_fokus(): void
    {
        $this->user->healthFocus()->create(['timezone' => 'Asia/Jakarta']);

        Livewire::test(HealthFocusCard::class)->assertSee('Fokus belum ada');
    }

    public function test_batang_langkah_hari_ini_memakai_target_dari_hp(): void
    {
        $this->seedFocus();
        $this->user->stepDays()->create(['date' => '2026-10-16', 'steps' => 2400]);

        Livewire::test(HealthFocusCard::class)->assertSee('2.400 / 8.000');
    }

    public function test_tanpa_target_langkah_dari_hp_batang_tidak_digambar(): void
    {
        $this->seedFocus(['steps_target' => null]);
        $this->user->stepDays()->create(['date' => '2026-10-16', 'steps' => 2400]);

        Livewire::test(HealthFocusCard::class)->assertDontSee('2.400 / ');
    }

    public function test_fokus_yang_dihitung_kemarin_diberi_keterangan_basi(): void
    {
        $this->seedFocus(['computed_at' => Carbon::parse('2026-10-15 09:00', 'Asia/Jakarta')->utc()]);

        Livewire::test(HealthFocusCard::class)->assertSee('menghitung ulang fokus hari ini');
    }

    public function test_fokus_yang_baru_dihitung_tidak_diberi_keterangan_basi(): void
    {
        $this->seedFocus();

        Livewire::test(HealthFocusCard::class)->assertDontSee('menghitung ulang fokus hari ini');
    }

    public function test_teks_fokus_di_escape_dan_tidak_bisa_menyisipkan_html(): void
    {
        $this->seedFocus(['title' => '<script>alert(1)</script>']);

        Livewire::test(HealthFocusCard::class)->assertDontSee('<script>alert(1)</script>', false)->assertSee('&lt;script&gt;', false);
    }

    public function test_pengguna_lain_tidak_melihat_fokus_orang_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->healthFocus()->create(['timezone' => 'Asia/Jakarta', 'rule' => 'ON_TRACK', 'title' => 'Rahasia orang lain', 'sentence' => 'x', 'computed_at' => now()->utc()]);

        Livewire::test(HealthFocusCard::class)->assertDontSee('Rahasia orang lain');
    }

    // --- Bagian Kesehatan ---

    public function test_kosong_menjelaskan_cara_mengisinya(): void
    {
        Livewire::test(HealthSection::class)->assertSee('Belum ada data kesehatan')->assertSee('app Android');
    }

    public function test_berat_terakhir_bmi_target_dan_sisa_ditampilkan_tanpa_kategori(): void
    {
        $this->seedFocus();
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'kg' => 70.4]);

        Livewire::test(HealthSection::class)
            ->assertSee('70,4')
            ->assertSee('BMI 24,4')
            ->assertSee('Target 65,0 kg')
            ->assertSee('5,4 kg di atas target')
            ->assertSee('1 hari lalu')
            ->assertDontSee('Normal')
            ->assertDontSee('Gemuk');
    }

    public function test_berat_di_bawah_dan_tepat_di_target(): void
    {
        $this->seedFocus(['target_kg' => 72]);
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-10-16 07:00'), 'kg' => 70.4]);

        Livewire::test(HealthSection::class)->assertSee('1,6 kg di bawah target')->assertSee('hari ini');

        $this->user->healthFocus->update(['target_kg' => 70.4]);
        Livewire::test(HealthSection::class)->assertSee('sudah di target');
    }

    public function test_grafik_berat_punya_deskripsi_untuk_pembaca_layar_dan_garis_target(): void
    {
        $this->seedFocus();
        foreach (['2026-10-01' => 71.0, '2026-10-08' => 70.8, '2026-10-15' => 70.4] as $day => $kg) {
            $this->user->weightEntries()->create(['time_millis' => $this->millis("$day 07:00"), 'kg' => $kg]);
        }

        Livewire::test(HealthSection::class)
            ->assertSeeHtml('role="img"')
            ->assertSee('3 catatan, terakhir 70,4 kg')
            ->assertSee('garis putus-putus menunjukkan target 65,0 kg')
            ->assertSeeHtml('<polyline');
    }

    public function test_berat_di_luar_enam_minggu_tidak_digambar_tapi_berat_terakhir_tetap_tampil(): void
    {
        $this->seedFocus();
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-08-01 07:00'), 'kg' => 72.0]);

        Livewire::test(HealthSection::class)->assertSee('72,0')->assertSee('Belum ada catatan berat dalam 6 minggu terakhir');
    }

    public function test_langkah_tujuh_hari_dengan_hari_tanpa_data(): void
    {
        $this->seedFocus();
        $this->user->stepDays()->create(['date' => '2026-10-16', 'steps' => 2400]);
        $this->user->stepDays()->create(['date' => '2026-10-15', 'steps' => 8123]);

        Livewire::test(HealthSection::class)
            ->assertSee('Langkah, 7 hari')
            ->assertSee('8,1k')
            ->assertSee('2,4k')
            ->assertSee('tidak ada data')
            ->assertSee('target 8.000 langkah per hari');
    }

    public function test_langkah_kosong_menjelaskan_asalnya(): void
    {
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'kg' => 70.4]);

        Livewire::test(HealthSection::class)->assertSee('Belum ada langkah dari HP');
    }

    public function test_tensi_terakhir_dan_riwayat_tanpa_kategori(): void
    {
        $this->seedFocus();
        $this->user->bloodPressureEntries()->create(['time_millis' => $this->millis('2026-10-16 07:30'), 'systolic' => 128, 'diastolic' => 82, 'pulse' => 72]);
        $this->user->bloodPressureEntries()->create(['time_millis' => $this->millis('2026-10-15 07:30'), 'systolic' => 135, 'diastolic' => 88]);

        Livewire::test(HealthSection::class)
            ->assertSee('128/82')
            ->assertSee('nadi 72')
            ->assertSee('135/88')
            ->assertDontSee('Hipertensi')
            ->assertDontSee('Normal');
    }

    public function test_tensi_hanya_lima_terbaru(): void
    {
        $this->seedFocus();
        foreach (range(1, 8) as $i) {
            $this->user->bloodPressureEntries()->create(['time_millis' => $this->millis('2026-10-0'.$i.' 07:00'), 'systolic' => 100 + $i, 'diastolic' => 70]);
        }

        $bps = HealthSummary::bloodPressuresFor($this->user);

        $this->assertCount(HealthSummary::BP_ROWS, $bps);
        $this->assertSame(108, $bps[0]['systolic']);
    }

    public function test_waktu_catat_dihitung_di_zona_waktu_pengguna_bukan_utc(): void
    {
        // 23.30 UTC tanggal 15 = 06.30 WIB tanggal 16.
        $this->seedFocus();
        $this->user->weightEntries()->create(['time_millis' => Carbon::parse('2026-10-15 23:30', 'UTC')->getTimestampMs(), 'kg' => 70.0]);

        $weight = HealthSummary::weightFor($this->user);

        $this->assertSame('2026-10-16', $weight['latest']['at']->toDateString());
        $this->assertSame(0, $weight['latest']['ageDays']);
    }

    public function test_tanpa_zona_waktu_dari_hp_memakai_cadangan_jakarta(): void
    {
        $this->assertSame('Asia/Jakarta', HealthSummary::timezone($this->user, null));
    }

    public function test_bagian_kesehatan_hanya_membaca_tanpa_form_atau_tombol_catat(): void
    {
        $this->seedFocus();
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'kg' => 70.4]);

        Livewire::test(HealthSection::class)->assertDontSeeHtml('<form')->assertDontSeeHtml('<input')->assertDontSee('+ Catat');
        Livewire::test(HealthFocusCard::class)->assertDontSeeHtml('<form')->assertDontSeeHtml('<input');
    }

    public function test_data_pengguna_lain_tidak_ikut_tampil(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->weightEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'kg' => 99.9]);
        $other->bloodPressureEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'systolic' => 199, 'diastolic' => 111]);

        Livewire::test(HealthSection::class)->assertDontSee('99,9')->assertDontSee('199/111')->assertSee('Belum ada data kesehatan');
    }

    public function test_keterangan_terakhir_dikirim_menyebut_zona_waktu(): void
    {
        $this->seedFocus();
        $this->user->weightEntries()->create(['time_millis' => $this->millis('2026-10-15 07:00'), 'kg' => 70.4]);

        Livewire::test(HealthSection::class)->assertSee('Terakhir dikirim')->assertSee('Zona waktu Asia/Jakarta');
    }

    public function test_halaman_beranda_butuh_login(): void
    {
        auth()->logout();

        $this->get('/')->assertRedirect();
    }

    // --- Geometri grafik ---

    public function test_geometri_satu_titik_tidak_menjadi_garis_datar_di_tepi_dan_target_ikut_rentang(): void
    {
        $chart = WeightChart::build([['time_millis' => 5_000, 'kg' => 70.0]], 0, 10_000, 65.0);

        $this->assertNotNull($chart);
        $this->assertLessThan($chart['points'][0]['y'], $chart['yTop']);
        $this->assertGreaterThan($chart['points'][0]['y'], $chart['yBottom']);
        $this->assertGreaterThan($chart['points'][0]['y'], $chart['targetY'], 'target 65 kg di bawah titik 70 kg harus lebih rendah di layar');
        $this->assertLessThanOrEqual(65.0, $chart['minKg']);
        $this->assertGreaterThanOrEqual(70.0, $chart['maxKg']);
    }

    public function test_geometri_tanpa_titik_menghasilkan_null(): void
    {
        $this->assertNull(WeightChart::build([], 0, 10_000, 65.0));
    }

    public function test_geometri_titik_di_dalam_kotak_dan_urut_waktu_menaik_ke_kanan(): void
    {
        $chart = WeightChart::build([
            ['time_millis' => 1_000, 'kg' => 72.0], ['time_millis' => 5_000, 'kg' => 71.0], ['time_millis' => 9_000, 'kg' => 69.5],
        ], 0, 10_000, null);

        $xs = array_column($chart['points'], 'x');
        $this->assertSame($xs, collect($xs)->sort()->values()->all());
        foreach ($chart['points'] as $p) {
            $this->assertGreaterThanOrEqual(WeightChart::PAD_LEFT, $p['x']);
            $this->assertLessThanOrEqual(WeightChart::WIDTH - WeightChart::PAD_RIGHT, $p['x']);
            $this->assertGreaterThanOrEqual($chart['yTop'], $p['y']);
            $this->assertLessThanOrEqual($chart['yBottom'], $p['y']);
        }
        $this->assertNull($chart['targetY']);
        // Sumbu y SVG tumbuh ke bawah: berat turun (72 -> 69,5) berarti titik terakhir lebih rendah di layar, y lebih besar.
        $this->assertGreaterThan($chart['points'][0]['y'], $chart['points'][2]['y']);
    }

    public function test_geometri_titik_di_luar_jendela_waktu_dijepit_ke_tepi(): void
    {
        $chart = WeightChart::build([['time_millis' => -50_000, 'kg' => 70.0], ['time_millis' => 99_000, 'kg' => 70.0]], 0, 10_000, null);

        $this->assertSame((float) WeightChart::PAD_LEFT, $chart['points'][0]['x']);
        $this->assertSame((float) (WeightChart::WIDTH - WeightChart::PAD_RIGHT), $chart['points'][1]['x']);
    }
}
