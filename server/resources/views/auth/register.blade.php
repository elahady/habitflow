<x-auth-card title="Daftar">
    <x-slot:tagline>Catat habit, to-do, dan jadwal harianmu dalam 2 detik — tanpa pikir panjang soal kategori atau tanggal dulu.</x-slot:tagline>
    <form method="POST" action="{{ route('register') }}">
        @csrf
        <div class="field">
            <label for="name">Nama</label>
            <input type="text" id="name" name="name" value="{{ old('name') }}" required autofocus autocomplete="name">
            @error('name')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ old('email') }}" required autocomplete="email">
            @error('email')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password">Kata sandi</label>
            <input type="password" id="password" name="password" required autocomplete="new-password">
            <p class="hint">Minimal 8 karakter, dan bukan kata sandi yang umum dipakai.</p>
            @error('password')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password_confirmation">Ulangi kata sandi</label>
            <input type="password" id="password_confirmation" name="password_confirmation" required autocomplete="new-password">
        </div>
        <div class="check">
            <input type="checkbox" id="privacy" name="privacy" value="1" @checked(old('privacy')) required>
            <label for="privacy" style="margin: 0; font-weight: 400;">
                Saya menyetujui <a href="{{ route('privacy') }}" target="_blank" rel="noopener">Kebijakan Privasi</a>,
                termasuk penyimpanan data kesehatan yang saya masukkan (berat badan, tekanan darah, langkah) di server Habitflow.
            </label>
        </div>
        @error('privacy')
            <p class="error-text" style="margin-top: -8px; margin-bottom: 14px;">{{ $message }}</p>
        @enderror
        <button type="submit" class="primary">Daftar</button>
    </form>
    <div class="divider">atau</div>
    <x-google-button>Daftar dengan Google</x-google-button>
    <p class="switch">Sudah punya akun? <a href="{{ route('login') }}">Masuk</a></p>
</x-auth-card>
