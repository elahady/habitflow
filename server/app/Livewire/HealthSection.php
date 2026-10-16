<?php

namespace App\Livewire;

use App\Support\Health\HealthSummary;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Bagian "Kesehatan" di Beranda web (tahap 30 langkah 5): grafik berat 6 minggu terhadap target, batang langkah 7 hari, dan
 * catatan tensi terakhir. Hanya membaca data yang dikirim Android; web tidak punya form catat berat atau tensi, dan tidak
 * menghitung kategori (BMI, tensi), yang adalah aturan di Android.
 */
class HealthSection extends Component
{
    public function render(): View
    {
        $user = auth()->user();
        $data = HealthSummary::stepsFor($user);
        $steps = $data['steps'];

        // Batang langkah dinormalisasi ke yang terbesar antara target dan langkah tertinggi, supaya garis target selalu terlihat.
        $stepMax = max([$steps['target'] ?? 0, ...array_map(fn ($d) => $d['steps'] ?? 0, $steps['days']), 1]);

        return view('livewire.health-section', [
            'weight' => HealthSummary::weightFor($user),
            'steps' => $steps,
            'stepMax' => $stepMax,
            'bps' => HealthSummary::bloodPressuresFor($user),
            'lastSync' => HealthSummary::lastSync($user),
            'timezone' => $data['timezone'],
            'hasAnything' => $user->weightEntries()->exists() || $user->bloodPressureEntries()->exists() || $user->stepDays()->exists(),
        ]);
    }
}
