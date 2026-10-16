<section wire:poll.300s class="hf-focus" aria-labelledby="fokus-judul" style="padding: 28px 32px; margin-bottom: 16px; display: flex; flex-direction: column; gap: 14px;">
    <div class="hf-eyebrow" style="color: inherit;">Fokus hari ini</div>

    @if (! $focus)
        <h2 id="fokus-judul" class="hf-serif" style="font-size: 26px; line-height: 1.2; margin: 0;">Fokus belum ada</h2>
        <p style="font-size: 15px; line-height: 1.65; margin: 0; max-width: 56ch;">
            Fokus kesehatan dihitung di app Android dari berat, tensi, dan langkahmu, lalu muncul di sini setelah sinkron. Buka layar Hari ini di app untuk menghitungnya.
        </p>
    @else
        <h2 id="fokus-judul" class="hf-serif" style="font-size: 30px; line-height: 1.2; margin: 0; text-wrap: balance;">{{ $focus->title }}</h2>
        <p style="font-size: 15px; line-height: 1.65; margin: 0; max-width: 56ch;">{{ $focus->sentence }}</p>

        @if ($steps['today'] !== null && $steps['target'])
            @php $pct = min(100, (int) round($steps['today'] / $steps['target'] * 100)); @endphp
            <div style="max-width: 420px;">
                <div style="display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px;">
                    <span>Langkah hari ini</span>
                    <span class="hf-serif" style="font-size: 16px;">{{ number_format($steps['today'], 0, ',', '.') }} / {{ number_format($steps['target'], 0, ',', '.') }}</span>
                </div>
                <div class="hf-bar" role="img" aria-label="Langkah hari ini {{ number_format($steps['today'], 0, ',', '.') }} dari {{ number_format($steps['target'], 0, ',', '.') }}"><div class="hf-bar-fill" style="width: {{ $pct }}%;"></div></div>
            </div>
        @endif

        <details>
            <summary style="cursor: pointer; font-size: 13px; font-weight: 700; text-decoration: underline; min-height: 32px; display: inline-flex; align-items: center;">Kenapa ini?</summary>
            <div style="margin-top: 10px; border-top: 1px solid currentColor; padding-top: 12px; max-width: 56ch;">
                @if (! empty($focus->reasons))
                    <div class="hf-stat-rows" style="margin-bottom: 10px;">
                        @foreach ($focus->reasons as $reason)
                            <div class="hf-stat-row" style="color: inherit;"><span>{{ $reason['label'] }}</span><span class="hf-serif">{{ $reason['value'] }}</span></div>
                        @endforeach
                    </div>
                @endif
                @if ($focus->note)
                    <p style="font-size: 13px; line-height: 1.6; margin: 0;">{{ $focus->note }}</p>
                @endif
            </div>
        </details>

        <p style="font-size: 12px; margin: 0; opacity: 0.8;">
            Dihitung di HP{{ $computedAt ? ' '.$computedAt->locale('id')->translatedFormat('l, j F H.i') : '' }}. Hanya baca.
            @if ($stale)
                Sudah lebih dari sehari: buka app Android lalu sinkron untuk menghitung ulang fokus hari ini.
            @endif
        </p>
    @endif
</section>
