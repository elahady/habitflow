<div>
    <div style="font-size: 13px; color: var(--on-surface-variant); margin-bottom: 4px;">{{ $dateLabel }}</div>
    <h1 style="margin: 0 0 24px;">Selamat datang, {{ auth()->user()->name }}</h1>

    <livewire:health-focus-card />

    <livewire:schedule-card />

    <div class="hf-strip" style="margin-bottom: 16px;">
        <x-progress-card title="Habit hari ini" :href="route('habit')" :done="$summary['habits']['done']" :total="$summary['habits']['total']" empty="Belum ada habit.">
            <x-slot:icon>
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M8 12l2.5 2.5L16 9"></path></svg>
            </x-slot:icon>
        </x-progress-card>

        <x-progress-card title="To-do hari ini" :href="route('habit')" :done="$summary['todos']['done']" :total="$summary['todos']['total']" empty="Belum ada to-do hari ini.">
            <x-slot:icon>
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line></svg>
            </x-slot:icon>
        </x-progress-card>

        <a href="{{ route('follow-ups') }}" class="hf-card hf-stat" aria-label="Follow-up kerja">
            <div class="hf-stat-head">
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 12h-6l-2 3h-4l-2-3H2"></path><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path></svg>
                <span>Follow-up kerja</span>
            </div>
            <div class="hf-stat-rows">
                <div class="hf-stat-row" style="{{ $summary['followUps']['overdue'] > 0 ? 'color: var(--error); font-weight: 600;' : '' }}">
                    <span>Lewat tanggal</span><span class="hf-serif">{{ $summary['followUps']['overdue'] }}</span>
                </div>
                <div class="hf-stat-row"><span>Jatuh tempo hari ini</span><span class="hf-serif">{{ $summary['followUps']['today'] }}</span></div>
                <div class="hf-stat-row"><span>Inbox belum dirapikan</span><span class="hf-serif">{{ $summary['followUps']['inbox'] }}</span></div>
            </div>
        </a>

        <a href="{{ route('teams') }}" class="hf-card hf-stat" aria-label="Tugas Rumah: {{ $summary['teamMine'] }} belum selesai">
            <div class="hf-stat-head">
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></svg>
                <span>Tugas Rumah</span>
            </div>
            <div class="hf-serif hf-stat-value">{{ $summary['teamMine'] }}</div>
            <div class="hf-stat-note">punyaku belum selesai</div>
        </a>
    </div>

    <livewire:health-section />
</div>
