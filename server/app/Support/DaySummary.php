<?php

namespace App\Support;

use App\Models\TeamTodo;
use App\Models\User;

/**
 * Angka ringkas untuk strip kartu di Beranda web (tahap 33): habit, to-do, follow-up, dan tugas rumah
 * hari ini. Aturan follow-up adalah salinan `sectionFor` di `domain/work/FollowUps.kt` (Android) supaya
 * angka di web sama dengan di HP; kalau salah satu berubah, ubah keduanya.
 */
class DaySummary
{
    /**
     * @return array{
     *     habits: array{done: int, total: int},
     *     todos: array{done: int, total: int},
     *     followUps: array{overdue: int, today: int, inbox: int},
     *     teamMine: int,
     * }
     */
    public static function for(User $user, string $today): array
    {
        $habitIds = $user->habits()->pluck('id');
        $habitsDone = $habitIds->isEmpty() ? 0 : $user->habits()
            ->whereHas('entries', fn ($q) => $q->whereDate('date', $today))
            ->count();

        $todos = $user->todos()->whereDate('date', $today)->get(['done']);

        return [
            'habits' => ['done' => $habitsDone, 'total' => $habitIds->count()],
            'todos' => ['done' => $todos->where('done', true)->count(), 'total' => $todos->count()],
            'followUps' => self::followUps($user, $today),
            'teamMine' => self::teamMine($user),
        ];
    }

    /** @return array{overdue: int, today: int, inbox: int} */
    private static function followUps(User $user, string $today): array
    {
        $counts = ['overdue' => 0, 'today' => 0, 'inbox' => 0];

        foreach ($user->followUps()->where('status', '!=', 'DONE')->get() as $item) {
            if ($item->status === 'INBOX') {
                $counts['inbox']++;

                continue;
            }

            $date = $item->date?->toDateString();
            $picked = $item->picked_date?->toDateString() === $today;

            if ($picked || $date === $today) {
                $counts['today']++;
            } elseif ($date !== null && $date < $today) {
                $counts['overdue']++;
            }
        }

        return $counts;
    }

    /** Tugas tim yang ditugaskan ke pengguna dan belum selesai, hanya di tim yang masih ia ikuti. */
    private static function teamMine(User $user): int
    {
        return TeamTodo::query()
            ->whereIn('team_id', $user->teams()->pluck('teams.id'))
            ->where('assigned_to', $user->id)
            ->where('done', false)
            ->count();
    }
}
