<div
    x-data="{ open: false, message: '', run: null }"
    x-on:confirm-action.window="open = true; message = $event.detail.message; run = $event.detail.action"
    x-show="open"
    x-cloak
    style="position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45); display: flex; align-items: center; justify-content: center; padding: 16px; z-index: 100;"
    @click.self="open = false"
    @keydown.escape.window="open = false"
>
    <div class="hf-card" role="alertdialog" aria-modal="true" style="max-width: 380px; width: 100%; padding: 22px 24px; display: flex; flex-direction: column; gap: 18px;">
        <p style="margin: 0; font-size: 15px; line-height: 1.5;" x-text="message"></p>
        <div style="display: flex; justify-content: flex-end; gap: 16px;">
            <button type="button" class="hf-btn-text" style="color: var(--on-surface-variant);" @click="open = false">Batal</button>
            <button type="button" class="hf-btn-text" style="color: var(--error);" @click="run && run(); open = false">Hapus</button>
        </div>
    </div>
</div>
