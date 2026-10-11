<?php

namespace App\Support\Schedule;

use App\Models\ScheduleBlock;

/** Blok jadwal dengan waktu yang sudah dihitung untuk satu tanggal, dalam menit sejak 00.00. */
final class ResolvedBlock
{
    public function __construct(
        public readonly ScheduleBlock $block,
        public readonly int $startMinute,
        public readonly int $endMinute,
    ) {}

    /** Durasi 0 = titik waktu (mis. batas tidur); tidak pernah jadi "Sekarang". */
    public function isPoint(): bool
    {
        return $this->endMinute <= $this->startMinute;
    }
}
