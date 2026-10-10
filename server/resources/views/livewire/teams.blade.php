<div>
    <h1>Tugas Rumah</h1>

    @if ($teams->isEmpty())
        <p>Belum gabung tim manapun.</p>
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
    @else
        @foreach ($teams as $team)
            <div style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
                <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 8px;">{{ $team->name }}</h2>
                <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0 0 16px;">
                    Anggota: {{ $team->members->pluck('name')->join(', ') }}
                </p>

                {{-- Daftar to-do --}}
                @forelse ($team->todos as $todo)
                    <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                        <input type="checkbox" wire:click="toggleDone({{ $todo->id }})" @checked($todo->done)>
                        <span style="flex: 1; {{ $todo->done ? 'text-decoration: line-through; color: var(--on-surface-variant);' : '' }}">
                            {{ $todo->title }}
                        </span>
                        <select wire:change="assignTodo({{ $todo->id }}, $event.target.value || null)" style="font-size: 12px; border: 1px solid var(--outline-variant); border-radius: 8px; padding: 4px 6px; background: var(--surface-container-low); color: var(--on-surface); font-family: 'Manrope', sans-serif;">
                            <option value="">Belum ditugaskan</option>
                            @foreach ($team->members as $member)
                                <option value="{{ $member->id }}" @selected($todo->assigned_to === $member->id)>{{ $member->name }}</option>
                            @endforeach
                        </select>
                        <button type="button" wire:click="deleteTodo({{ $todo->id }})" style="color: var(--error);">Hapus</button>
                    </div>
                @empty
                    <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada tugas.</p>
                @endforelse

                <form wire:submit="addTodo({{ $team->id }})" style="display: flex; gap: 8px; margin-top: 12px;">
                    <input type="text" wire:model="newTodoTitle.{{ $team->id }}" placeholder="Tugas baru..." style="flex: 1;">
                    <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
                </form>

                <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--outline-variant);">
                    <button wire:click="createInvite({{ $team->id }})" type="button" style="background: none; border: 1px solid var(--outline-variant); color: var(--on-surface-variant); padding: 8px 16px; border-radius: 999px; cursor: pointer; font-family: 'Manrope', sans-serif;">Buat Link Undangan</button>

                    @if ($inviteLink)
                        <p style="margin-top: 12px; font-size: 13px;">
                            Kirim link ini (berlaku 48 jam, sekali pakai):<br>
                            <code style="word-break: break-all;">{{ $inviteLink }}</code>
                        </p>
                    @endif
                </div>
            </div>
        @endforeach
    @endif
</div>
