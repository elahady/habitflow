<?php

namespace Tests\Feature;

use App\Models\Habit;
use App\Models\User;
use App\Support\HabitStats;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Tests\TestCase;

/** Port dari tes level dan streak di Android (HabitRulesTest dan kawan-kawan): aturan harus sama persis. */
class HabitStatsTest extends TestCase
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

    private function habit(string $name, string $createdOn = '2026-01-01', bool $mandatory = false): Habit
    {
        $habit = $this->user->habits()->create(['name' => $name, 'is_mandatory' => $mandatory]);
        $habit->created_at = Carbon::parse($createdOn.' 08:00', 'Asia/Jakarta');
        $habit->saveQuietly();

        return $habit;
    }

    private function check(Habit $habit, string ...$dates): void
    {
        foreach ($dates as $date) {
            $habit->entries()->create(['date' => $date]);
        }
    }

    private function stats(): array
    {
        return HabitStats::for($this->user, self::TODAY);
    }

    public function test_level_hari(): void
    {
        $cases = [
            [0, 0, 5, 0],  // tanpa habit: selalu 0
            [4, 0, 0, 0],
            [4, 0, 5, 0],  // to-do tidak menaikkan hari tanpa habit
            [4, 1, 0, 1],  // kurang dari 50%
            [3, 1, 0, 1],
            [4, 2, 0, 2],  // tepat 50% masuk level 2
            [3, 2, 0, 2],
            [4, 3, 9, 2],  // to-do tidak bisa menaikkan ke level 4 kalau habit belum lengkap
            [4, 4, 0, 3],
            [4, 4, 1, 3],
            [4, 4, 2, 4],
            [1, 1, 5, 4],
        ];
        foreach ($cases as [$total, $done, $todos, $expected]) {
            $this->assertSame($expected, HabitStats::dayLevel($total, $done, $todos), "dayLevel($total,$done,$todos)");
        }
    }

    public function test_hari_lengkap(): void
    {
        $this->assertFalse(HabitStats::isComplete(0, 0));
        $this->assertFalse(HabitStats::isComplete(3, 2));
        $this->assertTrue(HabitStats::isComplete(3, 3));
    }

    public function test_streak_berjalan_dari_hari_ini(): void
    {
        $this->assertSame(3, HabitStats::currentStreak(['2026-10-13', '2026-10-14', '2026-10-15'], self::TODAY));
    }

    public function test_hari_ini_belum_lengkap_tidak_memutus_streak(): void
    {
        $this->assertSame(2, HabitStats::currentStreak(['2026-10-13', '2026-10-14'], self::TODAY));
    }

    public function test_kemarin_tidak_lengkap_memutus_streak(): void
    {
        $this->assertSame(0, HabitStats::currentStreak(['2026-10-12', '2026-10-13'], self::TODAY));
        $this->assertSame(1, HabitStats::currentStreak(['2026-10-12', '2026-10-15'], self::TODAY));
        $this->assertSame(0, HabitStats::currentStreak([], self::TODAY));
    }

    public function test_streak_terpanjang(): void
    {
        $this->assertSame(0, HabitStats::longestStreak([]));
        $this->assertSame(1, HabitStats::longestStreak(['2026-10-01', '2026-10-03']));
        $this->assertSame(3, HabitStats::longestStreak(['2026-10-09', '2026-10-01', '2026-10-02', '2026-10-03', '2026-10-05']));
    }

    public function test_jendela_dimulai_senin_dan_berakhir_hari_ini(): void
    {
        $stats = $this->stats();

        $this->assertSame('Monday', $stats['start']->englishDayOfWeek);
        $this->assertSame('2025-10-13', $stats['start']->toDateString()); // Senin minggu ini (12 Okt 2026) dikurangi 52 minggu
        $this->assertSame('2025-10-13', array_key_first($stats['combined']));
        $this->assertSame(self::TODAY, array_key_last($stats['combined']));
    }

    public function test_level_gabungan_dan_streak_dari_riwayat_penuh(): void
    {
        $a = $this->habit('Sholat', mandatory: true);
        $b = $this->habit('Baca');
        $this->check($a, '2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15');
        $this->check($b, '2026-10-13', '2026-10-14', '2026-10-15');
        $this->user->todos()->create(['title' => 'x', 'date' => '2026-10-14', 'done' => true]);
        $this->user->todos()->create(['title' => 'y', 'date' => '2026-10-14', 'done' => true]);

        $stats = $this->stats();

        $this->assertSame(2, $stats['combined']['2026-10-12']); // 1 dari 2 habit = 50%
        $this->assertSame(3, $stats['combined']['2026-10-13']); // semua habit
        $this->assertSame(4, $stats['combined']['2026-10-14']); // semua habit + 2 to-do selesai
        $this->assertSame(3, $stats['combined']['2026-10-15']);
        $this->assertSame(0, $stats['combined']['2026-10-11']);
        $this->assertSame(3, $stats['currentStreak']);
        $this->assertSame(3, $stats['longestStreak']);
    }

    public function test_streak_lebih_lama_dari_jendela_heatmap_tetap_terhitung(): void
    {
        $a = $this->habit('Sholat', '2024-01-01');
        $dates = [];
        for ($d = Carbon::parse('2026-10-15'); $d >= Carbon::parse('2025-08-01'); $d->subDay()) {
            $dates[] = $d->toDateString();
        }
        $this->check($a, ...$dates);

        $stats = $this->stats();

        $this->assertSame(count($dates), $stats['currentStreak']); // > 53 minggu, melewati awal jendela
        $this->assertGreaterThan(365, $stats['currentStreak']);
    }

    public function test_hari_sebelum_habit_dibuat_tidak_dihitung_sebagai_gagal(): void
    {
        $old = $this->habit('Lama', '2026-01-01');
        $this->habit('Baru', '2026-10-14'); // habit kedua baru dibuat kemarin
        $this->check($old, '2026-10-12', '2026-10-13', '2026-10-14');

        $stats = $this->stats();

        $this->assertSame(3, $stats['combined']['2026-10-12']); // hanya 1 habit aktif, dan selesai
        $this->assertSame(3, $stats['combined']['2026-10-13']);
        $this->assertSame(2, $stats['combined']['2026-10-14']); // sekarang 2 habit aktif, 1 selesai
        $baru = collect($stats['perHabit'])->firstWhere('habit.name', 'Baru');
        $this->assertSame(HabitStats::NOT_DRAWN, $baru['levels']['2026-10-13']);
        $this->assertSame(0, $baru['levels']['2026-10-14']);
    }

    public function test_tanggal_dibuat_memakai_centang_tertua_kalau_lebih_awal_dari_sinkron(): void
    {
        // Habit baru tersinkron 11 Oktober, tapi riwayat centang dari HP sudah ada sejak September.
        $habit = $this->habit('Sholat', '2026-10-11');
        $this->check($habit, '2026-09-20', '2026-09-21');

        $stats = $this->stats();

        $this->assertSame(3, $stats['combined']['2026-09-20']);
        $this->assertSame(0, $stats['combined']['2026-09-22']);
        $this->assertSame(HabitStats::NOT_DRAWN, $stats['perHabit'][0]['levels']['2026-09-19']);
        $this->assertSame(4, $stats['perHabit'][0]['levels']['2026-09-20']);
    }

    public function test_tanggal_dibuat_dihitung_di_zona_waktu_pengguna(): void
    {
        // 14 Oktober 18.00 UTC = 15 Oktober 01.00 WIB: habit dibuat "tanggal 15" bagi pengguna.
        $habit = $this->user->habits()->create(['name' => 'Malam']);
        $habit->created_at = Carbon::parse('2026-10-14 18:00', 'UTC');
        $habit->saveQuietly();

        $levels = $this->stats()['perHabit'][0]['levels'];

        $this->assertSame(HabitStats::NOT_DRAWN, $levels['2026-10-14']);
        $this->assertSame(0, $levels['2026-10-15']);
    }

    public function test_data_pengguna_lain_tidak_ikut(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $theirs = $other->habits()->create(['name' => 'Milik lain']);
        $theirs->entries()->create(['date' => self::TODAY]);

        $stats = $this->stats();

        $this->assertSame([], $stats['perHabit']);
        $this->assertSame(0, $stats['combined'][self::TODAY]);
        $this->assertSame(0, $stats['currentStreak']);
    }

    public function test_tanpa_habit_semua_nol(): void
    {
        $stats = $this->stats();

        $this->assertSame(0, $stats['currentStreak']);
        $this->assertSame(0, $stats['longestStreak']);
        $this->assertSame([0], array_values(array_unique($stats['combined'])));
    }
}
