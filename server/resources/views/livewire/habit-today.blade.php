<div>
    <h1>Habit</h1>

    {{-- Hari ini --}}
    <div style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
        <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 12px;">Hari ini</h2>

        @forelse ($habits as $habit)
            <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                <input type="checkbox" wire:click="toggleHabit({{ $habit->id }})" @checked($habit->entries->isNotEmpty())>
                <span style="flex: 1;">
                    {{ $habit->name }}
                    @if ($habit->is_mandatory)
                        <span style="font-size: 11px; color: var(--primary); font-weight: 600;">Wajib</span>
                    @endif
                </span>
            </div>
        @empty
            <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada habit. Tambahkan di bagian Kelola Habit di bawah.</p>
        @endforelse

        <h3 style="font-size: 14px; margin: 16px 0 8px;">To-do ({{ $todos->count() }}/5)</h3>
        @forelse ($todos as $todo)
            <div style="display: flex; align-items: center; gap: 10px; padding: 6px 0;">
                <input type="checkbox" wire:click="toggleTodo({{ $todo->id }})" @checked($todo->done)>
                <span style="flex: 1; {{ $todo->done ? 'text-decoration: line-through; color: var(--on-surface-variant);' : '' }}">{{ $todo->title }}</span>
                <button type="button" wire:click="deleteTodo({{ $todo->id }})" wire:confirm="Hapus to-do ini?" style="color: var(--error); background: none; border: none; cursor: pointer;">Hapus</button>
            </div>
        @empty
            <p style="font-size: 13px; color: var(--on-surface-variant);">Belum ada to-do hari ini.</p>
        @endforelse

        @if ($todos->count() < 5)
            <form wire:submit="addTodo" style="display: flex; gap: 8px; margin-top: 12px;">
                <input type="text" wire:model="newTodoTitle" placeholder="To-do baru..." style="flex: 1;">
                <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
            </form>
        @endif
    </div>

    {{-- Kelola Habit --}}
    <div style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px;">
        <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 12px;">Kelola Habit</h2>

        @foreach ($habits as $habit)
            <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                @if (array_key_exists($habit->id, $editingName))
                    <input type="text" wire:model="editingName.{{ $habit->id }}" style="flex: 1;">
                    <button type="button" wire:click="saveName({{ $habit->id }})" class="primary" style="width: auto;">Simpan</button>
                @else
                    <span style="flex: 1;">{{ $habit->name }}</span>
                    <button type="button" wire:click="startEditing({{ $habit->id }}, '{{ $habit->name }}')" style="background: none; border: none; color: var(--on-surface-variant); cursor: pointer;">Ubah</button>
                @endif

                <label style="display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--on-surface-variant);">
                    <input type="checkbox" wire:click="toggleMandatory({{ $habit->id }})" @checked($habit->is_mandatory) @if ($habit->is_mandatory) wire:confirm="Matikan status wajib habit ini?" @endif>
                    Wajib
                </label>

                @unless ($habit->is_mandatory)
                    <button type="button" wire:click="deleteHabit({{ $habit->id }})" wire:confirm="Hapus habit ini? Riwayat centangnya ikut terhapus." style="color: var(--error); background: none; border: none; cursor: pointer;">Hapus</button>
                @endunless
            </div>
        @endforeach

        <form wire:submit="addHabit" style="display: flex; gap: 8px; margin-top: 12px;">
            <input type="text" wire:model="newHabitName" placeholder="Habit baru..." style="flex: 1;">
            @error('newHabitName')
                <p class="error-text">{{ $message }}</p>
            @enderror
            <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Tambah</button>
        </form>
    </div>
</div>
