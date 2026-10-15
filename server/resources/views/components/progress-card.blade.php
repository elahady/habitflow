@props(['title', 'done', 'total', 'empty', 'href' => null])
@php
    $pct = $total > 0 ? round($done / $total * 100) : 0;
    $tag = $href ? 'a' : 'div';
@endphp
<{{ $tag }} @if ($href) href="{{ $href }}" @endif class="hf-card hf-stat" aria-label="{{ $title }}: {{ $done }} dari {{ $total }}">
    <div class="hf-stat-head">
        {{ $icon }}
        <span>{{ $title }}</span>
    </div>
    @if ($total > 0)
        <div class="hf-serif hf-stat-value">{{ $done }} / {{ $total }}</div>
        <div class="hf-bar" aria-hidden="true"><div class="hf-bar-fill" style="width: {{ $pct }}%;"></div></div>
    @else
        <div class="hf-stat-note">{{ $empty }}</div>
    @endif
</{{ $tag }}>
