<x-auth-card title="Masuk">
    <x-slot:tagline>Asisten harian yang tahu sedang di blok apa sekarang dan apa berikutnya — dari bangun tidur sampai tidur lagi.</x-slot:tagline>
    <form method="POST" action="{{ route('login') }}">
        @csrf
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ old('email') }}" required autofocus autocomplete="email">
        </div>
        <div class="field">
            <label for="password">Kata sandi</label>
            <input type="password" id="password" name="password" required autocomplete="current-password">
        </div>
        @error('email')
            <p class="error-text">{{ $message }}</p>
        @enderror
        <p class="link-row"><a href="{{ route('password.forgot') }}">Lupa kata sandi?</a></p>
        <button type="submit" class="primary">Masuk</button>
    </form>
    <div class="divider">atau</div>
    <x-google-button>Masuk dengan Google</x-google-button>
    <p class="switch">Belum punya akun? <a href="{{ route('register') }}">Daftar</a></p>
</x-auth-card>
