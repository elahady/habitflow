<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{{ $title ?? config('app.name') }}</title>
    @livewireStyles
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; margin: 0; background: #f7fafc; color: #1a202c; }
        header { background: #fff; border-bottom: 1px solid #e2e8f0; padding: 1rem 1.5rem; display: flex; justify-content: space-between; align-items: center; }
        header .brand { font-weight: 600; font-size: 1.125rem; }
        header .user { display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem; }
        header img { width: 28px; height: 28px; border-radius: 9999px; }
        main { max-width: 56rem; margin: 2rem auto; padding: 0 1.5rem; }
        form { display: inline; }
        button { background: none; border: none; color: #718096; cursor: pointer; font-size: 0.875rem; }
    </style>
</head>
<body>
    @auth
    <header>
        <div class="brand">{{ config('app.name') }}</div>
        <div class="user">
            @if (auth()->user()->avatar)
                <img src="{{ auth()->user()->avatar }}" alt="">
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
