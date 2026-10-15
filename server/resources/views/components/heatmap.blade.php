{{-- Grid heatmap kolom per minggu (Senin di atas). $levels: tanggal Y-m-d => level (-1 tidak digambar). --}}
@props(['levels', 'start', 'weeks', 'today', 'label', 'mini' => false, 'binary' => false])
@php
    $cells = [];
    for ($col = 0; $col < $weeks; $col++) {
        for ($row = 0; $row < 7; $row++) {
            $date = $start->copy()->addDays($col * 7 + $row);
            $key = $date->toDateString();
            $level = $key > $today ? -1 : ($levels[$key] ?? -1);
            $cells[] = ['key' => $key, 'level' => $level, 'date' => $date];
        }
    }
@endphp
<div @unless ($mini) x-data x-init="$nextTick(() => $el.scrollLeft = $el.scrollWidth)" @endunless
     style="{{ $mini ? '' : 'overflow-x: auto; padding-bottom: 4px;' }}">
    <div role="img" aria-label="{{ $label }}" class="hf-heat {{ $mini ? 'hf-heat-mini' : '' }}"
         style="grid-template-columns: repeat({{ $weeks }}, minmax({{ $mini ? '0' : '11px' }}, 1fr)); {{ $mini ? '' : 'min-width: '.($weeks * 13).'px;' }}">
        @foreach ($cells as $cell)
            @if ($cell['level'] < 0)
                <i class="hf-heat-none"></i>
            @else
                <i class="hf-heat-{{ $cell['level'] }}" title="{{ $cell['date']->locale('id')->translatedFormat('D, j M Y') }}{{ $binary ? ($cell['level'] > 0 ? ' · selesai' : ' · belum') : ' · level '.$cell['level'] }}"></i>
            @endif
        @endforeach
    </div>
</div>
