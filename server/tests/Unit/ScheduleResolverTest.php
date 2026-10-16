<?php

namespace Tests\Unit;

use App\Models\PrayerDay;
use App\Models\ScheduleBlock;
use App\Support\Schedule\ScheduleResolver;
use Illuminate\Support\Carbon;
use PHPUnit\Framework\TestCase;

class ScheduleResolverTest extends TestCase
{
    private int $nextId = 1;

    private function block(array $attrs): ScheduleBlock
    {
        $block = new ScheduleBlock($attrs + [
            'name' => 'Blok', 'start_type' => 0, 'start_value' => 540, 'prayer' => null,
            'duration_minutes' => 60, 'end_minute_of_day' => null, 'active_days' => 127, 'sort_order' => 0,
        ]);
        $block->id = $this->nextId++;

        return $block;
    }

    private function prayers(): PrayerDay
    {
        // setRawAttributes meniru baris dari database (waktu sebagai "HH:MM:SS").
        return (new PrayerDay)->setRawAttributes([
            'subuh' => '04:40:00', 'dzuhur' => '11:55:00', 'ashar' => '15:10:00',
            'maghrib' => '17:58:00', 'isya' => null,
        ]);
    }

    private function names($resolved): array
    {
        return $resolved->map(fn ($r) => $r->block->name)->all();
    }

    // 2026-10-12 hari Senin, 2026-10-17 Sabtu.
    private function monday(): Carbon
    {
        return Carbon::parse('2026-10-12');
    }

    public function test_jam_tetap_dan_durasi(): void
    {
        $r = ScheduleResolver::resolve([$this->block(['start_value' => 540, 'duration_minutes' => 90])], null, $this->monday());

        $this->assertSame(540, $r[0]->startMinute);
        $this->assertSame(630, $r[0]->endMinute);
    }

    public function test_patokan_sholat_dengan_selisih_negatif(): void
    {
        $b = $this->block(['start_type' => 1, 'prayer' => 'SUBUH', 'start_value' => -15, 'duration_minutes' => 15]);
        $r = ScheduleResolver::resolve([$b], $this->prayers(), $this->monday());

        $this->assertSame(4 * 60 + 25, $r[0]->startMinute);
        $this->assertSame(4 * 60 + 40, $r[0]->endMinute);
    }

    public function test_blok_sholat_dibuang_kalau_waktu_sholat_tidak_diketahui(): void
    {
        $sholat = $this->block(['name' => 'Isya', 'start_type' => 1, 'prayer' => 'ISYA', 'start_value' => 0]);
        $tetap = $this->block(['name' => 'Kerja']);

        // Tanpa cache sama sekali, dan dengan cache yang isya-nya null.
        $this->assertSame(['Kerja'], $this->names(ScheduleResolver::resolve([$sholat, $tetap], null, $this->monday())));
        $this->assertSame(['Kerja'], $this->names(ScheduleResolver::resolve([$sholat, $tetap], $this->prayers(), $this->monday())));
    }

    public function test_hari_libur_membuang_blok_yang_hanya_aktif_di_hari_kerja(): void
    {
        $kerja = $this->block(['name' => 'Kerja', 'active_days' => 0b0011111]);
        $setiapHari = $this->block(['name' => 'Sholat', 'active_days' => 127, 'start_value' => 600]);
        $akhirPekan = $this->block(['name' => 'Santai', 'active_days' => 0b1100000]);
        $jumatSabtu = $this->block(['name' => 'Campur', 'active_days' => 0b0110000, 'start_value' => 700]);

        // Senin libur: hanya blok yang juga aktif di Sabtu/Minggu bertahan (sama dengan `isWorkdayOnly` di Android).
        $this->assertSame(
            ['Sholat'],
            $this->names(ScheduleResolver::resolve([$kerja, $setiapHari, $akhirPekan], null, $this->monday(), dayOff: true)),
        );
        $this->assertSame(
            ['Kerja', 'Sholat'],
            $this->names(ScheduleResolver::resolve([$kerja, $setiapHari, $akhirPekan], null, $this->monday(), dayOff: false)),
        );
        // Sabtu libur: blok campur (Jumat-Sabtu) tetap tampil karena ikut menyentuh akhir pekan.
        $this->assertSame(
            ['Santai', 'Sholat', 'Campur'], // urut jam mulai: 09.00, 10.00, 11.40
            $this->names(ScheduleResolver::resolve([$kerja, $setiapHari, $akhirPekan, $jumatSabtu], null, Carbon::parse('2026-10-17'), dayOff: true)),
        );
    }

    public function test_hari_aktif_bitmask_senin_sampai_minggu(): void
    {
        $kerja = $this->block(['name' => 'Kerja', 'active_days' => 0b0011111]);
        $akhirPekan = $this->block(['name' => 'Santai', 'active_days' => 0b1100000]);

        $this->assertSame(['Kerja'], $this->names(ScheduleResolver::resolve([$kerja, $akhirPekan], null, $this->monday())));
        $this->assertSame(['Santai'], $this->names(ScheduleResolver::resolve([$kerja, $akhirPekan], null, Carbon::parse('2026-10-17'))));
        $this->assertSame(['Santai'], $this->names(ScheduleResolver::resolve([$kerja, $akhirPekan], null, Carbon::parse('2026-10-18'))));
    }

