<section wire:poll.300s aria-labelledby="kesehatan-judul" style="margin-bottom: 16px;">
    <h2 id="kesehatan-judul" class="hf-section-title" style="font-size: 22px; margin: 0 0 12px;">Kesehatan</h2>

    @if (! $hasAnything)
        <div class="hf-card" style="padding: 20px;">
            <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0;">
                Belum ada data kesehatan di server. Berat, tensi, dan langkah dicatat di app Android dan muncul di sini setelah sinkron.
            </p>
        </div>
    @else
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px;">

            {{-- Berat --}}
            <div class="hf-card" style="padding: 20px; grid-column: 1 / -1;">
                <div class="hf-stat-head">
                    <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3v18h18"></path><path d="M7 14l4-4 3 3 5-6"></path></svg>
                    <span>Berat badan</span>
                </div>

                @if (! $weight)
                    <div class="hf-stat-note">Belum ada catatan berat.</div>
                @else
                    <div style="display: flex; flex-wrap: wrap; gap: 8px 24px; align-items: baseline; margin-bottom: 6px;">
                        <div class="hf-serif" style="font-size: 30px;">{{ number_format($weight['latest']['kg'], 1, ',', '.') }} <span style="font-size: 14px;">kg</span></div>
                        <div class="hf-stat-note">
                            {{ $weight['latest']['at']->locale('id')->translatedFormat('l, j F') }}
                            @if ($weight['latest']['ageDays'] > 0) · {{ $weight['latest']['ageDays'] }} hari lalu @else · hari ini @endif
                        </div>
                    </div>
                    <div class="hf-stat-note" style="margin-bottom: 12px;">
                        @if ($weight['bmi'] !== null) BMI {{ number_format($weight['bmi'], 1, ',', '.') }} @endif
                        @if ($weight['targetKg'] !== null)
                            @if ($weight['bmi'] !== null) · @endif
                            Target {{ number_format($weight['targetKg'], 1, ',', '.') }} kg,
                            @if ($weight['remainingKg'] > 0) {{ number_format($weight['remainingKg'], 1, ',', '.') }} kg di atas target
                            @elseif ($weight['remainingKg'] < 0) {{ number_format(abs($weight['remainingKg']), 1, ',', '.') }} kg di bawah target
                            @else sudah di target @endif
                        @endif
                    </div>

                    @if ($weight['chart'])
                        @php
                            $c = $weight['chart'];
                            $w = \App\Support\Health\WeightChart::WIDTH;
                            $h = \App\Support\Health\WeightChart::HEIGHT;
                            $first = $c['points'][0];
                            $lastPoint = $c['points'][count($c['points']) - 1];
                            $summary = 'Grafik berat 6 minggu terakhir, '.count($c['points']).' catatan, terakhir '.number_format($lastPoint['kg'], 1, ',', '.').' kg'
                                .($weight['targetKg'] !== null ? ', garis putus-putus menunjukkan target '.number_format($weight['targetKg'], 1, ',', '.').' kg' : '').'.';
                        @endphp
                        <svg viewBox="0 0 {{ $w }} {{ $h }}" role="img" aria-label="{{ $summary }}" style="width: 100%; height: auto; display: block; max-height: 260px;">
                            <title>{{ $summary }}</title>
                            {{-- Garis bantu dan label sumbu y --}}
                            <line x1="{{ \App\Support\Health\WeightChart::PAD_LEFT }}" x2="{{ $w - \App\Support\Health\WeightChart::PAD_RIGHT }}" y1="{{ $c['yTop'] }}" y2="{{ $c['yTop'] }}" style="stroke: var(--outline-variant);" stroke-width="1"></line>
                            <line x1="{{ \App\Support\Health\WeightChart::PAD_LEFT }}" x2="{{ $w - \App\Support\Health\WeightChart::PAD_RIGHT }}" y1="{{ $c['yBottom'] }}" y2="{{ $c['yBottom'] }}" style="stroke: var(--outline-variant);" stroke-width="1"></line>
                            <text x="{{ \App\Support\Health\WeightChart::PAD_LEFT - 6 }}" y="{{ $c['yTop'] + 4 }}" text-anchor="end" font-size="15" style="fill: var(--on-surface-variant);">{{ number_format($c['maxKg'], 1, ',', '.') }}</text>
                            <text x="{{ \App\Support\Health\WeightChart::PAD_LEFT - 6 }}" y="{{ $c['yBottom'] + 4 }}" text-anchor="end" font-size="15" style="fill: var(--on-surface-variant);">{{ number_format($c['minKg'], 1, ',', '.') }}</text>
                            {{-- Label sumbu x: awal dan akhir jendela --}}
                            <text x="{{ \App\Support\Health\WeightChart::PAD_LEFT }}" y="{{ $h - 6 }}" font-size="15" style="fill: var(--on-surface-variant);">{{ $weight['from']->locale('id')->translatedFormat('j M') }}</text>
                            <text x="{{ $w - \App\Support\Health\WeightChart::PAD_RIGHT }}" y="{{ $h - 6 }}" text-anchor="end" font-size="15" style="fill: var(--on-surface-variant);">{{ $weight['to']->locale('id')->translatedFormat('j M') }}</text>
                            @if ($c['targetY'] !== null)
                                <line x1="{{ \App\Support\Health\WeightChart::PAD_LEFT }}" x2="{{ $w - \App\Support\Health\WeightChart::PAD_RIGHT }}" y1="{{ $c['targetY'] }}" y2="{{ $c['targetY'] }}" stroke-dasharray="5 4" stroke-width="1.5" style="stroke: var(--primary);"></line>
                            @endif
                            @if (count($c['points']) > 1)
                                <polyline points="{{ $c['line'] }}" fill="none" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" style="stroke: var(--primary);"></polyline>
                            @endif
                            @foreach ($c['points'] as $p)
                                <circle cx="{{ $p['x'] }}" cy="{{ $p['y'] }}" r="3.5" style="fill: var(--primary);"></circle>
                            @endforeach
                        </svg>
                    @else
                        <div class="hf-stat-note">Belum ada catatan berat dalam 6 minggu terakhir.</div>
                    @endif
                @endif
            </div>

            {{-- Langkah --}}
            <div class="hf-card" style="padding: 20px;">
                <div class="hf-stat-head">
                    <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 12h-4l-3 9L9 3l-3 9H2"></path></svg>
                    <span>Langkah, 7 hari</span>
                </div>

                @if (collect($steps['days'])->every(fn ($d) => $d['steps'] === null))
                    <div class="hf-stat-note">Belum ada langkah dari HP. Langkah dibaca dari Health Connect dan dikirim saat sinkron.</div>
                @else
                    @php $targetPct = $steps['target'] ? min(100, $steps['target'] / $stepMax * 100) : null; @endphp
                    <div role="img" aria-label="Langkah 7 hari terakhir: {{ collect($steps['days'])->map(fn ($d) => $d['label'].' '.($d['steps'] === null ? 'tidak ada data' : number_format($d['steps'], 0, ',', '.')))->implode(', ') }}{{ $steps['target'] ? '. Target '.number_format($steps['target'], 0, ',', '.').' per hari' : '' }}" style="position: relative; display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; align-items: end; height: 150px; padding-top: 8px;">
                        @if ($targetPct !== null)
                            <div aria-hidden="true" style="position: absolute; left: 0; right: 0; bottom: calc(22px + (100% - 22px - 8px) * {{ $targetPct / 100 }}); border-top: 1.5px dashed var(--primary); pointer-events: none;"></div>
                        @endif
                        @foreach ($steps['days'] as $d)
                            @php $barPct = $d['steps'] === null ? 0 : max(2, $d['steps'] / $stepMax * 100); @endphp
                            <div style="display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; min-width: 0;">
                                {{-- Latar sama dengan kartu supaya garis target tidak menimpa angka. --}}
                                <div style="position: relative; z-index: 1; font-size: 10px; line-height: 1.2; padding: 0 2px; margin-bottom: 2px; color: var(--on-surface-variant); background: var(--surface-lowest);">{{ $d['steps'] === null ? '–' : number_format($d['steps'] / 1000, 1, ',', '.').'k' }}</div>
                                <div style="width: 100%; max-width: 28px; height: calc((100% - 38px) * {{ $barPct / 100 }}); min-height: {{ $d['steps'] === null ? 0 : 3 }}px; border-radius: 6px 6px 2px 2px; background: {{ $d['date'] === $steps['days'][count($steps['days']) - 1]['date'] ? 'var(--primary)' : 'var(--primary-fixed-dim, var(--primary-fixed))' }};"></div>
                                <div style="font-size: 11px; color: var(--on-surface-variant); margin-top: 6px;">{{ $d['label'] }}</div>
                            </div>
                        @endforeach
                    </div>
                    @if ($steps['target'])
                        <div class="hf-stat-note" style="margin-top: 8px;">Garis putus-putus: target {{ number_format($steps['target'], 0, ',', '.') }} langkah per hari.</div>
                    @endif
                @endif
            </div>

            {{-- Tensi --}}
            <div class="hf-card" style="padding: 20px;">
                <div class="hf-stat-head">
                    <svg class="hf-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
                    <span>Tensi</span>
                </div>

                @if ($bps === [])
                    <div class="hf-stat-note">Belum ada catatan tensi.</div>
                @else
                    <div class="hf-serif" style="font-size: 30px; margin-bottom: 4px;">{{ $bps[0]['systolic'] }}/{{ $bps[0]['diastolic'] }} <span style="font-size: 14px;">mmHg</span></div>
                    <div class="hf-stat-note" style="margin-bottom: 12px;">
                        {{ $bps[0]['at']->locale('id')->translatedFormat('l, j F H.i') }}@if ($bps[0]['pulse'] !== null) · nadi {{ $bps[0]['pulse'] }}@endif
                    </div>
                    @if (count($bps) > 1)
                        <div class="hf-stat-rows">
                            @foreach (array_slice($bps, 1) as $b)
                                <div class="hf-stat-row">
                                    <span>{{ $b['at']->locale('id')->translatedFormat('j M H.i') }}</span>
                                    <span class="hf-serif">{{ $b['systolic'] }}/{{ $b['diastolic'] }}</span>
                                </div>
                            @endforeach
                        </div>
                    @endif
                @endif
            </div>
        </div>

        <p style="font-size: 12px; color: var(--on-surface-variant); margin: 12px 0 0;">
            Hanya baca. Dicatat di app Android.
            @if ($lastSync) Terakhir dikirim {{ $lastSync->locale('id')->translatedFormat('j F Y H.i') }}. @endif
            Zona waktu {{ $timezone }}. Kategori dan saran kesehatan hanya ada di app.
        </p>
    @endif
</section>
