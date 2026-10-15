<?php

namespace App\Support;

use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;

/**
 * Teks ringkasan EOD untuk dibagikan, salinan `buildEodSummary` di `domain/work/EodSummary.kt` (Android).
 * Hanya kelompok yang berisi yang ditampilkan. Server tidak tahu "Pindah tanggal" (itu mengubah tanggal
 * follow-up, bukan catatan terpisah), jadi kelompok itu tidak ada di web.
 */
class EodSummary
{
    /**
     * @param  Collection<int, \App\Models\FollowUp>  $done  selesai pada tanggal itu
     * @param  Collection<int, \App\Models\FollowUp>  $tomorrow  dipilih untuk besok
     * @param  Collection<int, \App\Models\FollowUp>  $waiting  menunggu, dengan tanggal cek ulang kalau ada
     */
    public static function build(string $date, Collection $done, Collection $tomorrow, Collection $waiting, ?string $note): string
    {
        $lines = ['EOD '.Carbon::parse($date)->locale('id')->translatedFormat('l, j F Y')];

        $group = function (string $label, array $entries) use (&$lines): void {
            if ($entries === []) {
                return;
            }
            $lines[] = '';
            $lines[] = $label.':';
            foreach ($entries as $entry) {
                $lines[] = '- '.$entry;
            }
        };

        $group('Selesai', $done->pluck('title')->all());
        $group('Lanjut besok', $tomorrow->pluck('title')->all());
        $group('Menunggu', $waiting->map(fn ($f) => $f->title.($f->date ? ' (cek '.$f->date->locale('id')->translatedFormat('j M').')' : ''))->all());

        $clean = trim((string) $note);
        if ($clean !== '') {
            $lines[] = '';
            $lines[] = 'Catatan:';
            $lines[] = $clean;
        }

        return implode("\n", $lines);
    }
}
