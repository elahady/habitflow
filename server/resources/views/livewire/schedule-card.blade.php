<div wire:poll.60s style="border: 1px solid var(--outline-variant); border-radius: 16px; padding: 16px; margin-bottom: 16px;">
    <h2 style="font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 18px; margin: 0 0 12px;">Jadwal sekarang</h2>

    @if (! $hasBlocks)
        <p style="font-size: 13px; color: var(--on-surface-variant); margin: 0;">Belum ada jadwal di server. Jadwal diatur di app Android dan akan muncul di sini setelah sinkron.</p>
    @else
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px;">
            <div style="background: var(--primary-fixed); color: var(--on-primary-container); border-radius: 12px; padding: 14px 16px;">
                <div style="font-size: 12px; font-weight: 600; opacity: 0.8;">Sekarang</div>
                @if ($now)
                    <div style="font-size: 18px; font-weight: 700; margin: 2px 0;">{{ $now->block->name }}</div>
                    <div style="font-size: 13px;">
                        {{ \App\Support\Schedule\ScheduleResolver::formatMinute($now->startMinute) }}
                        – {{ \App\Support\Schedule\ScheduleResolver::formatMinute($now->endMinute) }}
                        · sisa {{ $now->endMinute - $nowMinute }} menit
                    </div>
                @else
                    <div style="font-size: 15px; margin: 4px 0;">Tidak ada blok yang berjalan.</div>
                @endif
            </div>

            <div style="border: 1px solid var(--outline-variant); border-radius: 12px; padding: 14px 16px;">
                <div style="font-size: 12px; font-weight: 600; color: var(--on-surface-variant);">Berikutnya</div>
                @if ($next)
                    <div style="font-size: 18px; font-weight: 700; margin: 2px 0;">{{ $next->block->name }}</div>
                    <div style="font-size: 13px; color: var(--on-surface-variant);">
                        {{ \App\Support\Schedule\ScheduleResolver::formatMinute($next->startMinute) }}
                        · {{ $next->startMinute - $nowMinute }} menit lagi
                    </div>
                @else
                    <div style="font-size: 15px; margin: 4px 0; color: var(--on-surface-variant);">Tidak ada lagi hari ini.</div>
                @endif
            </div>
        </div>

        @if ($dayOff)
            <p style="font-size: 12px; color: var(--on-surface-variant); margin: 12px 0 0;">Hari ini libur, jadi blok hari kerja disembunyikan.</p>
        @endif

        @if ($missingPrayer)
            <p style="font-size: 12px; color: var(--on-surface-variant); margin: 12px 0 0;">
                Waktu sholat hari ini belum dikirim dari HP, jadi blok yang berpatokan sholat belum tampil. Buka app Android dan sinkron.
            </p>
        @elseif ($resolvedCount === 0)
            <p style="font-size: 12px; color: var(--on-surface-variant); margin: 12px 0 0;">Tidak ada blok aktif untuk hari ini.</p>
        @endif

        <p style="font-size: 12px; color: var(--on-surface-variant); margin: 8px 0 0;">
            Hanya baca. Zona waktu {{ $timezone }}@if ($lastSync), waktu sholat terakhir disinkron {{ $lastSync->format('d M Y H:i') }}@endif.
        </p>
    @endif
</div>
