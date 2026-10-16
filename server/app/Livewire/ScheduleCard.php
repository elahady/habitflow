<?php

namespace App\Livewire;

use App\Support\Schedule\ScheduleResolver;
use App\Support\UserDay;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Kartu "Sekarang / Berikutnya" di dashboard web (tahap 29 langkah 3). Hanya membaca: blok dari
 * tabel `schedule_blocks` dan waktu sholat dari cache yang dikirim Android (`prayer_days`, opsi a).
 * Jam "sekarang" dihitung di zona waktu terakhir yang dikirim HP, bukan zona server (UTC).
 */
class ScheduleCard extends Component
{
    public function render(): View
    {
        $user = auth()->user();
        $latest = $user->prayerDays()->latest('updated_at')->first();
        $now = UserDay::now($user);
        $today = $user->prayerDays()->where('date', $now->toDateString())->first();

        $blocks = $user->scheduleBlocks()->orderBy('sort_order')->orderBy('id')->get();
        $dayOff = $user->daysOff()->where('date', $now->toDateString())->exists();
        $resolved = ScheduleResolver::resolve($blocks, $today, $now, $dayOff);
        $nowMinute = $now->hour * 60 + $now->minute;

        return view('livewire.schedule-card', [
            'hasBlocks' => $blocks->isNotEmpty(),
            'dayOff' => $dayOff,
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
