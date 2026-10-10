<div>
    @if (session('status'))
        <p style="background: var(--primary-fixed); color: var(--on-primary-container); padding: 12px 16px; border-radius: 12px; font-size: 14px;">
            {{ session('status') }}
        </p>
    @endif

    <h1>Selamat datang, {{ auth()->user()->name }}</h1>
    <p>Belum ada apa-apa di sini — menyusul fitur To-Do Tim dan Follow-up Kerja.</p>

    <div style="margin-top: 24px; padding: 16px; border: 1px solid var(--outline-variant); border-radius: 16px;">
        @if (auth()->user()->google_sub)
            <p style="margin: 0 0 12px;">✅ Akun Google terhubung.</p>
            <form method="POST" action="{{ route('auth.google.destroy') }}">
                @csrf
                @method('DELETE')
                <button type="submit" style="background: none; border: 1px solid var(--outline-variant); color: var(--on-surface-variant); padding: 8px 16px; border-radius: 999px; cursor: pointer; font-family: 'Manrope', sans-serif;">Putuskan Google</button>
            </form>
        @else
            <p style="margin: 0 0 12px;">Akun Google belum terhubung.</p>
            <a href="{{ route('auth.google') }}" style="display: inline-block; background: var(--primary); color: var(--on-primary); text-decoration: none; padding: 8px 16px; border-radius: 999px; font-family: 'Manrope', sans-serif; font-weight: 600; font-size: 14px;">Hubungkan Google</a>
        @endif
    </div>
</div>
