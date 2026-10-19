<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    @include('partials.theme-init')
    <title>{{ $title ?? config('app.name') }}</title>
    <link rel="icon" type="image/png" href="{{ asset('images/logo.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Libre+Caslon+Text:wght@400;700&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet">
    @livewireStyles
    <style>
        @include('partials.theme-styles')
        * { box-sizing: border-box; }
        [x-cloak] { display: none !important; }
        body { font-family: 'Manrope', system-ui, sans-serif; margin: 0; background: var(--background); color: var(--on-surface); }
        header { background: var(--surface-lowest); border-bottom: 1px solid var(--outline-variant); padding: 14px 24px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
        header .brand { display: flex; align-items: center; gap: 10px; font-family: 'Libre Caslon Text', serif; font-size: 18px; }
        header .brand img { width: 28px; height: 28px; border-radius: 8px; }
        nav { display: flex; gap: 16px; }
        nav a { color: var(--on-surface-variant); text-decoration: none; font-size: 14px; font-weight: 600; }
        nav a.active { color: var(--primary); }
        header .user { display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem; }
        header img.avatar { width: 28px; height: 28px; border-radius: 9999px; }
        main { max-width: {{ $width ?? '56rem' }}; margin: 2rem auto; padding: 0 1.5rem; }
        form { display: inline; }
        button[type="submit"]:not(.primary) { background: none; border: none; color: var(--on-surface-variant); cursor: pointer; font-size: 0.875rem; font-family: 'Manrope', sans-serif; }
        h1 { font-family: 'Libre Caslon Text', serif; font-weight: 400; }
        .field { margin-bottom: 18px; }
        label { display: block; font-size: 13px; font-weight: 600; color: var(--on-surface-variant); margin-bottom: 6px; }
        input[type="text"], input[type="email"], input[type="password"], input[type="date"], input[type="time"], select, textarea {
            width: 100%; padding: 11px 14px; border: 1px solid var(--outline-variant); border-radius: 12px;
            background: var(--surface-container-low); color: var(--on-surface); font-family: 'Manrope', sans-serif; font-size: 15px;
        }
        button.primary {
            background: var(--primary); color: var(--on-primary); border: none; padding: 11px 22px;
            border-radius: 999px; font-family: 'Manrope', sans-serif; font-weight: 600; font-size: 14px; cursor: pointer;
        }
        /* Gaya kartu dari mockup Design canvas (tahap 33). Halaman lain dipindah ke sini bertahap. */
        .hf-serif { font-family: 'Libre Caslon Text', serif; font-weight: 400; }
        .hf-card { background: var(--surface-lowest); border: 1px solid var(--outline-variant); border-radius: 16px; }
        .hf-icon { width: 20px; height: 20px; color: var(--primary); flex-shrink: 0; }
        .hf-bar { height: 6px; border-radius: 999px; background: var(--outline-variant); overflow: hidden; }
        .hf-bar-fill { height: 100%; background: var(--primary); border-radius: 999px; }
        .hf-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 16px; }
        .hf-stat { display: block; padding: 20px; min-width: 0; }
        a.hf-stat { color: inherit; text-decoration: none; }
        a.hf-stat:hover { border-color: var(--primary); }
        a.hf-stat:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
        .hf-stat-head { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; font-size: 13px; font-weight: 600; color: var(--on-surface-variant); }
        .hf-stat-value { font-size: 30px; margin-bottom: 10px; }
        .hf-stat-note { font-size: 13px; color: var(--on-surface-variant); }
        .hf-stat-rows { display: flex; flex-direction: column; gap: 8px; }
        .hf-stat-row { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; font-size: 13px; color: var(--on-surface-variant); }
        .hf-stat-row .hf-serif { font-size: 20px; color: inherit; }
        .hf-heat { display: grid; grid-auto-flow: column; grid-template-rows: repeat(7, auto); gap: 2px; }
        .hf-heat > i { display: block; aspect-ratio: 1; border-radius: 2px; }
        .hf-heat-mini { max-width: 240px; }
        .hf-heat-none { background: transparent; }
        .hf-heat-0 { background: var(--heat-0); } .hf-heat-1 { background: var(--heat-1); } .hf-heat-2 { background: var(--heat-2); }
        .hf-heat-3 { background: var(--heat-3); } .hf-heat-4 { background: var(--heat-4); }
        .hf-legend { display: flex; align-items: center; justify-content: flex-end; gap: 4px; font-size: 12px; color: var(--on-surface-variant); }
        .hf-legend i { width: 12px; height: 12px; border-radius: 2px; display: block; }
        .hf-section-title { font-family: 'Libre Caslon Text', serif; font-weight: 400; margin: 0; }
        .hf-chip { display: inline-flex; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; background: var(--primary-fixed); color: var(--on-primary-container); }
        .hf-chip.neutral { background: var(--surface-container-low); border: 1px solid var(--outline-variant); color: var(--on-surface-variant); }
        .hf-chip.warn { background: var(--warn-fixed); color: var(--on-warn-fixed); }
        .hf-chip.on { background: var(--surface-lowest); color: var(--on-primary-container); padding: 4px 10px; font-size: 12px; }
        .hf-focus { background: var(--primary-fixed); color: var(--on-primary-container); border-radius: 20px; }
        .hf-focus .hf-bar { background: var(--surface-lowest); }
        .hf-focus .hf-stat-row .hf-serif { font-size: 18px; }
        .hf-eyebrow { font-size: 12px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: var(--on-surface-variant); }
        .hf-label { font-size: 12px; color: var(--on-surface-variant); }
        .hf-row { display: flex; align-items: flex-start; gap: 14px; padding: 13px 0; border-bottom: 1px solid var(--outline-variant); }
        .hf-row:last-child { border-bottom: none; }
        .hf-check-sm { width: 18px; height: 18px; accent-color: var(--primary); cursor: pointer; flex-shrink: 0; margin-top: 2px; }
        .hf-filter { background: none; border: 1px solid var(--outline-variant); border-radius: 999px; padding: 6px 14px; font-family: 'Manrope', system-ui, sans-serif; font-size: 13px; font-weight: 600; color: var(--on-surface-variant); cursor: pointer; }
        .hf-filter.active { background: var(--primary); border-color: var(--primary); color: var(--on-primary); }
        .hf-btn-tonal { display: inline-flex; align-items: center; gap: 8px; background: var(--primary-fixed); color: var(--on-primary-container); border: none; border-radius: 999px; padding: 10px 18px; font-family: 'Manrope', system-ui, sans-serif; font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; }
        button[type="submit"].hf-btn-tonal { background: var(--primary-fixed); color: var(--on-primary-container); font-size: 14px; }
        .hf-btn-text { background: none; border: none; padding: 0 2px; color: var(--primary); font-family: 'Manrope', system-ui, sans-serif; font-size: 13px; font-weight: 600; cursor: pointer; }
        .hf-textarea { width: 100%; resize: vertical; line-height: 1.55; font-size: 13px; }
        .hf-date-btn { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; width: 100%; padding: 10px 12px; border: none; border-radius: 12px; background: none; color: var(--on-surface); text-align: left; cursor: pointer; font-family: 'Manrope', system-ui, sans-serif; }
        .hf-date-btn:hover { background: var(--surface-container-low); }
        .hf-date-btn.active { background: var(--primary-fixed); color: var(--on-primary-container); }
        .hf-date-btn .d { font-size: 14px; font-weight: 600; }
        .hf-date-btn .m { font-size: 12px; opacity: 0.8; }
        .error-text { color: var(--error); font-size: 13px; margin-top: 6px; }
        .notice { background: var(--primary-fixed); color: var(--on-primary-container); padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.5; margin-bottom: 16px; }
        .notice.error { background: transparent; color: var(--error); border: 1px solid var(--error); }
        .prose { line-height: 1.7; font-size: 15px; }
        .prose h2 { font-family: 'Libre Caslon Text', serif; font-weight: 400; font-size: 20px; margin: 32px 0 8px; }
        .prose ul { padding-left: 22px; }
        .prose li { margin-bottom: 6px; }
        .prose a, footer a { color: var(--primary); }
        .prose table { border-collapse: collapse; width: 100%; font-size: 14px; margin: 12px 0; }
        .prose th, .prose td { border: 1px solid var(--outline-variant); padding: 8px 10px; text-align: left; vertical-align: top; }
        .prose th { background: var(--surface-container-low); }
        footer { max-width: 56rem; margin: 0 auto; padding: 24px 1.5rem 40px; font-size: 13px; color: var(--on-surface-variant); }
    </style>
