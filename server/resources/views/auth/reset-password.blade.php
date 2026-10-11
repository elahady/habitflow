<x-auth-card title="Kata sandi baru">
    <x-slot:tagline>Masukkan kode 6 angka dari email, lalu kata sandi barumu. Setelah diganti, semua perangkat perlu masuk lagi.</x-slot:tagline>
    <form method="POST" action="{{ route('password.update') }}">
        @csrf
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ $email }}" required autocomplete="email">
            @error('email')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="code">Kode</label>
            <input type="text" id="code" name="code" class="code" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="one-time-code" required autofocus>
            @error('code')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password">Kata sandi baru</label>
            <input type="password" id="password" name="password" required autocomplete="new-password">
            <p class="hint">Minimal 8 karakter, dan bukan kata sandi yang umum dipakai.</p>
            @error('password')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password_confirmation">Ulangi kata sandi baru</label>
            <input type="password" id="password_confirmation" name="password_confirmation" required autocomplete="new-password">
        </div>
        <button type="submit" class="primary">Ganti kata sandi</button>
    </form>
    <p class="switch"><a href="{{ route('password.forgot') }}">Minta kode baru</a></p>
</x-auth-card>
