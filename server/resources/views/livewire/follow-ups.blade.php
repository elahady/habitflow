<div style="display: flex; flex-wrap: wrap; align-items: flex-start; gap: 24px;">

    {{-- Riwayat rekap --}}
    <aside class="hf-card" aria-label="Riwayat rekap harian" style="flex: 0 1 240px; min-width: 220px; padding: 16px 10px; display: flex; flex-direction: column; gap: 2px; max-height: 760px; overflow-y: auto;">
        <div class="hf-eyebrow" style="padding: 2px 12px 10px;">Riwayat rekap</div>
        @foreach ($history as $day)
            <button type="button" wire:click="selectDate('{{ $day['date'] }}')" wire:key="hist-{{ $day['date'] }}" class="hf-date-btn {{ $day['date'] === $selected ? 'active' : '' }}" @if ($day['date'] === $selected) aria-current="true" @endif>
                <span class="d">{{ \Illuminate\Support\Carbon::parse($day['date'])->locale('id')->translatedFormat('j F Y') }}</span>
                <span class="m">
                    @if ($day['date'] === $today)
                        Hari ini · {{ $day['eodAt'] ? 'EOD '.$day['eodAt']->format('H.i') : 'belum EOD' }}
                    @else
                        {{ ($day['eodTotal'] ?? 0) > 0 ? $day['eodDone'].' dari '.$day['eodTotal'] : $day['done'] }} selesai{{ $day['eodAt'] ? ' · EOD '.$day['eodAt']->format('H.i') : '' }}
                    @endif
                </span>
            </button>
        @endforeach
        @if ($hasMoreHistory)
            <div style="padding: 10px 12px 2px;">
                <button type="button" wire:click="loadOlder" class="hf-btn-text">Muat lebih lama…</button>
            </div>
        @endif
    </aside>

    <div style="flex: 1 1 640px; min-width: 0; display: flex; flex-direction: column; gap: 24px;">

        <div style="display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px 16px;">
            <div>
                <div style="font-size: 13px; color: var(--on-surface-variant); margin-bottom: 4px;">{{ $dateLabel }}</div>
                <h1 style="margin: 0;">{{ $recap['isToday'] ? 'Rekap hari ini' : 'Rekap harian' }}</h1>
            </div>
            @if ($eodAt)
                <span class="hf-chip">EOD · {{ $eodAt->format('H.i') }}</span>
            @endif
        </div>

        {{-- Ringkasan --}}
        <section class="hf-focus" aria-labelledby="ringkasan-judul" style="padding: 28px 32px; display: flex; flex-wrap: wrap; gap: 32px;">
            <div style="flex: 1 1 340px; min-width: 0; display: flex; flex-direction: column; gap: 14px;">
                <div class="hf-eyebrow" style="color: inherit;">Ringkasan {{ $recap['isToday'] ? 'hari ini' : 'hari itu' }}</div>
                <h2 id="ringkasan-judul" class="hf-serif" style="font-size: 30px; line-height: 1.2; margin: 0; text-wrap: balance;">
                    @if ($recap['isToday'])
                        @if ($recap['total'] > 0)
                            {{ $recap['done'] }} dari {{ $recap['total'] }} follow-up selesai
                        @else
                            Belum ada follow-up hari ini
                        @endif
                    @elseif (($eodTotal ?? 0) > 0)
                        {{ $eodDone }} dari {{ $eodTotal }} follow-up selesai
                    @else
                        {{ $recap['done'] }} follow-up selesai
                    @endif
                </h2>
                <p style="font-size: 15px; line-height: 1.65; margin: 0; max-width: 56ch;">
                    @if ($recap['isToday'])
                        @php
                            $notes = array_filter([
                                $recap['waiting'] > 0 ? $recap['waiting'].' follow-up menunggu balasan.' : null,
                                $recap['tomorrow']->count() > 0 ? $recap['tomorrow']->count().' dipilih untuk besok.' : null,
                                $recap['inbox'] > 0 ? $recap['inbox'].' item Inbox belum dirapikan.' : null,
                            ]);
                        @endphp
                        {{ $notes ? implode(' ', $notes) : 'Tidak ada yang tertunda.' }}
                    @else
                        {{ $eodNote !== '' ? $eodNote : 'Tidak ada catatan EOD untuk hari ini.' }}
                    @endif
                </p>
                <div style="display: flex; flex-wrap: wrap; gap: 8px;">
                    <span class="hf-chip on">✓ Selesai {{ $recap['done'] }}</span>
                    @if ($recap['isToday'])
                        <span class="hf-chip on">Menunggu {{ $recap['waiting'] }}</span>
                        <span class="hf-chip on">Lanjut besok {{ $recap['tomorrow']->count() }}</span>
                    @endif
                </div>
            </div>

            @if ($recap['isToday'])
                <div style="flex: 1 1 240px; min-width: 0; border-left: 1px solid currentColor; padding-left: 32px; display: flex; flex-direction: column; gap: 16px;">
                    <div class="hf-eyebrow" style="color: inherit;">Rincian status</div>
                    <div class="hf-stat-row" style="color: inherit;"><span>Tingkat selesai</span><span class="hf-serif">{{ $recap['rate'] }}%</span></div>
                    <div class="hf-bar" aria-hidden="true"><div class="hf-bar-fill" style="width: {{ $recap['rate'] }}%;"></div></div>
                    <div class="hf-stat-row" style="color: inherit;"><span>Inbox belum dirapikan</span><span class="hf-serif">{{ $recap['inbox'] }}</span></div>
                    <div class="hf-stat-row" style="color: inherit;"><span>Dipilih untuk besok</span><span class="hf-serif">{{ $recap['tomorrow']->count() }}</span></div>
                </div>
            @endif
        </section>

        @if ($recap['isToday'])
            <livewire:daily-brief-card />
        @endif

        <div style="display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start;">

            {{-- Daftar follow-up --}}
            <div class="hf-card" style="flex: 2 1 420px; min-width: 0; padding: 24px 28px;">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
                    <span class="hf-eyebrow" style="color: var(--primary);">{{ $recap['isToday'] ? 'Follow-up' : 'Selesai pada hari itu' }}</span>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                        @foreach (\App\Support\WorkRecap::FILTERS as $key => $label)
                            @if ($recap['isToday'] || in_array($key, ['all', 'done'], true))
                                <button type="button" wire:click="setFilter('{{ $key }}')" class="hf-filter {{ $filter === $key ? 'active' : '' }}" aria-pressed="{{ $filter === $key ? 'true' : 'false' }}">{{ $label }} {{ $recap['counts'][$key] }}</button>
                            @endif
                        @endforeach
                    </div>
                </div>

                @if ($recap['isToday'])
                    <form wire:submit="add" style="display: flex; gap: 10px; margin-bottom: 8px;">
                        <input type="text" wire:model="newTitle" placeholder="Catat cepat ke Inbox..." aria-label="Catat cepat ke Inbox" style="flex: 1;">
                        <button type="submit" class="hf-btn-tonal">Catat</button>
                    </form>
                    @error('newTitle')
                        <p class="error-text">{{ $message }}</p>
                    @enderror
                @endif

                @forelse ($rows as $item)
                    @php
                        $status = $item->status;
                        $isTomorrow = $item->picked_date?->toDateString() === \Illuminate\Support\Carbon::parse($today)->addDay()->toDateString();
                    @endphp
                    @if ($editingId === $item->id)
                        <div wire:key="row-{{ $item->id }}" style="padding: 12px 0; border-bottom: 1px solid var(--outline-variant);">
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
                                    <label for="f-title">Judul</label>
                                    <input id="f-title" type="text" wire:model="form.title">
                                    @error('form.title') <p class="error-text">{{ $message }}</p> @enderror
                                </div>
                                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px;">
                                    <div class="field">
                                        <label for="f-status">Status</label>
                                        <select id="f-status" wire:model="form.status">
                                            <option value="INBOX">Inbox</option>
                                            <option value="ACTIVE">Aktif</option>
                                            <option value="WAITING">Menunggu</option>
                                            <option value="DONE">Selesai</option>
                                        </select>
                                    </div>
                                    <div class="field">
                                        <label for="f-date">Tanggal</label>
                                        <input id="f-date" type="date" wire:model="form.date">
                                        @error('form.date') <p class="error-text">{{ $message }}</p> @enderror
                                    </div>
                                    <div class="field">
                                        <label for="f-time">Jam</label>
                                        <input id="f-time" type="time" wire:model="form.time">
                                        @error('form.time') <p class="error-text">{{ $message }}</p> @enderror
                                    </div>
                                    <div class="field">
                                        <label for="f-person">Orang</label>
                                        <input id="f-person" type="text" wire:model="form.person">
                                    </div>
                                </div>
                                <div class="field">
                                    <label for="f-note">Catatan</label>
                                    <textarea id="f-note" wire:model="form.note" rows="3"></textarea>
                                </div>
                                <div style="display: flex; gap: 8px;">
                                    <button type="submit" class="primary" style="width: auto;">Simpan</button>
                                    <button type="button" wire:click="cancel" style="background: none; border: none; color: var(--on-surface-variant); cursor: pointer;">Batal</button>
                                </div>
                            </form>
                        </div>
                    @else
                        <div class="hf-row" wire:key="row-{{ $item->id }}">
                            @if ($status === 'DONE')
                                <input type="checkbox" class="hf-check-sm" checked wire:click="reopen({{ $item->id }})" aria-label="Buka lagi {{ $item->title }}">
                            @else
                                <input type="checkbox" class="hf-check-sm" wire:click="markDone({{ $item->id }})" aria-label="Tandai {{ $item->title }} selesai">
                            @endif
                            <div style="flex: 1 1 0; min-width: 0;">
                                <div style="font-size: 14px; {{ $status === 'DONE' ? 'text-decoration: line-through; color: var(--on-surface-variant);' : '' }}">{{ $item->title }}</div>
                                <div class="hf-label">
                                    @if ($item->date)
                                        {{ $item->date->locale('id')->translatedFormat('j M Y') }}@if ($item->time) {{ substr($item->time, 0, 5) }}@endif
                                        @if ($status !== 'DONE' && $status !== 'INBOX' && $item->date->toDateString() < $today)
                                            <span style="color: var(--error); font-weight: 600;">Terlambat</span>
                                        @endif
                                    @endif
                                    @if ($item->person) · {{ $item->person }} @endif
                                    @if (! $item->date && ! $item->person) Tanpa tanggal atau orang @endif
                                </div>
                            </div>
                            @if ($status === 'DONE')
                                <span class="hf-chip">Selesai</span>
                            @elseif ($status === 'WAITING')
                                <span class="hf-chip warn">Menunggu</span>
                            @elseif ($isTomorrow)
                                <span class="hf-chip neutral">Lanjut besok</span>
                            @elseif ($status === 'INBOX')
                                <span class="hf-chip neutral">Inbox</span>
                            @else
                                <span class="hf-chip neutral">Aktif</span>
                            @endif
                            <button type="button" wire:click="edit({{ $item->id }})" class="hf-btn-text">Ubah</button>
                            <button type="button" wire:click="delete({{ $item->id }})" wire:confirm="Hapus follow-up ini?" class="hf-btn-text" style="color: var(--error);">Hapus</button>
                        </div>
                    @endif
                @empty
                    <p style="font-size: 13px; color: var(--on-surface-variant); margin: 12px 0 0;">
                        {{ $recap['isToday'] ? 'Tidak ada follow-up di daftar ini.' : 'Tidak ada follow-up yang selesai pada hari itu.' }}
                    </p>
                @endforelse
            </div>

            {{-- Panel samping --}}
            <div style="flex: 1 1 280px; min-width: 0; display: flex; flex-direction: column; gap: 16px;">

                <div class="hf-card" style="padding: 20px; display: flex; flex-direction: column; gap: 10px;">
                    <span class="hf-eyebrow">Meeting &amp; briefing</span>
                    <textarea wire:model="meetingNote" rows="4" class="hf-textarea" aria-label="Catatan meeting" placeholder="Keputusan dan hal yang masih terbuka dari meeting hari itu.">{{ $meetingNote }}</textarea>
                    <button type="button" wire:click="saveMeetingNote" class="hf-btn-tonal" style="align-self: flex-start;">Simpan catatan meeting</button>
                </div>

                @if ($recap['isToday'])
                    <div class="hf-card" style="padding: 20px; display: flex; flex-direction: column; gap: 10px;">
                        <span class="hf-eyebrow">Rencana besok</span>
                        @forelse ($recap['tomorrow'] as $item)
                            <label wire:key="tmr-{{ $item->id }}" style="display: flex; align-items: flex-start; gap: 10px; font-size: 14px; line-height: 1.4; margin: 0; color: inherit; font-weight: 400;">
                                <input type="checkbox" class="hf-check-sm" checked wire:click="removeFromTomorrow({{ $item->id }})">
                                <span>{{ $item->title }}</span>
                            </label>
                        @empty
                            <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0;">Belum ada yang dipilih. Yang dipilih muncul di daftar daily scrum besok.</p>
                        @endforelse
                        @if ($pickable->isNotEmpty())
                            <form wire:submit="addToTomorrow" style="display: flex; gap: 8px; align-items: center;">
                                <select wire:model="pickTomorrowId" aria-label="Pilih follow-up untuk besok" style="flex: 1; min-width: 0;">
                                    <option value="">Tambah dari daftar…</option>
                                    @foreach ($pickable as $option)
                                        <option value="{{ $option->id }}">{{ $option->status === 'INBOX' ? '[Inbox] ' : '' }}{{ \Illuminate\Support\Str::limit($option->title, 48) }}</option>
                                    @endforeach
                                </select>
                                <button type="submit" class="hf-btn-tonal">Tambah</button>
                            </form>
                        @endif
                    </div>
                @endif

                <div class="hf-card" style="padding: 20px; display: flex; flex-direction: column; gap: 10px;">
                    <span class="hf-eyebrow">Catatan EOD</span>
                    @if ($eodConflict !== null)
                        <div style="background: var(--primary-fixed); color: var(--on-primary-container); border-radius: 12px; padding: 12px 16px; font-size: 14px;">
                            <strong>Catatan ini berubah di tempat lain (mungkin dari HP).</strong>
                            <p style="margin: 6px 0; white-space: pre-line;">{{ $eodConflict === '' ? '(kosong)' : $eodConflict }}</p>
                            <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                                <button type="button" wire:click="overwriteEodNote" class="primary" style="width: auto;">Timpa dengan punyaku</button>
                                <button type="button" wire:click="useLatestEodNote" style="background: none; border: 1px solid var(--outline-variant); color: inherit; padding: 10px 20px; border-radius: 999px; cursor: pointer;">Pakai yang terbaru</button>
                            </div>
                        </div>
                    @endif
                    <textarea wire:model="eodNote" rows="3" class="hf-textarea" aria-label="Catatan EOD" placeholder="Apa yang terjadi hari ini.">{{ $eodNote }}</textarea>
                    @error('eodNote') <p class="error-text">{{ $message }}</p> @enderror
                    <button type="button" wire:click="saveEodNote" class="hf-btn-tonal" style="align-self: flex-start;">Simpan catatan EOD</button>
                </div>

                <div x-data="{ done: false }">
                    <button type="button" class="hf-btn-tonal" style="width: 100%; justify-content: center;"
                            x-on:click="const t = @js($shareText); if (navigator.share) { navigator.share({ text: t }).catch(() => {}) } else { navigator.clipboard.writeText(t).then(() => { done = true; setTimeout(() => done = false, 2500) }) }">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="width: 16px; height: 16px;"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
                        <span x-text="done ? 'Tersalin ke papan klip' : 'Bagikan ringkasan'">Bagikan ringkasan</span>
                    </button>
                </div>
            </div>
        </div>
    </div>
</div>
