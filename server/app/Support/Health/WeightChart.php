<?php

namespace App\Support\Health;

/**
 * Geometri grafik garis berat untuk SVG (tahap 30 langkah 5), dihitung di PHP supaya bisa dites dan halaman tidak butuh
 * JavaScript. Hanya angka dan skala: tidak ada aturan kesehatan di sini (kategori dan saran dihitung di Android).
 *
 * Koordinat dalam kotak [WIDTH] x [HEIGHT]. Sumbu x adalah waktu (milidetik) dari [$fromMillis] sampai [$toMillis], sumbu y
 * adalah kg dengan sedikit ruang di atas dan bawah, dan target (kalau ada) ikut menentukan rentang supaya garisnya selalu terlihat.
 */
class WeightChart
{
    public const WIDTH = 600;

    public const HEIGHT = 220;

    public const PAD_LEFT = 44;

    public const PAD_RIGHT = 14;

    public const PAD_TOP = 14;

    public const PAD_BOTTOM = 26;

    /**
     * @param  list<array{time_millis: int, kg: float}>  $points  urut waktu naik
     * @return array{points: list<array{x: float, y: float, kg: float, time_millis: int}>, line: string, targetY: float|null, minKg: float, maxKg: float, yTop: float, yBottom: float}|null
     */
    public static function build(array $points, int $fromMillis, int $toMillis, ?float $targetKg): ?array
    {
        if ($points === []) {
            return null;
        }

        $kgs = array_column($points, 'kg');
        if ($targetKg !== null) {
            $kgs[] = $targetKg;
        }
        $min = min($kgs);
        $max = max($kgs);
        // Rentang minimal 2 kg supaya satu titik, atau berat yang hampir sama, tidak menjadi garis datar yang ditarik ke tepi.
        $span = max($max - $min, 2.0);
        $center = ($max + $min) / 2;
        $minKg = floor(($center - $span / 2 - 0.5) * 2) / 2;
        $maxKg = ceil(($center + $span / 2 + 0.5) * 2) / 2;

        $plotW = self::WIDTH - self::PAD_LEFT - self::PAD_RIGHT;
        $plotH = self::HEIGHT - self::PAD_TOP - self::PAD_BOTTOM;
        $timeSpan = max($toMillis - $fromMillis, 1);

        $x = fn (int $t): float => round(self::PAD_LEFT + max(0.0, min(1.0, ($t - $fromMillis) / $timeSpan)) * $plotW, 1);
        $y = fn (float $kg): float => round(self::PAD_TOP + (1 - ($kg - $minKg) / ($maxKg - $minKg)) * $plotH, 1);

        $mapped = array_map(fn (array $p) => [
            'x' => $x($p['time_millis']), 'y' => $y($p['kg']), 'kg' => $p['kg'], 'time_millis' => $p['time_millis'],
        ], $points);

        return [
            'points' => $mapped,
            'line' => implode(' ', array_map(fn (array $p) => $p['x'].','.$p['y'], $mapped)),
            'targetY' => $targetKg === null ? null : $y($targetKg),
            'minKg' => $minKg,
            'maxKg' => $maxKg,
            'yTop' => (float) self::PAD_TOP,
            'yBottom' => (float) (self::PAD_TOP + $plotH),
        ];
    }
}
