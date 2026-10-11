:root {
    --primary: #4D6359;
    --on-primary: #FFFFFF;
    --primary-fixed: #CFE8DB;
    --on-primary-container: #273C33;
    --background: #FAF9F7;
    --surface-lowest: #FFFFFF;
    --surface-container-low: #F5F3F1;
    --on-surface: #1B1C1B;
    --on-surface-variant: #424845;
    --outline-variant: #C2C8C3;
    --error: #BA1A1A;
}
@media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
        --primary: #B3CCBF;
        --on-primary: #1F352B;
        --primary-fixed: #354B42;
        --on-primary-container: #CFE8DB;
        --background: #121412;
        --surface-lowest: #0D0F0E;
        --surface-container-low: #1A1C1A;
        --on-surface: #E3E3E0;
        --on-surface-variant: #C2C8C3;
        --outline-variant: #424845;
        --error: #FFB4AB;
    }
}
:root[data-theme="dark"] {
    --primary: #B3CCBF;
    --on-primary: #1F352B;
    --primary-fixed: #354B42;
    --on-primary-container: #CFE8DB;
    --background: #121412;
    --surface-lowest: #0D0F0E;
    --surface-container-low: #1A1C1A;
    --on-surface: #E3E3E0;
    --on-surface-variant: #C2C8C3;
    --outline-variant: #424845;
    --error: #FFB4AB;
}
.theme-toggle {
    background: none;
    border: 1px solid var(--outline-variant);
    border-radius: 999px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: var(--on-surface-variant);
    flex-shrink: 0;
}
.theme-toggle:hover { color: var(--on-surface); }
.theme-toggle svg { width: 18px; height: 18px; }
.theme-toggle .icon-sun { display: none; }
.theme-toggle .icon-moon { display: block; }
:root[data-theme="dark"] .theme-toggle .icon-sun { display: block; }
:root[data-theme="dark"] .theme-toggle .icon-moon { display: none; }
@media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) .theme-toggle .icon-sun { display: block; }
    :root:not([data-theme="light"]) .theme-toggle .icon-moon { display: none; }
}
