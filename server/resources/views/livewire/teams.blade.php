<div style="display: flex; flex-direction: column; gap: 24px;">
    <div>
        <div style="font-size: 13px; color: var(--on-surface-variant); margin-bottom: 4px;">{{ $dateLabel }}</div>
        <h1 style="margin: 0;">Tugas Rumah</h1>
    </div>

    @if ($teams->isEmpty())
        <div class="hf-card" style="padding: 24px 28px;">
            <h2 class="hf-section-title" style="font-size: 18px; margin-bottom: 4px;">Belum gabung tim manapun</h2>
            <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0 0 16px;">Buat tim baru, lalu kirim link undangan ke anggota keluarga. Atau buka link undangan dari mereka.</p>
            <form wire:submit="createTeam">
                <div class="field">
                    <label for="newTeamName">Nama tim (mis. "Rumah Roziq")</label>
                    <input type="text" id="newTeamName" wire:model="newTeamName" required>
                </div>
                @error('newTeamName')
                    <p class="error-text">{{ $message }}</p>
                @enderror
                <button type="submit" class="primary">Buat Tim</button>
            </form>
        </div>
    @else
        {{-- Dua kartu ringkas --}}
        <div class="hf-strip">
            <x-progress-card title="Tugas saya" :done="$mineTotal - $mineOpen" :total="$mineTotal" empty="Belum ada tugas untuk Anda.">
                <x-slot:icon>
                    <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                </x-slot:icon>
            </x-progress-card>

            <x-progress-card title="Semua tugas tim" :done="$allDone" :total="$allTotal" empty="Belum ada tugas di tim manapun.">
                <x-slot:icon>
                    <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
                </x-slot:icon>
            </x-progress-card>
        </div>

        @foreach ($teams as $team)
            <section class="hf-card" style="padding: 24px 28px;" aria-labelledby="tim-{{ $team->id }}">
                <h2 id="tim-{{ $team->id }}" class="hf-section-title" style="font-size: 20px; margin-bottom: 8px;">{{ $team->name }}</h2>
                <div style="display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 16px;">
                    <span class="hf-label">Anggota:</span>
                    @foreach ($team->members as $member)
                        <span class="hf-chip {{ $member->id === auth()->id() ? '' : 'neutral' }}">{{ $member->name }}{{ $member->id === auth()->id() ? ' (Anda)' : '' }}</span>
                    @endforeach
                </div>

                {{-- Daftar to-do --}}
                @forelse ($team->todos as $todo)
                    <div class="hf-row" style="align-items: center;">
                        <input type="checkbox" class="hf-check-sm" id="tugas-{{ $todo->id }}" aria-label="Selesai: {{ $todo->title }}" wire:click="toggleDone({{ $todo->id }})" @checked($todo->done) style="margin-top: 0;">
                        <label for="tugas-{{ $todo->id }}" style="flex: 1; margin: 0; font-size: 15px; font-weight: 400; {{ $todo->done ? 'text-decoration: line-through; color: var(--on-surface-variant);' : 'color: inherit;' }}">{{ $todo->title }}</label>
                        <select aria-label="Ditugaskan ke" wire:change="assignTodo({{ $todo->id }}, $event.target.value || null)" style="font-size: 12px; border: 1px solid var(--outline-variant); border-radius: 8px; padding: 4px 6px; background: var(--surface-container-low); color: var(--on-surface); font-family: 'Manrope', sans-serif;">
                            <option value="">Belum ditugaskan</option>
                            @foreach ($team->members as $member)
                                <option value="{{ $member->id }}" @selected($todo->assigned_to === $member->id)>{{ $member->name }}</option>
                            @endforeach
                        </select>
                        <button type="button" class="hf-btn-text" wire:click="deleteTodo({{ $todo->id }})" wire:confirm="Hapus tugas ini?" style="color: var(--error);">Hapus</button>
                    </div>
                @empty
                    <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada tugas.</p>
                @endforelse

                <form wire:submit="addTodo({{ $team->id }})" style="display: flex; gap: 8px; margin-top: 12px;">
                    <input type="text" wire:model="newTodoTitle.{{ $team->id }}" placeholder="Tugas baru..." aria-label="Tugas baru untuk {{ $team->name }}" style="flex: 1;">
                    <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
                </form>

                <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--outline-variant);">
                    <button wire:click="createInvite({{ $team->id }})" type="button" class="hf-btn-tonal">Buat Link Undangan</button>

                    @if ($inviteLink && $inviteTeamId === $team->id)
                        <p style="margin: 12px 0 0; font-size: 13px;">
                            Kirim link ini (berlaku 48 jam, sekali pakai):<br>
                            <code style="word-break: break-all;">{{ $inviteLink }}</code>
                        </p>
                    @endif
                </div>
            </section>
        @endforeach
    @endif
</div>
