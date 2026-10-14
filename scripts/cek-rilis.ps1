<#
.SYNOPSIS
    Cek rilis Habitflow (tahap 31J). Dijalankan sebelum mengunggah AAB ke Play Console.

.DESCRIPTION
    Memeriksa manifest rilis hasil gabungan, BuildConfig rilis, konfigurasi build, aturan cadangan, dan isi repo.
    Keluar dengan kode 0 kalau semua lulus, 1 kalau ada yang gagal (JANGAN unggah), 2 kalau pemeriksaan tidak bisa
    dijalankan (misalnya Gradle gagal). Daftar pemeriksaan dan alasannya: docs/rilis-play-store.md bagian 3.

    Berkas yang diisi pemilik ada di folder scripts: izin-disetujui.txt, target-sdk-minimum.txt, versi-terakhir.txt,
    client-id-uji.txt.

.PARAMETER Root
    Akar repo. Bawaannya induk folder scripts. Dipakai uji-cek-rilis.ps1 untuk menjalankan skrip ini pada salinan.

.PARAMETER TanpaGradle
    Jangan menjalankan Gradle dulu; baca keluaran build yang sudah ada. Bawaannya Gradle selalu dijalankan
    (processReleaseManifest dan generateReleaseBuildConfig, cepat kalau tidak ada perubahan) supaya yang diperiksa
    pasti sesuai kode sekarang, bukan sisa build lama.
