<x-layout title="Akun">
    <h1>Akun</h1>

    @if ($errors->has('google'))
        <p class="notice error" role="alert">{{ $errors->first('google') }}</p>
    @endif

    <section style="margin-top: 24px; padding: 20px; border: 1px solid var(--outline-variant); border-radius: 16px;">
        <h2 style="margin: 0 0 12px; font-size: 17px;">Profil</h2>
        <p style="margin: 0 0 4px;"><strong>{{ $user->name }}</strong></p>
        <p style="margin: 0; color: var(--on-surface-variant);">
            {{ $user->email }}
            @if ($user->hasVerifiedEmail())
                · email terverifikasi
            @endif
        </p>
    </section>

    <section style="margin-top: 16px; padding: 20px; border: 1px solid var(--outline-variant); border-radius: 16px;">
        <h2 style="margin: 0 0 12px; font-size: 17px;">{{ $user->password ? 'Ganti kata sandi' : 'Buat kata sandi' }}</h2>
        @unless ($user->password)
            <p style="margin: 0 0 16px; color: var(--on-surface-variant); font-size: 14px;">Akunmu masuk lewat Google. Dengan kata sandi, kamu juga bisa masuk dengan email.</p>
        @endunless
        <form method="POST" action="{{ route('account.password') }}" style="display: block; max-width: 380px;">
            @csrf
            @method('PUT')
            @if ($user->password)
                <div class="field">
                    <label for="current_password">Kata sandi saat ini</label>
                    <input type="password" id="current_password" name="current_password" required autocomplete="current-password">
                    @error('current_password')
                        <p class="error-text">{{ $message }}</p>
                    @enderror
                </div>
            @endif
            <div class="field">
                <label for="password">Kata sandi baru</label>
                <input type="password" id="password" name="password" required autocomplete="new-password">
                <p style="font-size: 12.5px; color: var(--on-surface-variant); margin: 6px 0 0;">Minimal 8 karakter, dan bukan kata sandi yang umum dipakai.</p>
                @error('password')
                    <p class="error-text">{{ $message }}</p>
                @enderror
            </div>
            <div class="field">
                <label for="password_confirmation">Ulangi kata sandi baru</label>
                <input type="password" id="password_confirmation" name="password_confirmation" required autocomplete="new-password">
            </div>
            <button type="submit" class="primary">{{ $user->password ? 'Ganti kata sandi' : 'Buat kata sandi' }}</button>
            <p style="font-size: 12.5px; color: var(--on-surface-variant); margin: 12px 0 0;">Perangkat lain (HP dan browser lain) akan dikeluarkan.</p>
        </form>
    </section>

    <section style="margin-top: 16px; padding: 20px; border: 1px solid var(--outline-variant); border-radius: 16px;">
        <h2 style="margin: 0 0 12px; font-size: 17px;">Google</h2>
        @if ($user->google_sub)
            <p style="margin: 0 0 12px;">Akun Google terhubung.</p>
            <form method="POST" action="{{ route('auth.google.destroy') }}">
                @csrf
                @method('DELETE')
                <button type="submit" style="background: none; border: 1px solid var(--outline-variant); color: var(--on-surface-variant); padding: 8px 16px; border-radius: 999px; cursor: pointer; font-family: 'Manrope', sans-serif;">Putuskan Google</button>
            </form>
        @else
            <p style="margin: 0 0 12px;">Akun Google belum terhubung.</p>
            <a href="{{ route('auth.google') }}" style="display: inline-block; background: var(--primary); color: var(--on-primary); text-decoration: none; padding: 8px 16px; border-radius: 999px; font-family: 'Manrope', sans-serif; font-weight: 600; font-size: 14px;">Hubungkan Google</a>
        @endif
    </section>

    <section style="margin-top: 16px; padding: 20px; border: 1px solid var(--outline-variant); border-radius: 16px;">
        <h2 style="margin: 0 0 8px; font-size: 17px;">Keluar dari semua perangkat</h2>
        <p style="margin: 0 0 12px; color: var(--on-surface-variant); font-size: 14px;">Mengeluarkan akunmu dari semua HP dan browser, termasuk yang ini. Berguna kalau HP hilang. Data di server tidak dihapus.</p>
        <form method="POST" action="{{ route('account.logout-all') }}">
            @csrf
            <button type="submit" style="background: none; border: 1px solid var(--error); color: var(--error); padding: 8px 16px; border-radius: 999px; cursor: pointer; font-family: 'Manrope', sans-serif;">Keluar dari semua perangkat</button>
        </form>
    </section>
</x-layout>
