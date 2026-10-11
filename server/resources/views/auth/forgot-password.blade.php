<x-auth-card title="Lupa kata sandi">
    <x-slot:tagline>Masukkan emailmu. Kalau terdaftar, kami kirim kode 6 angka untuk membuat kata sandi baru.</x-slot:tagline>
    <form method="POST" action="{{ route('password.forgot') }}">
        @csrf
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ old('email') }}" required autofocus autocomplete="email">
            @error('email')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <button type="submit" class="primary">Kirim kode</button>
    </form>
    <p class="switch">Sudah punya kode? <a href="{{ route('password.reset') }}">Masukkan kode</a></p>
    <p class="switch" style="margin-top: 8px;"><a href="{{ route('login') }}">Kembali ke Masuk</a></p>
</x-auth-card>