#>
[CmdletBinding()]
param(
    [string]$Root = '',
    [switch]$TanpaGradle
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
if ($Root -eq '') { $Root = Split-Path -Parent $PSScriptRoot }

# Keputusan pemilik 11 Oktober 2026: R8 mati untuk rilis pertama supaya biner uji tertutup sama dengan yang dirilis.
# Ubah ke 'true' hanya bersama keputusan menyalakannya (docs/concept.md, 31L).
$MinifyDiharapkan = 'false'

$ns = 'http://schemas.android.com/apk/res/android'
$Root = (Resolve-Path -LiteralPath $Root).Path
$scriptsDir = Join-Path $Root 'scripts'
$manifestPath = Join-Path $Root 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
$buildConfigPath = Join-Path $Root 'app/build/generated/source/buildConfig/release/com/roziqrizal/habitflow/BuildConfig.java'
$gradlePath = Join-Path $Root 'app/build.gradle.kts'
$xmlDir = Join-Path $Root 'app/src/main/res/xml'
$prefFilePath = Join-Path $Root 'app/src/main/java/com/roziqrizal/habitflow/data/PrefFile.kt'
$debugSrcDir = Join-Path $Root 'app/src/debug/java'

# ---------------------------------------------------------------- Gradle

if (-not $TanpaGradle) {
    . (Join-Path $PSScriptRoot "_jdk.ps1")
    Pakai-Jdk17
    Write-Host 'Menyiapkan manifest dan BuildConfig rilis lewat Gradle...'
    Push-Location $Root
    try {
        & (Join-Path $Root 'gradlew.bat') processReleaseManifest generateReleaseBuildConfig --console=plain -q
        $kodeGradle = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($kodeGradle -ne 0) {
        Write-Host "Gradle gagal (kode $kodeGradle). Pemeriksaan tidak dijalankan." -ForegroundColor Red
        exit 2
    }
}

# ---------------------------------------------------------------- Pembantu

$hasil = New-Object System.Collections.Generic.List[object]

function Cek([string]$Id, [string]$Judul, [scriptblock]$Isi) {
    try {
        $masalah = @(& $Isi)
    } catch {
        $masalah = @("pemeriksaan tidak bisa dijalankan: $($_.Exception.Message)")
    }
    $hasil.Add([pscustomobject]@{ Id = $Id; Judul = $Judul; Masalah = $masalah })
}

function Baca-Daftar([string]$Nama) {
    $path = Join-Path $scriptsDir $Nama
    if (-not (Test-Path -LiteralPath $path)) { throw "scripts/$Nama tidak ada" }
    @(Get-Content -LiteralPath $path | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' -and -not $_.StartsWith('#') })
}

function Baca-Angka([string]$Nama) {
    $baris = @(Baca-Daftar $Nama)
    if ($baris.Count -ne 1 -or $baris[0] -notmatch '^\d+$') { throw "scripts/$Nama harus berisi satu angka" }
    [int]$baris[0]
}

function Baca-Manifest {
    if (-not (Test-Path -LiteralPath $manifestPath)) { throw "manifest rilis gabungan tidak ada ($manifestPath); jalankan tanpa -TanpaGradle" }
    $xml = New-Object System.Xml.XmlDocument
    $xml.Load($manifestPath)
    $xml
}

function Baca-BuildConfig([string]$Nama) {
    if (-not (Test-Path -LiteralPath $buildConfigPath)) { throw "BuildConfig rilis tidak ada ($buildConfigPath); jalankan tanpa -TanpaGradle" }
    $m = [regex]::Match((Get-Content -LiteralPath $buildConfigPath -Raw), '(?m)^\s*public static final \w+ ' + [regex]::Escape($Nama) + ' = (.*);\s*$')
    if (-not $m.Success) { throw "BuildConfig tidak memuat $Nama" }
    $m.Groups[1].Value.Trim().Trim('"')
}

# Isi blok `nama { ... }` pertama (kurung kurawal dihitung, jadi blok bersarang dan "${...}" di string aman).
function Ambil-Blok([string]$Teks, [string]$Nama) {
    $m = [regex]::Match($Teks, '(?m)^\s*' + [regex]::Escape($Nama) + '\s*\{')
    if (-not $m.Success) { throw "blok '$Nama' tidak ditemukan di app/build.gradle.kts" }
    $mulai = $m.Index + $m.Length
    $dalam = 1
    $i = $mulai
    while ($i -lt $Teks.Length -and $dalam -gt 0) {
        if ($Teks[$i] -eq '{') { $dalam++ } elseif ($Teks[$i] -eq '}') { $dalam-- }
        $i++
    }
    if ($dalam -ne 0) { throw "blok '$Nama' tidak tertutup" }
    $Teks.Substring($mulai, $i - $mulai - 1)
}

# ---------------------------------------------------------------- Pemeriksaan

Cek 'debuggable' 'Build tidak debuggable' {
    $app = (Baca-Manifest).SelectSingleNode('/manifest/application')
    if ($app.GetAttribute('debuggable', $ns) -eq 'true') { 'android:debuggable="true" di manifest rilis' }
    if ((Baca-BuildConfig 'DEBUG') -ne 'false') { 'BuildConfig.DEBUG bernilai true' }
    if ((Baca-BuildConfig 'BUILD_TYPE') -ne 'release') { 'BuildConfig bukan dari varian release' }
}

Cek 'komponen-debug' 'Tidak ada komponen src/debug di manifest rilis' {
    $xml = Baca-Manifest
    $nama = @($xml.SelectNodes('/manifest/application/*') | ForEach-Object { $_.GetAttribute('name', $ns) } | Where-Object { $_ -ne '' })
    # Kelas dari src/debug (mis. DebugSessionReceiver) tidak boleh terdaftar, dan paket .debug. tidak boleh ada.
    $kelasDebug = @()
    if (Test-Path -LiteralPath $debugSrcDir) {
        $kelasDebug = @(Get-ChildItem -LiteralPath $debugSrcDir -Recurse -Filter *.kt | ForEach-Object { $_.BaseName })
    }
    foreach ($n in $nama) {
        if ($n -match '(?i)\.debug\.') { "komponen di paket debug terdaftar: $n" }
        elseif ($kelasDebug -contains ($n -split '\.')[-1]) { "komponen dari src/debug terdaftar: $n" }
    }
}

Cek 'cleartext' 'Lalu lintas tanpa enkripsi tidak diizinkan' {
    $app = (Baca-Manifest).SelectSingleNode('/manifest/application')
    if ($app.GetAttribute('usesCleartextTraffic', $ns) -eq 'true') { 'android:usesCleartextTraffic="true" di manifest rilis' }
    if ($app.GetAttribute('networkSecurityConfig', $ns) -ne '') { 'manifest rilis memakai networkSecurityConfig (konfigurasi itu khusus debug)' }
}

Cek 'https' 'Alamat server rilis https://' {
    $url = Baca-BuildConfig 'API_BASE_URL'
    if ($url -notmatch '^https://[^/\s]+') { "API_BASE_URL rilis bukan https:// (nilainya: $url)" }
}

Cek 'izin' 'Izin sama persis dengan scripts/izin-disetujui.txt' {
    $xml = Baca-Manifest
    $ada = @($xml.SelectNodes('/manifest/uses-permission | /manifest/uses-permission-sdk-23') |
        ForEach-Object { $_.GetAttribute('name', $ns) } | Sort-Object -Unique)
    $setuju = @(Baca-Daftar 'izin-disetujui.txt' | Sort-Object -Unique)
    foreach ($p in $ada) { if ($setuju -notcontains $p) { "izin di manifest tapi tidak disetujui: $p" } }
    foreach ($p in $setuju) { if ($ada -notcontains $p) { "izin disetujui tapi tidak ada di manifest: $p" } }
}

Cek 'target-sdk' 'targetSdk memenuhi syarat Play (scripts/target-sdk-minimum.txt)' {
    $sdk = (Baca-Manifest).SelectSingleNode('/manifest/uses-sdk')
    $target = [int]$sdk.GetAttribute('targetSdkVersion', $ns)
    $minimum = Baca-Angka 'target-sdk-minimum.txt'
    if ($target -lt $minimum) { "targetSdk $target di bawah syarat $minimum" }
}

Cek 'minify' 'isMinifyEnabled sesuai keputusan' {
    $gradle = Get-Content -LiteralPath $gradlePath -Raw
    $release = Ambil-Blok (Ambil-Blok $gradle 'buildTypes') 'release'
    $m = [regex]::Match($release, '(?m)^\s*isMinifyEnabled\s*=\s*(true|false)\s*$')
    if (-not $m.Success) { 'isMinifyEnabled tidak ditulis eksplisit di blok release'; return }
    if ($m.Groups[1].Value -ne $MinifyDiharapkan) { "isMinifyEnabled = $($m.Groups[1].Value), keputusan: $MinifyDiharapkan" }
}

Cek 'backup' 'Cadangan tidak memuat database dan pengaturan' {
    $app = (Baca-Manifest).SelectSingleNode('/manifest/application')
    if ($app.GetAttribute('allowBackup', $ns) -ne 'false') {
        if ($app.GetAttribute('fullBackupContent', $ns) -ne '@xml/backup_rules') { 'manifest tidak menunjuk @xml/backup_rules lewat fullBackupContent' }
        if ($app.GetAttribute('dataExtractionRules', $ns) -ne '@xml/data_extraction_rules') { 'manifest tidak menunjuk @xml/data_extraction_rules lewat dataExtractionRules' }
    }
    if (-not (Test-Path -LiteralPath $prefFilePath)) { throw 'PrefFile.kt tidak ditemukan' }
    $prefs = @([regex]::Matches((Get-Content -LiteralPath $prefFilePath -Raw), '(?m)^\s+[A-Z][A-Z_]*\("([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    if ($prefs.Count -eq 0) { throw 'tidak ada berkas pengaturan terbaca dari PrefFile.kt' }
    $wajib = @('habitflow.db', 'habitflow.db-wal', 'habitflow.db-shm', 'habitflow.db-journal' | ForEach-Object { "database|$_" })
    $wajib += @($prefs | ForEach-Object { "sharedpref|$_.xml" })
    $bagian = @{
        'backup_rules.xml'          = @('/full-backup-content')
        'data_extraction_rules.xml' = @('/data-extraction-rules/cloud-backup', '/data-extraction-rules/device-transfer')
    }
    foreach ($nama in $bagian.Keys) {
        $path = Join-Path $xmlDir $nama
        if (-not (Test-Path -LiteralPath $path)) { "$nama tidak ada"; continue }
        $xml = New-Object System.Xml.XmlDocument
        $xml.Load($path)
        if ($xml.SelectNodes('//include').Count -gt 0) { "$nama memuat <include>" }
        foreach ($b in $bagian[$nama]) {
            if ($null -eq $xml.SelectSingleNode($b)) { "$nama tidak punya bagian $b (bagian yang hilang berarti semuanya ikut dicadangkan)"; continue }
            $dikecualikan = @($xml.SelectNodes("$b/exclude") | ForEach-Object { $_.GetAttribute('domain') + '|' + $_.GetAttribute('path') })
            foreach ($w in $wajib) { if ($dikecualikan -notcontains $w) { "$nama ($b) tidak mengecualikan $($w -replace '\|', ' ')" } }
        }
    }
}

Cek 'client-id' 'Client ID Google rilis bukan nilai uji' {
    $id = Baca-BuildConfig 'GOOGLE_SERVER_CLIENT_ID'
    if ($id -eq '') { 'GOOGLE_SERVER_CLIENT_ID rilis kosong (isi GOOGLE_SERVER_CLIENT_ID_RELEASE atau GOOGLE_SERVER_CLIENT_ID di local.properties)'; return }
    if ($id -notmatch '^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$') { 'GOOGLE_SERVER_CLIENT_ID rilis tidak berbentuk client ID Google' }
    if ((Baca-Daftar 'client-id-uji.txt') -contains $id) { 'GOOGLE_SERVER_CLIENT_ID rilis tercantum di scripts/client-id-uji.txt (nilai uji)' }
}

Cek 'rahasia' 'Tidak ada rahasia di repo' {
    # Pola disusun dari potongan supaya skrip ini tidak menemukan dirinya sendiri.
    $polaNama = @(
        '(^|/)client_secret[^/]*\.json$', '\.(jks|keystore|p12|pfx|pem)$', '(^|/)keystore\.properties$',
        '(^|/)local\.properties$', '(^|/)\.env($|\.)', '(^|/)id_(rsa|ed25519)$'
    )
    $polaIsi = [ordered]@{
        'kunci privat'        = '-----BEGIN ' + '(RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'
        'rahasia klien Google' = 'GOC' + 'SPX-[A-Za-z0-9_-]{10,}'
        'kunci API Google'    = 'AI' + 'za[0-9A-Za-z_-]{35}'
        'kunci API Resend'    = '\bre' + '_[A-Za-z0-9]{20,}'
        'token GitHub'        = '\bgh' + '[pousr]_[A-Za-z0-9]{36}'
        'APP_KEY Laravel'     = 'APP_' + 'KEY\s*=\s*base64:[A-Za-z0-9+/=]{20,}'
        'sandi keystore'      = '(?m)^\s*(store|key)' + 'Password\s*=\s*(?!\.\.\.|<)[^\s"(]+\s*$'
    }
    $biner = '\.(png|jpe?g|gif|webp|ico|jar|apk|aab|zip|ttf|otf|woff2?|pdf|so|db|sqlite)$'

    $daftar = @(& git -C $Root -c core.quotepath=off ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'git ls-files gagal (bukan repo git?)' }
    foreach ($f in ($daftar | Sort-Object -Unique)) {
        # .env.example memang ada di repo dan tidak berisi rahasia.
        if ($f -notmatch '\.env\.example$') {
            foreach ($p in $polaNama) { if ($f -match $p) { "berkas rahasia ada di repo: $f"; break } }
        }
        if ($f -match $biner) { continue }
        $path = Join-Path $Root $f
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $path).Length -gt 2MB) { continue }
        $isi = [System.IO.File]::ReadAllText($path)
        foreach ($k in $polaIsi.Keys) {
            if ($isi -match $polaIsi[$k]) { "$k di ${f} (isinya sengaja tidak ditampilkan)" }
        }
    }
}

Cek 'version-code' 'versionCode lebih besar dari unggahan terakhir' {
    $kode = [int](Baca-Manifest).DocumentElement.GetAttribute('versionCode', $ns)
    $terakhir = Baca-Angka 'versi-terakhir.txt'
    if ($kode -le $terakhir) { "versionCode $kode tidak lebih besar dari yang terakhir diunggah ($terakhir)" }
}

# ---------------------------------------------------------------- Laporan

Write-Host ''
Write-Host "Cek rilis Habitflow ($Root)"
foreach ($h in $hasil) {
    if ($h.Masalah.Count -eq 0) {
        Write-Host "[LULUS] $($h.Id) - $($h.Judul)" -ForegroundColor Green
    } else {
        Write-Host "[GAGAL] $($h.Id) - $($h.Judul)" -ForegroundColor Red
        foreach ($m in $h.Masalah) { Write-Host "        $m" -ForegroundColor Red }
    }
}
$gagal = @($hasil | Where-Object { $_.Masalah.Count -gt 0 })
Write-Host ''
if ($gagal.Count -eq 0) {
    Write-Host "Semua $($hasil.Count) pemeriksaan lulus." -ForegroundColor Green
    exit 0
}
Write-Host "$($gagal.Count) dari $($hasil.Count) pemeriksaan gagal. JANGAN unggah AAB ini." -ForegroundColor Red
exit 1
