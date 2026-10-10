<x-auth-card title="Undangan Tim">
    @if ($valid)
        <p style="text-align: center; margin-bottom: 20px;">
            Kamu diundang bergabung ke tim <strong>{{ $invite->team->name }}</strong>.
        </p>
        <form method="POST" action="{{ route('invites.accept', $invite->token) }}">
            @csrf
            <button type="submit" class="primary">Gabung Tim</button>
        </form>
    @else
        <p style="text-align: center; color: var(--on-surface-variant);">
            Link undangan ini sudah tidak berlaku (kedaluwarsa atau sudah dipakai).
        </p>
    @endif
</x-auth-card>
