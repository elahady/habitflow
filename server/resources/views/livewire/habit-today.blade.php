<div style="display: flex; flex-direction: column; gap: 24px;">
    <div>
        <div style="font-size: 13px; color: var(--on-surface-variant); margin-bottom: 4px;">{{ $dateLabel }}</div>
        <h1 style="margin: 0;">Habit</h1>
    </div>

    {{-- Tiga kartu ringkas --}}
    <div class="hf-strip">
        <x-progress-card title="Habit hari ini" :done="$doneHabits" :total="$habits->count()" empty="Belum ada habit.">
            <x-slot:icon>
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M8 12l2.5 2.5L16 9"></path></svg>
            </x-slot:icon>
        </x-progress-card>

        <x-progress-card title="To-do hari ini" :done="$todos->where('done', true)->count()" :total="$todos->count()" empty="Belum ada to-do hari ini.">
            <x-slot:icon>
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line></svg>
            </x-slot:icon>
        </x-progress-card>

        <div class="hf-card hf-stat" aria-label="Streak: {{ $stats['currentStreak'] }} hari">
            <div class="hf-stat-head">
                <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"></path></svg>
                <span>Streak</span>
            </div>
            <div class="hf-serif hf-stat-value" style="margin-bottom: 4px;">{{ $stats['currentStreak'] }} hari</div>
            <div class="hf-stat-note">Terpanjang {{ $stats['longestStreak'] }} hari</div>
        </div>
    </div>

    {{-- Hari ini --}}
    <div class="hf-card" style="padding: 24px 28px;">
        <h2 class="hf-section-title" style="font-size: 18px; margin-bottom: 4px;">Hari ini</h2>
        <p style="font-size: 12px; color: var(--on-surface-variant); margin: 0 0 12px;">Habit wajib selalu tampil di atas dan ikut menentukan level hari dan streak. To-do tidak memengaruhi streak.</p>

        @forelse ($habits as $habit)
            <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                <input type="checkbox" id="habit-{{ $habit->id }}" wire:click="toggleHabit({{ $habit->id }})" @checked($habit->entries->isNotEmpty())>
                <label for="habit-{{ $habit->id }}" style="flex: 1; margin: 0; font-size: 15px; font-weight: 400; color: inherit;">
                    {{ $habit->name }}
                    @if ($habit->is_mandatory)
                        <span class="hf-chip" style="margin-left: 6px;">Wajib</span>
                    @endif
                </label>
            </div>
        @empty
            <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada habit. Tambahkan di bagian Kelola Habit di bawah.</p>
        @endforelse

        <h3 style="font-size: 14px; margin: 20px 0 8px;">To-do hari ini ({{ $todos->count() }}/5)</h3>
        @forelse ($todos as $todo)
            <div style="display: flex; align-items: center; gap: 10px; padding: 6px 0;">
                <input type="checkbox" id="todo-{{ $todo->id }}" wire:click="toggleTodo({{ $todo->id }})" @checked($todo->done)>
                <label for="todo-{{ $todo->id }}" style="flex: 1; margin: 0; font-size: 15px; font-weight: 400; {{ $todo->done ? 'text-decoration: line-through; color: var(--on-surface-variant);' : 'color: inherit;' }}">{{ $todo->title }}</label>
                <button type="button" @click="$dispatch('confirm-action', { message: 'Hapus to-do ini?', action: () => $wire.deleteTodo({{ $todo->id }}) })" style="color: var(--error); background: none; border: none; cursor: pointer;">Hapus</button>
            </div>
        @empty
            <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada to-do hari ini.</p>
        @endforelse

        @if ($todos->count() < 5)
            <form wire:submit="addTodo" style="display: flex; gap: 8px; margin-top: 12px;">
                <input type="text" wire:model="newTodoTitle" placeholder="To-do baru..." aria-label="To-do baru" style="flex: 1;">
                <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
            </form>
        @endif
    </div>

    {{-- Kontribusi --}}
    <section aria-labelledby="kontribusi-judul" style="display: flex; flex-direction: column; gap: 16px;">
        <div style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 8px 24px;">
            <h2 id="kontribusi-judul" class="hf-section-title" style="font-size: 22px;">Kontribusi</h2>
            <span style="font-size: 13px; color: var(--on-surface-variant);">Terisi otomatis setiap habit dicentang, sinkron dari HP.</span>
        </div>

        <div class="hf-card" style="padding: 22px 24px; display: flex; flex-direction: column; gap: 14px;">
            <span style="font-size: 13px; font-weight: 600; color: var(--on-surface-variant);">Gabungan, {{ \App\Support\HabitStats::COMBINED_WEEKS }} minggu terakhir</span>
            <x-heatmap :levels="$stats['combined']" :start="$stats['start']" :weeks="\App\Support\HabitStats::COMBINED_WEEKS" :today="$today"
                       label="Heatmap gabungan {{ \App\Support\HabitStats::COMBINED_WEEKS }} minggu terakhir, makin gelap berarti makin banyak habit selesai hari itu" />
            <div class="hf-legend">
                <span>Sedikit</span>
                @foreach ([0, 1, 2, 3, 4] as $level)
                    <i class="hf-heat-{{ $level }}"></i>
                @endforeach
                <span>Banyak</span>
            </div>
        </div>

        @if ($stats['perHabit'])
            <div class="hf-card" style="padding: 22px 24px; display: flex; flex-direction: column; gap: 18px;">
                <div>
                    <span style="font-size: 13px; font-weight: 600; color: var(--on-surface-variant);">Per habit</span><br>
                    <span style="font-size: 12px; color: var(--on-surface-variant);">{{ \App\Support\HabitStats::MINI_WEEKS }} minggu terakhir. Warna biner: selesai atau belum.</span>
                </div>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 24px;">
                    @foreach ($stats['perHabit'] as $item)
                        <div style="min-width: 0;">
                            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                                <span style="font-size: 14px; font-weight: 600;">{{ $item['habit']->name }}</span>
                                @if ($item['habit']->is_mandatory)
                                    <span class="hf-chip">Wajib</span>
                                @endif
                            </div>
                            <x-heatmap :levels="$item['levels']" :start="$miniStart" :weeks="\App\Support\HabitStats::MINI_WEEKS" :today="$today" :mini="true" :binary="true"
                                       label="Heatmap {{ $item['habit']->name }}, {{ \App\Support\HabitStats::MINI_WEEKS }} minggu terakhir" />
                        </div>
                    @endforeach
                </div>
            </div>
        @endif
    </section>

    {{-- Kelola Habit --}}
    <div class="hf-card" style="padding: 24px 28px;">
        <h2 class="hf-section-title" style="font-size: 18px; margin-bottom: 12px;">Kelola Habit</h2>

        @foreach ($habits as $habit)
            <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                @if (array_key_exists($habit->id, $editingName))
                    <input type="text" wire:model="editingName.{{ $habit->id }}" aria-label="Nama habit" style="flex: 1;">
                    <button type="button" wire:click="saveName({{ $habit->id }})" class="primary" style="width: auto;">Simpan</button>
                @else
                    <span style="flex: 1;">{{ $habit->name }}</span>
                    <button type="button" wire:click="startEditing({{ $habit->id }})" style="background: none; border: none; color: var(--on-surface-variant); cursor: pointer;">Ubah</button>
                @endif

                <label style="display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--on-surface-variant); margin: 0;">
                    <input type="checkbox" @checked($habit->is_mandatory)
                        @if ($habit->is_mandatory)
                            @click.prevent="$dispatch('confirm-action', { message: 'Matikan status wajib habit ini?', action: () => $wire.toggleMandatory({{ $habit->id }}) })"
                        @else
                            wire:click="toggleMandatory({{ $habit->id }})"
                        @endif>
                    Wajib
                </label>

                @unless ($habit->is_mandatory)
                    <button type="button" @click="$dispatch('confirm-action', { message: 'Hapus habit ini? Riwayat centangnya ikut terhapus.', action: () => $wire.deleteHabit({{ $habit->id }}) })" style="color: var(--error); background: none; border: none; cursor: pointer;">Hapus</button>
                @endunless
            </div>
        @endforeach

        <form wire:submit="addHabit" style="display: flex; gap: 8px; margin-top: 12px;">
            <input type="text" wire:model="newHabitName" placeholder="Habit baru..." aria-label="Habit baru" style="flex: 1;">
            <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
        </form>
        @error('newHabitName')
            <p class="error-text">{{ $message }}</p>
        @enderror
    </div>
</div>
