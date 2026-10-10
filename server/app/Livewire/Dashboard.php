<?php

namespace App\Livewire;

use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Halaman utama setelah login (tahap 25 langkah 5 - shell kosong). Tahap 26 dan 27
 * menambah isi: daftar to-do tim dan follow-up kerja.
 */
class Dashboard extends Component
{
    public function render(): View
    {
        return view('livewire.dashboard')->layout('components.layout', ['title' => 'Beranda']);
    }
}
