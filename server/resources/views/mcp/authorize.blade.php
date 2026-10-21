<x-auth-card title="Izinkan akses">
    <x-slot:tagline>Aplikasi di bawah meminta izin memakai akun Habitflow kamu lewat Claude.</x-slot:tagline>

    <div class="notice">
        <strong>{{ $client->name }}</strong> ingin membaca dan menulis data Habitflow atas nama
        <strong>{{ $user->email }}</strong>: ringkasan harian, dan fitur lain yang nanti ditambahkan ke koneksi Claude.
    </div>

    @php($hosts = collect($client->redirect_uris)->map(fn ($uri) => parse_url($uri, PHP_URL_HOST) ?: parse_url($uri, PHP_URL_SCHEME))->unique()->implode(', '))
    <p class="switch" style="margin-top:0">Setelah kamu setuju, kamu dikembalikan ke: <strong>{{ $hosts }}</strong>.
        Setujui hanya kalau kamu sendiri yang baru saja menambahkan Habitflow di Claude.</p>

    <form method="POST" action="{{ route('passport.authorizations.approve') }}">
        @csrf
        <input type="hidden" name="state" value="{{ $request->state }}">
        <input type="hidden" name="client_id" value="{{ $client->getKey() }}">
        <input type="hidden" name="auth_token" value="{{ $authToken }}">
        <button type="submit" class="primary">Izinkan</button>
    </form>

    <form method="POST" action="{{ route('passport.authorizations.deny') }}" style="margin-top:12px">
        @csrf
        @method('DELETE')
        <input type="hidden" name="state" value="{{ $request->state }}">
        <input type="hidden" name="client_id" value="{{ $client->getKey() }}">
        <input type="hidden" name="auth_token" value="{{ $authToken }}">
        <button type="submit" class="primary" style="background:var(--surface-container-low);color:var(--on-surface);border:1px solid var(--outline-variant)">Tolak</button>
    </form>
</x-auth-card>
