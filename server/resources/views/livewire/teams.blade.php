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
            <button type="submit" class="primary" style="width: auto;">Buat Tim</button>
        </form>
    @else
        @foreach ($teams as $team)
            <div style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
                <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 8px;">{{ $team->name }}</h2>
                <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0 0 12px;">
                    Anggota: {{ $team->members->pluck('name')->join(', ') }}
                </p>

                <button wire:click="createInvite({{ $team->id }})" class="primary" style="width: auto;">Buat Link Undangan</button>

                @if ($inviteLink)
                    <p style="margin-top: 12px; font-size: 13px;">
                        Kirim link ini (berlaku 48 jam, sekali pakai):<br>
                        <code style="word-break: break-all;">{{ $inviteLink }}</code>
                    </p>
                @endif
            </div>
        @endforeach
    @endif
</div>
