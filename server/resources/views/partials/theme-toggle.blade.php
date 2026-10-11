<button type="button" class="theme-toggle {{ $class ?? '' }}" onclick="habitflowToggleTheme()" aria-label="Ganti tema terang/gelap" title="Ganti tema terang/gelap">
    <svg class="icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="4"></circle>
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path>
    </svg>
    <svg class="icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
    </svg>
</button>
<script>
    function habitflowToggleTheme() {
        var html = document.documentElement;
        var current = html.getAttribute('data-theme');
        var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        var isDark = current === 'dark' || (current !== 'light' && prefersDark);
        var next = isDark ? 'light' : 'dark';
        html.setAttribute('data-theme', next);
        try { localStorage.setItem('habitflow-theme', next); } catch (e) {}
    }
</script>