    public function test_urutan_menurut_mulai_lalu_sort_order_lalu_id(): void
    {
        $a = $this->block(['name' => 'A', 'start_value' => 600, 'sort_order' => 2]);
        $b = $this->block(['name' => 'B', 'start_value' => 600, 'sort_order' => 1]);
        $c = $this->block(['name' => 'C', 'start_value' => 500]);
        $d = $this->block(['name' => 'D', 'start_value' => 600, 'sort_order' => 1]);

        $this->assertSame(['C', 'B', 'D', 'A'], $this->names(ScheduleResolver::resolve([$a, $b, $c, $d], null, $this->monday())));
    }

    public function test_waktu_dipotong_ke_dalam_satu_hari(): void
    {
        $lewatMalam = $this->block(['start_value' => 1430, 'duration_minutes' => 60]);
        $sebelumNol = $this->block(['start_type' => 1, 'prayer' => 'SUBUH', 'start_value' => -400]);
        $r = ScheduleResolver::resolve([$lewatMalam, $sebelumNol], $this->prayers(), $this->monday());

        $this->assertSame(0, $r[0]->startMinute);
        $this->assertSame(1430, $r[1]->startMinute);
        $this->assertSame(1440, $r[1]->endMinute);
    }

    public function test_end_minute_of_day_menggantikan_durasi(): void
    {
        $r = ScheduleResolver::resolve([$this->block(['start_value' => 540, 'duration_minutes' => 10, 'end_minute_of_day' => 720])], null, $this->monday());

        $this->assertSame(720, $r[0]->endMinute);
    }

    public function test_sekarang_adalah_blok_aktif_yang_paling_akhir_mulai(): void
    {
        $kerja = $this->block(['name' => 'Kerja', 'start_value' => 540, 'duration_minutes' => 240]);
        $sholat = $this->block(['name' => 'Dzuhur', 'start_value' => 715, 'duration_minutes' => 20]);
        $r = ScheduleResolver::resolve([$kerja, $sholat], null, $this->monday());

        $this->assertSame('Kerja', ScheduleResolver::nowAndNext($r, 600)['now']->block->name);
        $this->assertSame('Dzuhur', ScheduleResolver::nowAndNext($r, 720)['now']->block->name);
        $this->assertSame('Kerja', ScheduleResolver::nowAndNext($r, 740)['now']->block->name);
    }

    public function test_berikutnya_adalah_blok_pertama_setelah_sekarang(): void
    {
        $kerja = $this->block(['name' => 'Kerja', 'start_value' => 540, 'duration_minutes' => 60]);
        $makan = $this->block(['name' => 'Makan', 'start_value' => 720, 'duration_minutes' => 30]);
        $r = ScheduleResolver::resolve([$kerja, $makan], null, $this->monday());

        $this->assertSame('Makan', ScheduleResolver::nowAndNext($r, 560)['next']->block->name);
        // Tepat di menit mulai: blok itu sudah Sekarang, bukan Berikutnya.
        $this->assertNull(ScheduleResolver::nowAndNext($r, 720)['next']);
        $this->assertSame('Makan', ScheduleResolver::nowAndNext($r, 720)['now']->block->name);
    }

    public function test_titik_waktu_tidak_pernah_jadi_sekarang_tapi_bisa_berikutnya(): void
    {
        $tidur = $this->block(['name' => 'Batas tidur', 'start_value' => 1320, 'duration_minutes' => 0]);
        $r = ScheduleResolver::resolve([$tidur], null, $this->monday());

        $hasil = ScheduleResolver::nowAndNext($r, 1000);
        $this->assertNull($hasil['now']);
        $this->assertSame('Batas tidur', $hasil['next']->block->name);
        $this->assertNull(ScheduleResolver::nowAndNext($r, 1320)['now']);
    }

    public function test_batas_akhir_eksklusif_dan_tanpa_blok(): void
    {
        $r = ScheduleResolver::resolve([$this->block(['start_value' => 540, 'duration_minutes' => 60])], null, $this->monday());

        $this->assertNotNull(ScheduleResolver::nowAndNext($r, 599)['now']);
        $this->assertNull(ScheduleResolver::nowAndNext($r, 600)['now']);
        $this->assertSame(['now' => null, 'next' => null], ScheduleResolver::nowAndNext(collect(), 600));
    }

    public function test_format_menit(): void
    {
        $this->assertSame('09:05', ScheduleResolver::formatMinute(545));
        $this->assertSame('00:00', ScheduleResolver::formatMinute(0));
        $this->assertSame('00:00', ScheduleResolver::formatMinute(1440));
    }
}
