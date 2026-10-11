<script>
(function () {
    try {
        var theme = localStorage.getItem('habitflow-theme');
        if (theme === 'light' || theme === 'dark') {
            document.documentElement.setAttribute('data-theme', theme);
        }
    } catch (e) {}
})();
</script>
