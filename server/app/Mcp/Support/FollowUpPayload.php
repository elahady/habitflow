<?php

namespace App\Mcp\Support;

use App\Models\FollowUp;
use Illuminate\Support\Carbon;

/** Bentuk follow-up yang dikembalikan tool MCP, dan penentuan `done_at` dari tanggal selesai. */
class FollowUpPayload
{
    /** @return array<string, mixed> */
    public static function of(FollowUp $item): array
    {
        return [
            'id' => $item->id,
            'title' => $item->title,
            'status' => $item->status,
            'date' => $item->date?->toDateString(),
            'picked_date' => $item->picked_date?->toDateString(),
            'person' => $item->person,
            'note' => $item->note,
            'done_at' => $item->done_at?->toIso8601String(),
        ];
    }

    /**
     * `done_at` untuk follow-up yang ditandai selesai. Tanpa [$doneOn] memakai yang sudah ada, atau sekarang;
     * dengan [$doneOn] (`Y-m-d`) memakai tengah hari di zona pengguna pada tanggal itu, supaya rekap tanggal tersebut
     * menghitungnya (rekap mengelompokkan per tanggal `done_at` di zona pengguna) dan tidak bergeser hari karena UTC.
     */
    public static function doneAt(?FollowUp $existing, ?string $doneOn, string $timezone): Carbon
    {
        if ($doneOn !== null) {
            return Carbon::parse($doneOn.' 12:00:00', $timezone)->utc();
        }

        return $existing?->done_at ?? now();
    }
}