</head>
<body>
    @auth
    <header>
        <div class="brand">
            <img src="{{ asset('images/logo.png') }}" alt="">
            {{ config('app.name') }}
        </div>
        <nav>
            <a href="{{ route('dashboard') }}" class="{{ request()->routeIs('dashboard') ? 'active' : '' }}">Beranda</a>
            <a href="{{ route('habit') }}" class="{{ request()->routeIs('habit') ? 'active' : '' }}">Habit</a>
            <a href="{{ route('follow-ups') }}" class="{{ request()->routeIs('follow-ups') ? 'active' : '' }}">Follow-up</a>
            <a href="{{ route('teams') }}" class="{{ request()->routeIs('teams') ? 'active' : '' }}">Tugas Rumah</a>
        </nav>
        <div class="user">
            @include('partials.theme-toggle')
            @if (auth()->user()->avatar)
                <img class="avatar" src="{{ auth()->user()->avatar }}" alt="">
            @endif
            <a href="{{ route('account') }}" style="color: inherit; text-decoration: none; font-weight: 600;">{{ auth()->user()->name }}</a>
            <form method="POST" action="{{ route('logout') }}">
                @csrf
                <button type="submit">Keluar</button>
            </form>
        </div>
    </header>
    @endauth
    <main>
        @if (session('status'))
            <p class="notice" role="status">{{ session('status') }}</p>
        @endif
        {{ $slot }}
    </main>
    @auth
        <x-confirm-modal />
    @endauth
    <footer>
        <a href="{{ route('privacy') }}">Kebijakan Privasi</a> · <a href="{{ route('delete-account') }}">Hapus akun</a>
    </footer>
    @livewireScripts
</body>
</html>
