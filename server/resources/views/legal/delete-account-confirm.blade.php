<x-layout title="Konfirmasi hapus akun">
    <article class="prose">
        <h1>Konfirmasi hapus akun</h1>
        <p class="notice error" style="font-size: 14px;">
            Ini akan menghapus akun dan <strong>semua datamu</strong> secara langsung dan permanen. Tidak bisa dibatalkan.
        </p>
        <form method="POST" action="{{ route('delete-account.destroy') }}" style="display: block; max-width: 380px;">
            @csrf
            <div class="field">
                <label for="email">Email akun</label>
                <input type="email" id="email" name="email" value="{{ $email }}" required autocomplete="email">
                @error('email')
                    <p class="error-text">{{ $message }}</p>
                @enderror
            </div>
            <div class="field">
                <label for="code">Kode 6 angka dari email</label>
                <input type="text" id="code" name="code" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="one-time-code" required autofocus
                       style="text-align: center; letter-spacing: 0.5em; font-size: 22px;">
                @error('code')
                    <p class="error-text">{{ $message }}</p>
                @enderror
            </div>
            <button type="submit" style="background: var(--error); color: var(--background); border: none; padding: 12px 24px; border-radius: 999px; cursor: pointer; font-family: 'Manrope', sans-serif; font-weight: 600; font-size: 15px;"
                    onclick="return confirm('Hapus akun dan semua datamu secara permanen?');">Hapus akun permanen</button>
        </form>
        <p style="margin-top: 16px;"><a href="{{ route('delete-account') }}">Minta kode baru</a></p>
    </article>
</x-layout>
