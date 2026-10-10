<x-auth-card title="Daftar">
    <x-slot:tagline>Catat habit, to-do, dan jadwal harianmu dalam 2 detik — tanpa pikir panjang soal kategori atau tanggal dulu.</x-slot:tagline>
    <form method="POST" action="{{ route('register') }}">
        @csrf
        <div class="field">
            <label for="name">Nama</label>
            <input type="text" id="name" name="name" value="{{ old('name') }}" required autofocus>
        </div>
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ old('email') }}" required>
            @error('email')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password">Kata sandi</label>
            <input type="password" id="password" name="password" required>
            @error('password')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <div class="field">
            <label for="password_confirmation">Ulangi kata sandi</label>
            <input type="password" id="password_confirmation" name="password_confirmation" required>
        </div>
        <button type="submit" class="primary">Daftar</button>
    </form>
    <div class="divider">atau</div>
    <x-google-button>Daftar dengan Google</x-google-button>
    <p class="switch">Sudah punya akun? <a href="{{ route('login') }}">Masuk</a></p>
</x-auth-card>
