<?php

namespace App\Livewire;

use App\Support\Health\HealthSummary;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Carbon;
use Livewire\Component;

/**
 * Kartu hero "Fokus hari ini" di Beranda web (tahap 30 langkah 5). Hanya membaca: fokus dihitung di Android
 * (`domain/health/Focus.kt`) dan dikirim bersama teksnya lewat `PUT /api/v1/health`; web tidak punya salinan aturan,
 * jadi kalimat yang tampil sama persis dengan di HP. Tanpa form dan tanpa tombol catat.
 */
class HealthFocusCard extends Component
{
    public function render(): View
    {
        $user = auth()->user();
        $data = HealthSummary::stepsFor($user);
        $focus = HealthSummary::focus($user);

        $computedAt = $focus?->computed_at?->copy()->setTimezone($data['timezone']);
        $stale = $computedAt !== null
            && ($computedAt->toDateString() !== $data['today'] || $computedAt->diffInHours(Carbon::now()) >= HealthSummary::STALE_HOURS);

        return view('livewire.health-focus-card', [
            'focus' => $focus,
            'steps' => $data['steps'],
            'computedAt' => $computedAt,
            'stale' => $stale,
            'timezone' => $data['timezone'],
        ]);
    }
}
