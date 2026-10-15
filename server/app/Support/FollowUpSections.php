<?php

namespace App\Support;

use App\Models\FollowUp;

/**
 * Salinan PHP dari `sectionFor`, `isToday`, dan `isOverdue` di `domain/work/FollowUps.kt` (Android).
 * Dipakai Beranda ([DaySummary]) dan halaman Kerja ([WorkRecap]) supaya angka web sama dengan di HP.
 * Kalau satu sisi berubah, ubah keduanya.
 */
class FollowUpSections
{
    public const INBOX = 'INBOX';

    public const TODAY = 'TODAY';

    public const OVERDUE = 'OVERDUE';

    public const WAITING = 'WAITING';

    public const LATER = 'LATER';

    /**
     * Kelompok follow-up untuk [$today] (`Y-m-d`). Urutan prioritas: Inbox, Hari ini, Lewat tanggal, Menunggu, Nanti.
     * Follow-up yang sudah selesai tidak masuk kelompok mana pun (null).
     */
    public static function for(FollowUp $item, string $today): ?string
    {
        if ($item->status === 'DONE') {
            return null;
        }
        if ($item->status === 'INBOX') {
            return self::INBOX;
        }

        $date = $item->date?->toDateString();
        $picked = $item->picked_date?->toDateString() === $today;

        return match (true) {
            $picked || $date === $today => self::TODAY,
            $date !== null && $date < $today => self::OVERDUE,
            $item->status === 'WAITING' => self::WAITING,
            default => self::LATER,
        };
    }
}
