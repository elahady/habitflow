<div
    x-data="{ open: false, message: '', label: 'Hapus', run: null }"
    x-on:confirm-action.window="open = true; message = $event.detail.message; label = $event.detail.label || 'Hapus'; run = $event.detail.action"
    x-show="open"
    x-cloak
    class="hf-modal-backdrop"
    @click.self="open = false"
    @keydown.escape.window="open = false"
>
    <div class="hf-card hf-modal" role="alertdialog" aria-modal="true">
        <p style="margin: 0; font-size: 15px; line-height: 1.5;" x-text="message"></p>
        <div style="display: flex; justify-content: flex-end; gap: 16px;">
            <button type="button" class="hf-btn-text" style="color: var(--on-surface-variant);" @click="open = false">Batal</button>
            <button type="button" class="hf-btn-text" style="color: var(--error);" @click="run && run(); open = false" x-text="label">Hapus</button>
        </div>
    </div>
</div>
