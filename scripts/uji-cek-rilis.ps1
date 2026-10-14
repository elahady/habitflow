<#
.SYNOPSIS
    Menguji cek-rilis.ps1 dengan sengaja melanggar tiap pemeriksaan (tahap 31J).

.DESCRIPTION
    Menyalin berkas yang dibaca cek-rilis.ps1 ke folder sementara (repo git baru), memastikan salinan yang utuh lulus,
    lalu untuk setiap pelanggaran: merusak satu hal di salinan baru, menjalankan cek-rilis.ps1, dan menuntut skrip itu
    keluar dengan kode 1 dan menggagalkan PERSIS pemeriksaan yang dimaksud (bukan yang lain). Repo asli tidak diubah.
    Keluar dengan kode 0 kalau semua pelanggaran terbukti tertangkap.

    Jalankan setiap kali cek-rilis.ps1 diubah. Skrip yang tidak pernah terbukti gagal tidak bisa dipercaya lulus.

.PARAMETER TanpaGradle
    Pakai keluaran build rilis yang sudah ada alih-alih menjalankan Gradle dulu.
#>
[CmdletBinding()]
param([switch]$TanpaGradle)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$root = Split-Path -Parent $PSScriptRoot
$cekRilis = Join-Path $PSScriptRoot 'cek-rilis.ps1'

if (-not $TanpaGradle) {
    . (Join-Path $PSScriptRoot "_jdk.ps1")
    Pakai-Jdk17
    Write-Host 'Menyiapkan manifest dan BuildConfig rilis lewat Gradle...'
    Push-Location $root
    try { & (Join-Path $root 'gradlew.bat') processReleaseManifest generateReleaseBuildConfig --console=plain -q } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Host 'Gradle gagal.' -ForegroundColor Red; exit 2 }
}

$manifestRel = 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
$buildConfigRel = 'app/build/generated/source/buildConfig/release/com/roziqrizal/habitflow/BuildConfig.java'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Baru-Salinan {
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('cek-rilis-uji-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    function Salin([string]$rel) {
        $dari = Join-Path $root $rel
        $ke = Join-Path $tmp $rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ke) | Out-Null
        Copy-Item -LiteralPath $dari -Destination $ke
    }
    foreach ($rel in @('.gitignore', 'app/build.gradle.kts', $manifestRel, $buildConfigRel,
            'app/src/main/java/com/roziqrizal/habitflow/data/PrefFile.kt')) { Salin $rel }
    foreach ($pola in @('app/src/main/res/xml/*.xml', 'app/src/debug/java/*/*/*/debug/*.kt', 'scripts/*.txt')) {
        foreach ($f in Get-ChildItem -Path (Join-Path $root $pola)) { Salin ($f.FullName.Substring($root.Length + 1) -replace '\\', '/') }
    }
    # Syarat targetSdk yang sungguhan (angka pemilik) boleh lebih tinggi dari build sekarang; salinan dasar memakai 34
    # supaya dasar yang utuh bisa lulus dan yang diuji hanya pelanggarannya.
    [System.IO.File]::WriteAllText((Join-Path $tmp 'scripts/target-sdk-minimum.txt'), "34`n", $utf8)
    & git -C $tmp init -q
    if ($LASTEXITCODE -ne 0) { throw 'git init gagal' }
    $tmp
}

function Ubah([string]$tmp, [string]$rel, [scriptblock]$Fungsi) {
    $path = Join-Path $tmp $rel
    $baru = & $Fungsi ([System.IO.File]::ReadAllText($path))
    [System.IO.File]::WriteAllText($path, $baru, $utf8)
}

function Ganti([string]$tmp, [string]$rel, [string]$cari, [string]$ganti) {
    Ubah $tmp $rel {
        param($t)
        $n = [regex]::Matches($t, [regex]::Escape($cari)).Count
        if ($n -lt 1) { throw "pola uji tidak ditemukan di ${rel}: $cari" }
        ([regex]::new([regex]::Escape($cari))).Replace($t, $ganti.Replace('$', '$$'), 1)
    }
}

function Tulis([string]$tmp, [string]$rel, [string]$isi) {
    $path = Join-Path $tmp $rel
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [System.IO.File]::WriteAllText($path, $isi, $utf8)
}

$clientId = [regex]::Match([System.IO.File]::ReadAllText((Join-Path $root $buildConfigRel)), 'GOOGLE_SERVER_CLIENT_ID = "([^"]*)"').Groups[1].Value
$rahasiaPalsu = 'GOC' + 'SPX-' + ('x' * 24)

