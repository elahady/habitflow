<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{{ $title ?? config('app.name') }}</title>
    <link rel="icon" type="image/png" href="{{ asset('images/logo.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Libre+Caslon+Text:wght@400;700&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet">
    @livewireStyles
    <style>
        :root {
            --primary: #4D6359;
            --on-primary: #FFFFFF;
            --primary-fixed: #CFE8DB;
            --on-primary-container: #273C33;
            --background: #FAF9F7;
            --surface-lowest: #FFFFFF;
            --surface-container-low: #F5F3F1;
            --on-surface: #1B1C1B;
            --on-surface-variant: #424845;
            --outline-variant: #C2C8C3;
            --error: #BA1A1A;
        }
        @media (prefers-color-scheme: dark) {
            :root {
                --primary: #B3CCBF;
                --on-primary: #1F352B;
                --primary-fixed: #354B42;
                --on-primary-container: #CFE8DB;
                --background: #121412;
                --surface-lowest: #0D0F0E;
                --surface-container-low: #1A1C1A;
                --on-surface: #E3E3E0;
                --on-surface-variant: #C2C8C3;
                --outline-variant: #424845;
                --error: #FFB4AB;
            }
        }
        * { box-sizing: border-box; }
        body { font-family: 'Manrope', system-ui, sans-serif; margin: 0; background: var(--background); color: var(--on-surface); }
        header { background: var(--surface-lowest); border-bottom: 1px solid var(--outline-variant); padding: 14px 24px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
        header .brand { display: flex; align-items: center; gap: 10px; font-family: 'Libre Caslon Text', serif; font-size: 18px; }
        header .brand img { width: 28px; height: 28px; border-radius: 8px; }
        nav { display: flex; gap: 16px; }
        nav a { color: var(--on-surface-variant); text-decoration: none; font-size: 14px; font-weight: 600; }
        nav a.active { color: var(--primary); }
        header .user { display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem; }
        header img.avatar { width: 28px; height: 28px; border-radius: 9999px; }
        main { max-width: 56rem; margin: 2rem auto; padding: 0 1.5rem; }
        form { display: inline; }
        button[type="submit"]:not(.primary) { background: none; border: none; color: var(--on-surface-variant); cursor: pointer; font-size: 0.875rem; font-family: 'Manrope', sans-serif; }
        h1 { font-family: 'Libre Caslon Text', serif; font-weight: 400; }
        .field { margin-bottom: 18px; }
        label { display: block; font-size: 13px; font-weight: 600; color: var(--on-surface-variant); margin-bottom: 6px; }
        input[type="text"], input[type="email"], input[type="password"] {
            width: 100%; padding: 11px 14px; border: 1px solid var(--outline-variant); border-radius: 12px;
            background: var(--surface-container-low); color: var(--on-surface); font-family: 'Manrope', sans-serif; font-size: 15px;
        }
        button.primary {
            background: var(--primary); color: var(--on-primary); border: none; padding: 11px 22px;
            border-radius: 999px; font-family: 'Manrope', sans-serif; font-weight: 600; font-size: 14px; cursor: pointer;
        }
        .error-text { color: var(--error); font-size: 13px; margin-top: 6px; }
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
            <a href="{{ route('teams') }}" class="{{ request()->routeIs('teams') ? 'active' : '' }}">Tugas Rumah</a>
        </nav>
        <div class="user">
            @if (auth()->user()->avatar)
                <img class="avatar" src="{{ auth()->user()->avatar }}" alt="">
            @endif
            <span>{{ auth()->user()->name }}</span>
            <form method="POST" action="{{ route('logout') }}">
                @csrf
                <button type="submit">Keluar</button>
            </form>
        </div>
    </header>
    @endauth
    <main>
        {{ $slot }}
    </main>
    @livewireScripts
</body>
</html>
