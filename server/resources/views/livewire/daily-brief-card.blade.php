<section wire:poll.300s aria-labelledby="ringkasan-claude-judul" style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
    <div style="display: flex; align-items: baseline; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;">
        <h2 id="ringkasan-claude-judul" style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0;">Ringkasan dari Claude</h2>
        <span style="font-size: 12px; color: var(--on-surface-variant);">Ditulis otomatis, hanya dibaca</span>
    </div>

    @if (! $brief)
        <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0;">Belum ada ringkasan. Ringkasan email belum dibaca muncul di sini setelah skill <code>habitflow-morning-routine</code> dijalankan.</p>
    @else
        <p style="font-size: 12px; color: var(--on-surface-variant); margin: 0 0 8px;">
            @if ($isToday)
                Hari ini · diperbarui {{ $updatedAt->format('H.i') }}
            @else
                Belum ada ringkasan hari ini. Terakhir: {{ \Illuminate\Support\Carbon::parse($brief->date)->locale('id')->translatedFormat('l, j F Y') }}
            @endif
        </p>
        <div style="font-size: 14px; line-height: 1.6; white-space: pre-line; overflow-wrap: anywhere;">{{ $brief->email_digest }}</div>
    @endif
</section>
