<?php

namespace App\Support;

use App\Models\User;
use Illuminate\Support\Carbon;

/**
 * Level hari, streak, dan data heatmap untuk halaman Habit web (tahap 33 langkah 2). Salinan PHP dari
 * `domain/HabitRules.kt`, `domain/DayScore.kt`, `domain/Contribution.kt`, dan `domain/Heatmap.kt` di Android
 * (aturan di `docs/rancangan.md`, bagian Level warna dan Streak). Kalau satu berubah, ubah keduanya.
 *
 * Satu perbedaan yang disengaja: Android tahu tanggal habit dibuat di HP, server tidak (`habits.created_at`
 * adalah waktu pertama habit tersinkron). Tanggal "dibuat" di sini adalah yang lebih awal antara `created_at`
 * (di zona waktu pengguna) dan centang tertua habit itu, supaya riwayat dari sebelum sinkron tidak hilang.
 */
class HabitStats
{
    /** Jumlah minggu heatmap gabungan: batas atas di `docs/rancangan.md` (setahun). */
    public const COMBINED_WEEKS = 53;

    /** Jumlah minggu heatmap per habit di mockup. */
    public const MINI_WEEKS = 14;

    /** Level sel yang tidak digambar: habit belum dibuat pada tanggal itu. */
    public const NOT_DRAWN = -1;

    private const HABIT_DONE_LEVEL = 4;

    private const TODOS_FOR_LEVEL_4 = 2;

    /** Level warna hari 0 sampai 4; habit selalu jadi syarat, to-do tidak bisa menaikkan hari ke level 4 sendirian. */
    public static function dayLevel(int $totalHabits, int $doneHabits, int $doneTodos): int
    {
        if ($totalHabits === 0 || $doneHabits === 0) {
            return 0;
        }
        if ($doneHabits < $totalHabits) {
            // Tanpa pembagian supaya tepat 50% tidak terbulatkan ke bawah.
            return $doneHabits * 2 < $totalHabits ? 1 : 2;
        }

        return $doneTodos >= self::TODOS_FOR_LEVEL_4 ? 4 : 3;
    }

    /** Hari lengkap = semua habit aktif selesai. To-do tidak ikut. */
    public static function isComplete(int $totalHabits, int $doneHabits): bool
    {
        return $totalHabits > 0 && $doneHabits === $totalHabits;
    }

    /** Streak berjalan, dihitung mundur dari hari ini; hari ini yang belum lengkap tidak langsung memutus. */
    public static function currentStreak(array $completeDays, string $today): int
    {
        $set = array_flip($completeDays);
        $day = Carbon::parse($today);
        if (! isset($set[$day->toDateString()])) {
            $day = $day->subDay();
        }

        $streak = 0;
        while (isset($set[$day->toDateString()])) {
            $streak++;
            $day = $day->subDay();
        }

        return $streak;
    }

    /** Jumlah hari berturut-turut terpanjang. */
    public static function longestStreak(array $completeDays): int
    {
        sort($completeDays);
        $best = 0;
        $run = 0;
        $previous = null;
        foreach ($completeDays as $day) {
            $run = $previous !== null && Carbon::parse($previous)->addDay()->toDateString() === $day ? $run + 1 : 1;
            $best = max($best, $run);
            $previous = $day;
        }

        return $best;
    }

    /** Senin pertama jendela heatmap sebanyak [$weeks] minggu sampai minggu yang memuat [$today]. */
    public static function heatmapStart(string $today, int $weeks): Carbon
    {
        return Carbon::parse($today)->startOfWeek(Carbon::MONDAY)->subWeeks($weeks - 1);
    }

    /**
     * @return array{
     *     start: Carbon,
     *     combined: array<string, int>,
     *     perHabit: list<array{habit: \App\Models\Habit, levels: array<string, int>}>,
     *     currentStreak: int,
     *     longestStreak: int,
     * }
     *             `combined` dan `levels` berkunci tanggal `Y-m-d` dari awal jendela sampai [$today].
     */
    public static function for(User $user, string $today): array
    {
        $timezone = UserDay::timezone($user);
        $habits = $user->habits()->orderByDesc('is_mandatory')->orderBy('sort_order')->orderBy('id')->get();

        // Centang per tanggal: [date => [habitId => true]]. Seluruh riwayat, karena streak tidak dibatasi jendela.
        $doneByDate = [];
        $firstEntry = [];
        $entries = \App\Models\HabitEntry::query()->whereIn('habit_id', $habits->pluck('id'))->get(['habit_id', 'date']);
        foreach ($entries as $entry) {
            $date = $entry->date->toDateString();
            $doneByDate[$date][$entry->habit_id] = true;
            if (! isset($firstEntry[$entry->habit_id]) || $date < $firstEntry[$entry->habit_id]) {
                $firstEntry[$entry->habit_id] = $date;
            }
        }

        $todosDone = $user->todos()->where('done', true)->get(['date'])
            ->countBy(fn ($todo) => $todo->date->toDateString())->all();

        $createdOn = [];
        foreach ($habits as $habit) {
            $created = $habit->created_at->copy()->setTimezone($timezone)->toDateString();
            $createdOn[$habit->id] = isset($firstEntry[$habit->id]) ? min($created, $firstEntry[$habit->id]) : $created;
        }

        $score = function (string $date) use ($createdOn, $doneByDate, $todosDone): array {
            $active = array_keys(array_filter($createdOn, fn ($created) => $created <= $date));
            $done = count(array_filter($active, fn ($id) => isset($doneByDate[$date][$id])));

            return ['total' => count($active), 'done' => $done, 'todos' => $todosDone[$date] ?? 0];
        };

        $start = self::heatmapStart($today, self::COMBINED_WEEKS);
        $window = [];
        for ($day = $start->copy(); $day->toDateString() <= $today; $day->addDay()) {
            $window[] = $day->toDateString();
        }

        $combined = [];
        foreach ($window as $date) {
            $s = $score($date);
            $combined[$date] = self::dayLevel($s['total'], $s['done'], $s['todos']);
        }

        $perHabit = $habits->map(fn ($habit) => [
            'habit' => $habit,
            'levels' => collect($window)->mapWithKeys(fn ($date) => [
                $date => $createdOn[$habit->id] > $date
                    ? self::NOT_DRAWN
                    : (isset($doneByDate[$date][$habit->id]) ? self::HABIT_DONE_LEVEL : 0),
            ])->all(),
        ])->all();

        $complete = [];
        foreach (array_unique([...array_keys($doneByDate), $today]) as $date) {
            $s = $score($date);
            if (self::isComplete($s['total'], $s['done'])) {
                $complete[] = $date;
            }
        }

        return [
            'start' => $start,
            'combined' => $combined,
            'perHabit' => $perHabit,
            'currentStreak' => self::currentStreak($complete, $today),
            'longestStreak' => self::longestStreak($complete),
        ];
    }
}
