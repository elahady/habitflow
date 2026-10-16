<?php

namespace App\Support\Health;

use App\Models\HealthFocus;
use App\Models\User;
use App\Support\UserDay;
use Illuminate\Support\Carbon;

/**
 * Bahan tampilan data kesehatan di web (tahap 30 langkah 5). Hanya membaca apa yang dikirim Android lewat
 * `PUT /api/v1/health`. Aturan fokus dan kategori (BMI, tensi) hanya ada di Android; di sini cuma angka, tanggal, dan
 * aritmetika sederhana (sisa ke target, BMI sebagai angka), tanpa kategori atau saran.
 */
class HealthSummary
{
    /** Hari yang ditampilkan di grafik berat dan batang langkah. */
    public const WEIGHT_DAYS = 42;

    public const STEP_DAYS = 7;

    public const BP_ROWS = 5;

    /** Fokus yang dihitung lebih dari ini lalu dianggap basi dan diberi keterangan. */
    public const STALE_HOURS = 24;

    public static function timezone(User $user, ?HealthFocus $row): string
    {
        return $row?->timezone ?: UserDay::timezone($user);
    }

    /** Fokus terakhir yang sudah dihitung HP, atau null kalau belum pernah dikirim (baris hanya berisi zona waktu). */
    public static function focus(User $user): ?HealthFocus
    {
        $row = $user->healthFocus;

        return $row && $row->rule !== null ? $row : null;
    }

    /**
     * @return array{
     *   timezone: string, today: string, now: Carbon, row: ?HealthFocus,
     *   steps: array{today: ?int, target: ?int, days: list<array{date: string, label: string, steps: ?int}>},
     * }
     */
    public static function stepsFor(User $user, ?Carbon $now = null): array
    {
        $row = $user->healthFocus;
        $tz = self::timezone($user, $row);
        $now = ($now ?? Carbon::now())->copy()->setTimezone($tz);
        $today = $now->toDateString();

        $from = $now->copy()->subDays(self::STEP_DAYS - 1)->toDateString();
        $byDate = $user->stepDays()->whereBetween('date', [$from, $today])->pluck('steps', 'date');

        $days = [];
        for ($i = self::STEP_DAYS - 1; $i >= 0; $i--) {
            $d = $now->copy()->subDays($i);
            $days[] = [
                'date' => $d->toDateString(),
                'label' => $d->locale('id')->translatedFormat('D'),
                'steps' => isset($byDate[$d->toDateString()]) ? (int) $byDate[$d->toDateString()] : null,
            ];
        }

        return [
            'timezone' => $tz,
            'today' => $today,
            'now' => $now,
            'row' => $row,
            'steps' => [
                'today' => isset($byDate[$today]) ? (int) $byDate[$today] : null,
                'target' => $row?->steps_target,
                'days' => $days,
            ],
        ];
    }

    /**
     * Berat terakhir, grafik 6 minggu terhadap target, dan BMI sebagai angka. Null kalau belum ada catatan berat.
     *
     * @return array{latest: array{kg: float, at: Carbon, ageDays: int}, targetKg: ?float, remainingKg: ?float, heightCm: ?float, bmi: ?float, chart: ?array, from: Carbon, to: Carbon}|null
     */
    public static function weightFor(User $user, ?Carbon $now = null): ?array
    {
        $row = $user->healthFocus;
        $tz = self::timezone($user, $row);
        $now = ($now ?? Carbon::now())->copy()->setTimezone($tz);

        $latest = $user->weightEntries()->orderByDesc('time_millis')->first();
        if (! $latest) {
            return null;
        }

        $from = $now->copy()->subDays(self::WEIGHT_DAYS - 1)->startOfDay();
        $series = $user->weightEntries()
            ->where('time_millis', '>=', $from->getTimestampMs())
            ->orderBy('time_millis')
            ->get(['time_millis', 'kg'])
            ->map(fn ($w) => ['time_millis' => (int) $w->time_millis, 'kg' => (float) $w->kg])
            ->all();

        $at = Carbon::createFromTimestampMs($latest->time_millis, $tz);
        $kg = (float) $latest->kg;
        $target = $row?->target_kg;
        $height = $row?->height_cm;

        return [
            'latest' => ['kg' => $kg, 'at' => $at, 'ageDays' => (int) $at->copy()->startOfDay()->diffInDays($now->copy()->startOfDay())],
            'targetKg' => $target,
            'remainingKg' => $target === null ? null : round($kg - $target, 1),
            'heightCm' => $height,
            'bmi' => $height ? round($kg / (($height / 100) ** 2), 1) : null,
            'chart' => WeightChart::build($series, $from->getTimestampMs(), $now->getTimestampMs(), $target),
            'from' => $from,
            'to' => $now,
        ];
    }

    /**
     * Beberapa catatan tensi terakhir, terbaru dulu. Tanpa kategori: kategori adalah aturan yang hanya ada di Android.
     *
     * @return list<array{systolic: int, diastolic: int, pulse: ?int, note: ?string, at: Carbon}>
     */
    public static function bloodPressuresFor(User $user, ?Carbon $now = null): array
    {
        $tz = self::timezone($user, $user->healthFocus);

        return $user->bloodPressureEntries()->orderByDesc('time_millis')->limit(self::BP_ROWS)->get()
            ->map(fn ($b) => [
                'systolic' => (int) $b->systolic,
                'diastolic' => (int) $b->diastolic,
                'pulse' => $b->pulse === null ? null : (int) $b->pulse,
                'note' => $b->note,
                'at' => Carbon::createFromTimestampMs($b->time_millis, $tz),
            ])->all();
    }

    /** Kapan HP terakhir mengirim sesuatu (data apa pun), di zona waktu pengguna. Null kalau belum pernah. */
    public static function lastSync(User $user): ?Carbon
    {
        $tz = self::timezone($user, $user->healthFocus);
        $times = array_filter([
            $user->healthFocus?->updated_at,
            $user->stepDays()->max('updated_at'),
            $user->weightEntries()->max('updated_at'),
            $user->bloodPressureEntries()->max('updated_at'),
        ]);
        if ($times === []) {
            return null;
        }

        return collect($times)->map(fn ($t) => Carbon::parse($t))->max()->copy()->setTimezone($tz);
    }
}
