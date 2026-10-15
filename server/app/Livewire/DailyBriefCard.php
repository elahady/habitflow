<?php

namespace App\Livewire;

use Illuminate\Contracts\View\View;
use Illuminate\Support\Carbon;
use Livewire\Component;

/**
 * Kartu "Ringkasan dari Claude" di halaman Kerja web (tahap 32 langkah 4). Hanya membaca: isinya
 * ditulis skill `habitflow-morning-routine` lewat POST /api/v1/daily-briefs, tidak pernah dari form
 * di web, supaya sumbernya tidak tercampur dengan Catatan EOD yang ditulis manual. "Hari ini" dihitung
 * di zona waktu terakhir yang dikirim HP (sama dengan ScheduleCard), bukan zona server (UTC).
 */
class DailyBriefCard extends Component
{
    public function render(): View
    {
        $user = auth()->user();
        $timezone = $user->prayerDays()->latest('updated_at')->value('timezone') ?? ScheduleCard::FALLBACK_TIMEZONE;
        $today = Carbon::now($timezone)->toDateString();

        // Ringkasan hari ini kalau ada; kalau belum, yang terakhir dengan tanggalnya, supaya kartu
        // tidak kosong sebelum skill jalan tapi juga tidak berpura-pura itu ringkasan hari ini.
        $brief = $user->dailyBriefs()->whereNotNull('email_digest')->where('date', '<=', $today)->orderByDesc('date')->first();

        return view('livewire.daily-brief-card', [
            'brief' => $brief,
            'isToday' => $brief?->date === $today,
            'updatedAt' => $brief?->updated_at?->setTimezone($timezone),
            'timezone' => $timezone,
        ]);
    }
}
