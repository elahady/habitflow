<#
.SYNOPSIS
    Langkah wajib sebelum mengunggah AAB (tahap 31J): seluruh tes unit Android, lalu cek-rilis.ps1.

.DESCRIPTION
    Menjalankan `gradlew testDebugUnitTest` (semua tes unit di app/src/test), lalu cek-rilis.ps1 (yang menyiapkan
    manifest dan BuildConfig rilis lewat Gradle sendiri). Berhenti di tes yang gagal. Kode keluar 0 hanya kalau
    tes dan semua pemeriksaan rilis lulus; selain itu JANGAN unggah AAB.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot "_jdk.ps1")
Pakai-Jdk17

Write-Host '== 1/2 Tes unit Android =='
Push-Location $root
try { & (Join-Path $root 'gradlew.bat') cleanTestDebugUnitTest testDebugUnitTest --console=plain; $kodeTes = $LASTEXITCODE } finally { Pop-Location }
if ($kodeTes -ne 0) {
    Write-Host 'Tes unit gagal. JANGAN unggah AAB.' -ForegroundColor Red
    exit 1
}

$laporan = @(Get-ChildItem -LiteralPath (Join-Path $root 'app/build/test-results/testDebugUnitTest') -Filter *.xml -ErrorAction SilentlyContinue)
$jumlah = 0
foreach ($f in $laporan) { $jumlah += [int]([xml](Get-Content -LiteralPath $f.FullName -Raw)).testsuite.tests }
Write-Host "Tes unit lulus: $jumlah tes di $($laporan.Count) kelas."

Write-Host ''
Write-Host '== 2/2 Cek rilis =='
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'cek-rilis.ps1') -Root $root
exit $LASTEXITCODE
