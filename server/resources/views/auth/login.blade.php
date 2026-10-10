<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Masuk — {{ config('app.name') }}</title>
    <link rel="icon" type="image/png" href="{{ asset('images/logo.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Libre+Caslon+Text:wght@400;700&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --primary: #4D6359;
            --on-primary: #FFFFFF;
            --primary-fixed: #CFE8DB;
            --on-primary-container: #273C33;
            --background: #FAF9F7;
            --surface-lowest: #FFFFFF;
            --on-surface: #1B1C1B;
            --on-surface-variant: #424845;
            --outline-variant: #C2C8C3;
        }
        @media (prefers-color-scheme: dark) {
            :root {
                --primary: #B3CCBF;
                --on-primary: #1F352B;
                --primary-fixed: #354B42;
                --on-primary-container: #CFE8DB;
                --background: #121412;
                --surface-lowest: #0D0F0E;
                --on-surface: #E3E3E0;
                --on-surface-variant: #C2C8C3;
                --outline-variant: #424845;
            }
        }
        * { box-sizing: border-box; }
        body {
            font-family: 'Manrope', system-ui, sans-serif;
            margin: 0;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--background);
            color: var(--on-surface);
            padding: 16px;
        }
        .card {
            background: var(--surface-lowest);
            border: 1px solid var(--outline-variant);
            border-radius: 20px;
            padding: 40px 32px;
            width: 100%;
            max-width: 360px;
            text-align: center;
        }
        .logo {
            width: 72px;
            height: 72px;
            border-radius: 16px;
            margin-bottom: 20px;
        }
        h1 {
            font-family: 'Libre Caslon Text', serif;
            font-weight: 400;
            font-size: 24px;
            margin: 0 0 4px;
            color: var(--on-surface);
        }
        p.tagline {
            font-size: 14px;
            color: var(--on-surface-variant);
            margin: 0 0 32px;
        }
        a.google {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            background: var(--primary);
            color: var(--on-primary);
            text-decoration: none;
            padding: 13px 24px;
            border-radius: 999px;
            font-family: 'Manrope', sans-serif;
            font-weight: 600;
            font-size: 15px;
            transition: opacity 0.15s ease;
        }
        a.google:hover { opacity: 0.9; }
        a.google svg { flex-shrink: 0; }
    </style>
</head>
<body>
    <div class="card">
        <img class="logo" src="{{ asset('images/logo.png') }}" alt="{{ config('app.name') }}">
        <h1>{{ config('app.name') }}</h1>
        <p class="tagline">Habit, to-do, dan jadwal harianmu</p>
        <a class="google" href="{{ route('login.google') }}">
            <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
                <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z"/>
                <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z"/>
                <path fill="#FBBC05" d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z"/>
                <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z"/>
            </svg>
            Masuk dengan Google
        </a>
    </div>
</body>
</html>
