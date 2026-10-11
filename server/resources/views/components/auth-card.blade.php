<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    @include('partials.theme-init')
    <title>{{ $title }} — {{ config('app.name') }}</title>
    <link rel="icon" type="image/png" href="{{ asset('images/logo.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Libre+Caslon+Text:wght@400;700&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        @include('partials.theme-styles')
        * { box-sizing: border-box; }
        .theme-toggle.corner { position: fixed; top: 16px; right: 16px; background: var(--surface-lowest); }
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
            max-width: 380px;
        }
        .brand { text-align: center; margin-bottom: 28px; }
        .brand img {
            width: 56px;
            height: 56px;
            border-radius: 14px;
            margin-bottom: 12px;
        }
        .brand h1 {
            font-family: 'Libre Caslon Text', serif;
            font-weight: 400;
            font-size: 22px;
            margin: 0 0 6px;
            color: var(--on-surface);
        }
        .brand p {
            font-size: 13.5px;
            line-height: 1.5;
            color: var(--on-surface-variant);
            margin: 0;
        }
        label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: var(--on-surface-variant);
            margin-bottom: 6px;
        }
        .field { margin-bottom: 18px; }
        input[type="text"], input[type="email"], input[type="password"] {
            width: 100%;
            padding: 11px 14px;
            border: 1px solid var(--outline-variant);
            border-radius: 12px;
            background: var(--surface-container-low);
            color: var(--on-surface);
            font-family: 'Manrope', sans-serif;
            font-size: 15px;
        }
        input:focus { outline: 2px solid var(--primary); outline-offset: 1px; }
        .error-text {
            color: var(--error);
            font-size: 13px;
            margin-top: 6px;
        }
        button.primary {
            width: 100%;
            background: var(--primary);
            color: var(--on-primary);
            border: none;
            padding: 13px 24px;
            border-radius: 999px;
            font-family: 'Manrope', sans-serif;
            font-weight: 600;
            font-size: 15px;
            cursor: pointer;
            transition: opacity 0.15s ease;
        }
        button.primary:hover { opacity: 0.9; }
        a.google {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            background: var(--surface-container-low);
            border: 1px solid var(--outline-variant);
            color: var(--on-surface);
            text-decoration: none;
            padding: 12px 24px;
            border-radius: 999px;
            font-family: 'Manrope', sans-serif;
            font-weight: 600;
            font-size: 15px;
            transition: opacity 0.15s ease;
        }
        a.google:hover { opacity: 0.85; }
        .divider {
            display: flex;
            align-items: center;
            gap: 12px;
            margin: 24px 0;
            color: var(--on-surface-variant);
            font-size: 13px;
        }
        .divider::before, .divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: var(--outline-variant);
        }
        .switch {
            text-align: center;
            margin-top: 24px;
            font-size: 14px;
            color: var(--on-surface-variant);
        }
        .switch a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .switch a:hover { text-decoration: underline; }
    </style>
</head>
<body>
    @include('partials.theme-toggle', ['class' => 'corner'])
    <div class="card">
        <div class="brand">
            <img src="{{ asset('images/logo.png') }}" alt="{{ config('app.name') }}">
            <h1>{{ $title }}</h1>
            @isset($tagline)
                <p>{{ $tagline }}</p>
            @endisset
        </div>
        {{ $slot }}
    </div>
</body>
</html>
