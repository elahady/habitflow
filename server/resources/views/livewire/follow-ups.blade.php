<div>
    <h1>Follow-up Kerja</h1>

    <form wire:submit="add" style="display: flex; gap: 8px; margin-bottom: 16px;">
        <input type="text" wire:model="newTitle" placeholder="Catat cepat ke Inbox..." style="flex: 1;">
        <button type="submit" class="primary" style="width: auto; white-space: nowrap;">Catat</button>
    </form>
    @error('newTitle')
        <p class="error-text">{{ $message }}</p>
    @enderror

    @foreach ($sections as $status => [$label, $items])
        <div style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
            <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 12px;">{{ $label }} ({{ $items->count() }})</h2>

            @forelse ($items as $item)
                @if ($editingId === $item->id)
                    <div style="padding: 12px 0; border-bottom: 1px solid var(--outline-variant);">
                        @if ($conflict)
                            <div style="background: var(--primary-fixed); color: var(--on-primary-container); border-radius: 12px; padding: 12px 16px; margin-bottom: 12px; font-size: 14px;">
                                <strong>Berubah di tempat lain ({{ $conflict['updated_at'] }}).</strong>
                                <p style="margin: 6px 0;">Versi terbaru: "{{ $conflict['title'] }}", {{ $conflict['status'] }}@if ($conflict['date']), {{ $conflict['date'] }}@endif @if ($conflict['time']) {{ $conflict['time'] }}@endif @if ($conflict['person']), {{ $conflict['person'] }}@endif.</p>
                                <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                                    <button type="button" wire:click="overwrite" class="primary" style="width: auto;">Timpa dengan punyaku</button>
                                    <button type="button" wire:click="useLatest" style="background: none; border: 1px solid var(--outline-variant); color: inherit; padding: 10px 20px; border-radius: 999px; cursor: pointer;">Pakai yang terbaru</button>
                                </div>
                            </div>
                        @endif

                        <form wire:submit="save" style="display: block;">
                            <div class="field">
                                <label>Judul</label>
                                <input type="text" wire:model="form.title">
                                @error('form.title') <p class="error-text">{{ $message }}</p> @enderror
                            </div>
                            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px;">
                                <div class="field">
                                    <label>Status</label>
                                    <select wire:model="form.status">
                                        <option value="INBOX">Inbox</option>
                                        <option value="ACTIVE">Aktif</option>
                                        <option value="WAITING">Menunggu</option>
                                        <option value="DONE">Selesai</option>
                                    </select>
                                </div>
                                <div class="field">
                                    <label>Tanggal</label>
                                    <input type="date" wire:model="form.date">
                                    @error('form.date') <p class="error-text">{{ $message }}</p> @enderror
                                </div>
                                <div class="field">
                                    <label>Jam</label>
                                    <input type="time" wire:model="form.time">
                                    @error('form.time') <p class="error-text">{{ $message }}</p> @enderror
                                </div>
                                <div class="field">
                                    <label>Orang</label>
                                    <input type="text" wire:model="form.person">
                                </div>
                            </div>
                            <div class="field">
                                <label>Catatan</label>
                                <textarea wire:model="form.note" rows="3"></textarea>
                            </div>
                            <div style="display: flex; gap: 8px;">
                                <button type="submit" class="primary" style="width: auto;">Simpan</button>
                                <button type="button" wire:click="cancel" style="background: none; border: none; color: var(--on-surface-variant); cursor: pointer;">Batal</button>
                            </div>
                        </form>
                    </div>
                @else
                    <div style="display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--outline-variant);">
                        @if ($status === 'DONE')
                            <input type="checkbox" checked wire:click="reopen({{ $item->id }})">
                        @else
                            <input type="checkbox" wire:click="markDone({{ $item->id }})">
                        @endif
                        <div style="flex: 1; min-width: 0;">
                            <div style="{{ $status === 'DONE' ? 'text-decoration: line-through; color: var(--on-surface-variant);' : '' }}">{{ $item->title }}</div>
                            <div style="font-size: 12px; color: var(--on-surface-variant);">
                                @if ($item->date)
                                    {{ $item->date->format('d M Y') }}@if ($item->time) {{ substr($item->time, 0, 5) }}@endif
                                    @if ($status !== 'DONE' && $status !== 'INBOX' && $item->date->toDateString() < $today)
                                        <span style="color: var(--error); font-weight: 600;">Terlambat</span>
                                    @endif
                                @endif
                                @if ($item->person) · {{ $item->person }} @endif
                            </div>
                        </div>
                        <button type="button" wire:click="edit({{ $item->id }})" style="background: none; border: none; color: var(--on-surface-variant); cursor: pointer;">Ubah</button>
                        <button type="button" wire:click="delete({{ $item->id }})" wire:confirm="Hapus follow-up ini?" style="color: var(--error); background: none; border: none; cursor: pointer;">Hapus</button>
                    </div>
                @endif
            @empty
                <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0;">Kosong.</p>
            @endforelse
        </div>
    @endforeach
</div>
