<?php

namespace App\Livewire;

use App\Support\DaySummary;
use App\Support\UserDay;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Halaman utama setelah login. Tahap 33: baris tanggal dan strip 4 kartu ringkasan (habit, to-do,
 * follow-up, tugas rumah) sesuai mockup `Main.dc.html`, di atas kartu jadwal. Hanya membaca; tiap
 * kartu menaut ke halamannya. "Hari ini" mengikuti zona waktu HP (lihat [UserDay]).
 */
class Dashboard extends Component
{
    public function render(): View
    {
        $user = auth()->user();
        $now = UserDay::now($user);

        return view('livewire.dashboard', [
            'dateLabel' => $now->locale('id')->translatedFormat('l, j F Y'),
            'summary' => DaySummary::for($user, $now->toDateString()),
        ])->layout('components.layout', ['title' => 'Beranda']);
    }
}
