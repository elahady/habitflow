# Dipakai bersama (dot-source) oleh skrip di folder ini: Gradle proyek ini butuh JDK 17.
# Kalau JAVA_HOME sekarang bukan JDK 17 (di mesin ini bisa menunjuk JDK 11), pakai JDK 17 yang dicatat di CLAUDE.md.
function Pakai-Jdk17 {
    $dipakai = $false
    if ($env:JAVA_HOME) {
        $rilis = Join-Path $env:JAVA_HOME 'release'
        if ((Test-Path -LiteralPath $rilis) -and ((Get-Content -LiteralPath $rilis -Raw) -match 'JAVA_VERSION="17\.')) { $dipakai = $true }
    }
    if (-not $dipakai) {
        $jdk = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
        if (Test-Path -LiteralPath $jdk) { $env:JAVA_HOME = $jdk }
    }
}
