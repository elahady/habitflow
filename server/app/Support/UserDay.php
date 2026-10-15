<?php

namespace App\Support;

use App\Models\User;
use Illuminate\Support\Carbon;

/**
 * "Hari ini" menurut pengguna, bukan menurut server. Zona waktu aplikasi adalah UTC, jadi `Carbon::today()`
 * baru berganti hari pukul 07.00 WIB: antara 00.00 dan 07.00 halaman web masih menampilkan kemarin. Zona
 * yang dipakai adalah yang terakhir dikirim HP (`prayer_days`), dengan cadangan Asia/Jakarta sebelum HP
 * pernah mengirim. Semua halaman web yang bergantung pada tanggal harus lewat sini supaya angkanya sama.
 */
class UserDay
{
    /** Dipakai sebelum HP pernah mengirim zona waktu. */
    public const FALLBACK_TIMEZONE = 'Asia/Jakarta';

    public static function timezone(User $user): string
    {
        return $user->prayerDays()->latest('updated_at')->value('timezone') ?? self::FALLBACK_TIMEZONE;
    }

    public static function now(User $user): Carbon
    {
        return Carbon::now(self::timezone($user));
    }

    /** Tanggal hari ini pengguna dalam bentuk `Y-m-d`. */
    public static function today(User $user): string
    {
        return self::now($user)->toDateString();
    }
}
