<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Masuk — {{ config('app.name') }}</title>
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f7fafc; }
        .card { background: #fff; padding: 2.5rem; border-radius: 0.75rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); text-align: center; }
        h1 { margin: 0 0 1.5rem; font-size: 1.25rem; }
        a.google { display: inline-flex; align-items: center; gap: 0.5rem; background: #1a202c; color: #fff; text-decoration: none; padding: 0.75rem 1.5rem; border-radius: 0.5rem; font-size: 0.9375rem; }
    </style>
</head>
<body>
    <div class="card">
        <h1>{{ config('app.name') }}</h1>
        <a class="google" href="{{ route('login.google') }}">Masuk dengan Google</a>
    </div>
</body>
</html>
