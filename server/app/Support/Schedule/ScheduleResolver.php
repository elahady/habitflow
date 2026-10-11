<?php

namespace App\Support\Schedule;

use App\Models\PrayerDay;
use App\Models\ScheduleBlock;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;

/**
 * Salinan PHP dari `resolveBlocks` dan `nowAndNext` di `domain/schedule/Schedule.kt` (Android),
 * hanya untuk menampilkan jadwal di web (tahap 29). Aturannya sengaja sama; kalau satu berubah,
 * ubah keduanya. Waktu sholat TIDAK dihitung di sini: dibaca dari kiriman HP (`PrayerDay`).
 * "Hari ini libur" belum ada di server, jadi tidak diterapkan.
 */
final class ScheduleResolver
{
    public const MINUTES_PER_DAY = 1440;

    /**
     * Blok untuk [$date], urut menurut waktu mulai. Blok yang tidak aktif di hari itu, atau yang
     * berpatokan sholat padahal waktu sholatnya tidak diketahui, dibuang.
     *
     * @param  iterable<ScheduleBlock>  $blocks
     * @return Collection<int, ResolvedBlock>
     */
    public static function resolve(iterable $blocks, ?PrayerDay $prayers, Carbon $date): Collection
    {
        $dayBit = 1 << ($date->dayOfWeekIso - 1);

        return collect($blocks)
            ->filter(fn (ScheduleBlock $b) => ($b->active_days & $dayBit) !== 0)
            ->map(function (ScheduleBlock $b) use ($prayers) {
                if ($b->start_type === ScheduleBlock::START_PRAYER) {
                    $prayer = $prayers?->minuteOf((string) $b->prayer);
                    if ($prayer === null) {
                        return null;
                    }
                    $start = $prayer + $b->start_value;
                } else {
                    $start = $b->start_value;
                }
                $start = max(0, min(self::MINUTES_PER_DAY - 1, $start));
                $end = max($start, min(self::MINUTES_PER_DAY, $b->end_minute_of_day ?? $start + $b->duration_minutes));

                return new ResolvedBlock($b, $start, $end);
            })
            ->filter()
            ->sortBy([
                fn (ResolvedBlock $a, ResolvedBlock $b) => $a->startMinute <=> $b->startMinute,
                fn (ResolvedBlock $a, ResolvedBlock $b) => $a->block->sort_order <=> $b->block->sort_order,
                fn (ResolvedBlock $a, ResolvedBlock $b) => $a->block->id <=> $b->block->id,
            ])
            ->values();
    }

    /**
     * Sekarang = blok aktif yang paling akhir dimulai (blok sholat di tengah blok kerja menggantikan
     * blok kerja). Berikutnya = blok pertama yang dimulai setelah [$nowMinute].
     *
     * @param  Collection<int, ResolvedBlock>  $resolved
     * @return array{now: ?ResolvedBlock, next: ?ResolvedBlock}
     */
    public static function nowAndNext(Collection $resolved, int $nowMinute): array
    {
        return [
            'now' => $resolved->last(fn (ResolvedBlock $r) => ! $r->isPoint()
                && $r->startMinute <= $nowMinute && $nowMinute < $r->endMinute),
            'next' => $resolved->first(fn (ResolvedBlock $r) => $r->startMinute > $nowMinute),
        ];
    }

    /** 545 -> "09:05". */
    public static function formatMinute(int $minute): string
    {
        return sprintf('%02d:%02d', intdiv($minute, 60) % 24, $minute % 60);
    }
}
