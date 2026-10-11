<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class PrayerDay extends Model
{
    /** Nama kolom waktu sholat, sama dengan `PrayerName` di Android (huruf kecil). */
    public const PRAYERS = ['subuh', 'dzuhur', 'ashar', 'maghrib', 'isya'];

    // `date` sengaja tanpa cast: dibiarkan string Y-m-d supaya pencarian upsert per tanggal tepat.
    protected $fillable = ['user_id', 'date', 'timezone', ...self::PRAYERS];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /** Waktu sholat [$prayer] (huruf besar atau kecil) sebagai menit sejak 00.00, atau null kalau tidak ada. */
    public function minuteOf(string $prayer): ?int
    {
        $value = $this->getRawOriginal(strtolower($prayer));
        if ($value === null) {
            return null;
        }
        [$hour, $minute] = array_map('intval', explode(':', $value));

        return $hour * 60 + $minute;
    }
}