# Id, uraian, pemeriksaan yang HARUS gagal (kosong = harus lulus semua), cara melanggar.
$kasus = @(
    @{ Uraian = 'salinan utuh (dasar)'; Gagal = @(); Langkah = { param($t) } },
    @{ Uraian = 'android:debuggable="true"'; Gagal = @('debuggable'); Langkah = { param($t) Ganti $t $manifestRel '<application' '<application android:debuggable="true"' } },
    @{ Uraian = 'receiver debug terdaftar'; Gagal = @('komponen-debug'); Langkah = { param($t)
            Ganti $t $manifestRel '</application>' '<receiver android:name="com.roziqrizal.habitflow.debug.DebugSessionReceiver" android:exported="true" /></application>' } },
    @{ Uraian = 'usesCleartextTraffic="true"'; Gagal = @('cleartext'); Langkah = { param($t) Ganti $t $manifestRel '<application' '<application android:usesCleartextTraffic="true"' } },
    @{ Uraian = 'API_BASE_URL http://'; Gagal = @('https'); Langkah = { param($t)
            Ubah $t $buildConfigRel { param($x) [regex]::Replace($x, '(API_BASE_URL = ")https://', '${1}http://') } } },
    @{ Uraian = 'izin baru terselip di manifest (CAMERA)'; Gagal = @('izin'); Langkah = { param($t)
            Ganti $t $manifestRel '<application' '<uses-permission android:name="android.permission.CAMERA" /><application' } },
    @{ Uraian = 'izin disetujui tapi tidak ada di manifest'; Gagal = @('izin'); Langkah = { param($t)
            Ubah $t 'scripts/izin-disetujui.txt' { param($x) $x + "android.permission.CAMERA`n" } } },
    @{ Uraian = 'targetSdk di bawah syarat (syarat 99)'; Gagal = @('target-sdk'); Langkah = { param($t) Tulis $t 'scripts/target-sdk-minimum.txt' "99`n" } },
    @{ Uraian = 'isMinifyEnabled = true'; Gagal = @('minify'); Langkah = { param($t) Ganti $t 'app/build.gradle.kts' 'isMinifyEnabled = false' 'isMinifyEnabled = true' } },
    @{ Uraian = '<include> di backup_rules.xml'; Gagal = @('backup'); Langkah = { param($t)
            Ganti $t 'app/src/main/res/xml/backup_rules.xml' '</full-backup-content>' '<include domain="sharedpref" path="x.xml" /></full-backup-content>' } },
    @{ Uraian = 'account.xml tidak dikecualikan di cloud-backup'; Gagal = @('backup'); Langkah = { param($t)
            Ganti $t 'app/src/main/res/xml/data_extraction_rules.xml' '<exclude domain="sharedpref" path="account.xml" />' '' } },
    @{ Uraian = 'bagian device-transfer dihapus'; Gagal = @('backup'); Langkah = { param($t)
            Ubah $t 'app/src/main/res/xml/data_extraction_rules.xml' { param($x) [regex]::Replace($x, '(?s)<device-transfer>.*?</device-transfer>', '') } } },
    @{ Uraian = 'manifest tidak menunjuk aturan cadangan'; Gagal = @('backup'); Langkah = { param($t)
            Ganti $t $manifestRel 'android:fullBackupContent="@xml/backup_rules"' '' } },
    @{ Uraian = 'client ID rilis kosong'; Gagal = @('client-id'); Langkah = { param($t)
            Ubah $t $buildConfigRel { param($x) [regex]::Replace($x, '(GOOGLE_SERVER_CLIENT_ID = ")[^"]*"', '${1}"') } } },
    @{ Uraian = 'client ID rilis tercantum sebagai nilai uji'; Gagal = @('client-id'); Langkah = { param($t)
            Tulis $t 'scripts/client-id-uji.txt' "$clientId`n" } },
    @{ Uraian = 'client_secret_*.json dipaksa masuk repo (git add -f, melewati .gitignore)'; Gagal = @('rahasia'); Langkah = { param($t) Tulis $t 'client_secret_1.json' "{}`n"; & git -C $t add -f client_secret_1.json } },
    @{ Uraian = 'keystore (.jks) dipaksa masuk repo (git add -f, melewati .gitignore)'; Gagal = @('rahasia'); Langkah = { param($t) Tulis $t 'upload.jks' "x`n"; & git -C $t add -f upload.jks } },
    @{ Uraian = 'rahasia klien Google di dalam berkas teks'; Gagal = @('rahasia'); Langkah = { param($t) Tulis $t 'docs/catatan.txt' "kunci: $rahasiaPalsu`n" } },
    @{ Uraian = 'sandi keystore di berkas .properties'; Gagal = @('rahasia'); Langkah = { param($t) Tulis $t 'catatan.properties' "storePassword=sandirahasia`n" } },
    @{ Uraian = 'versionCode sama dengan unggahan terakhir'; Gagal = @('version-code'); Langkah = { param($t) Tulis $t 'scripts/versi-terakhir.txt' "1`n" } }
)

$salah = 0
foreach ($k in $kasus) {
    $tmp = Baru-Salinan
    try {
        & $k.Langkah $tmp
        $keluaran = @(& powershell -NoProfile -ExecutionPolicy Bypass -File $cekRilis -Root $tmp -TanpaGradle | ForEach-Object { "$_" })
        $kode = $LASTEXITCODE
        $gagal = @($keluaran | ForEach-Object { if ($_ -match '^\[GAGAL\] (\S+)') { $Matches[1] } } | Sort-Object -Unique)
        $harusGagal = @($k.Gagal | Sort-Object -Unique)
        $kodeBenar = if ($harusGagal.Count -eq 0) { 0 } else { 1 }
        $sama = ($gagal -join ',') -eq ($harusGagal -join ',')
        if ($sama -and $kode -eq $kodeBenar) {
            Write-Host ("[OK]    {0}  ->  {1}" -f $k.Uraian, $(if ($gagal.Count) { "gagal: $($gagal -join ', ')" } else { 'lulus semua' })) -ForegroundColor Green
        } else {
            $salah++
            Write-Host ("[SALAH] {0}: harusnya gagal [{1}], nyatanya [{2}], kode {3}" -f $k.Uraian, ($harusGagal -join ', '), ($gagal -join ', '), $kode) -ForegroundColor Red
            $keluaran | Where-Object { $_ -match '^\[GAGAL\]|^\s{8}' } | ForEach-Object { Write-Host "        $_" }
        }
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
if ($salah -eq 0) {
    Write-Host "Semua $($kasus.Count) kasus sesuai: cek-rilis.ps1 lulus pada yang utuh dan gagal pada setiap pelanggaran." -ForegroundColor Green
    exit 0
}
Write-Host "$salah dari $($kasus.Count) kasus tidak sesuai." -ForegroundColor Red
exit 1
