<?php

namespace App\Livewire;

use App\Support\Schedule\ScheduleResolver;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Carbon;
use Livewire\Component;

/**
 * Kartu "Sekarang / Berikutnya" di dashboard web (tahap 29 langkah 3). Hanya membaca: blok dari
 * tabel `schedule_blocks` dan waktu sholat dari cache yang dikirim Android (`prayer_days`, opsi a).
 * Jam "sekarang" dihitung di zona waktu terakhir yang dikirim HP, bukan zona server (UTC).
 */
class ScheduleCard extends Component
{
    /** Dipakai sebelum HP pernah mengirim zona waktu. */
    public const FALLBACK_TIMEZONE = 'Asia/Jakarta';

    public function render(): View
    {
        $user = auth()->user();
        $latest = $user->prayerDays()->latest('updated_at')->first();
        $now = Carbon::now($latest?->timezone ?? self::FALLBACK_TIMEZONE);
        $today = $user->prayerDays()->where('date', $now->toDateString())->first();

        $blocks = $user->scheduleBlocks()->orderBy('sort_order')->orderBy('id')->get();
        $resolved = ScheduleResolver::resolve($blocks, $today, $now);
        $nowMinute = $now->hour * 60 + $now->minute;

        return view('livewire.schedule-card', [
            'hasBlocks' => $blocks->isNotEmpty(),
            'resolvedCount' => $resolved->count(),
            // Blok berpatokan sholat yang terbuang karena waktu sholat hari ini belum dikirim HP.
            'missingPrayer' => $today === null && $blocks->contains(fn ($b) => $b->start_type === 1),
            'lastSync' => $latest?->updated_at?->setTimezone($now->getTimezone()),
            'timezone' => $now->getTimezone()->getName(),
            'nowMinute' => $nowMinute,
            ...ScheduleResolver::nowAndNext($resolved, $nowMinute),
        ]);
    }
}
