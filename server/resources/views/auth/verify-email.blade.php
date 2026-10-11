<x-auth-card title="Verifikasi email">
    <x-slot:tagline>Masukkan kode 6 angka yang kami kirim ke emailmu. Kode berlaku 10 menit.</x-slot:tagline>
    <form method="POST" action="{{ route('verification.verify') }}">
        @csrf
        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" value="{{ $email }}" required autocomplete="email">
        </div>
        <div class="field">
            <label for="code">Kode verifikasi</label>
            <input type="text" id="code" name="code" class="code" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="one-time-code" required autofocus>
            @error('code')
                <p class="error-text">{{ $message }}</p>
            @enderror
        </div>
        <button type="submit" class="primary">Verifikasi</button>
    </form>
    <form method="POST" action="{{ route('verification.resend') }}" class="center" style="display: block;">
        @csrf
        <input type="hidden" name="email" value="{{ $email }}">
        <button type="submit" class="link">Kirim ulang kode</button>
    </form>
    <p class="switch"><a href="{{ route('login') }}">Kembali ke Masuk</a></p>
</x-auth-card>
