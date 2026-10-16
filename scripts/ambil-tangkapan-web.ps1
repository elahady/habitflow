# Ambil tangkapan layar web Habitflow dari DATA CONTOH untuk portofolio (roziqrizal.com/karya/habitflow).
#
# Alur: tangkapan-web/render.php mengisi database SQLite SEMENTARA dengan data contoh (pengguna "Demo", tanggal dibekukan
# ke Jumat 16 Oktober 2026) dan merender empat halaman ke HTML tanpa server; Edge headless memotretnya; hasilnya dipotong
# jadi gambar 1280x640 bernama seperti di roziqrizalcom/public/images/karya/. Database server/ yang asli tidak disentuh.
#
# Pemakaian (dari folder mana pun):
#   powershell -File scripts/ambil-tangkapan-web.ps1
#   powershell -File scripts/ambil-tangkapan-web.ps1 -Keluaran D:\foto
#
# Keluaran default: build/tangkapan-web/ (di-gitignore). Salin keenam *.jpg ke repo situs (public/images/karya/); penanda
# ?v= pada URL gambar berubah sendiri mengikuti isi berkasnya, jadi tidak perlu mengubah kode situs.
# Kalau tampilan web berubah, ubah juga data contoh di tangkapan-web/render.php lalu jalankan ulang.

param(
    [string]$Keluaran = '',
    [string]$Php = 'C:\xampp\php\php.exe',
    [string]$Edge = ''
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $Keluaran) { $Keluaran = Join-Path $repo 'build\tangkapan-web' }
New-Item -ItemType Directory -Force $Keluaran | Out-Null
$Keluaran = (Resolve-Path $Keluaran).Path

if (-not (Test-Path -LiteralPath $Php)) { throw "PHP tidak ditemukan di $Php (isi -Php)" }
if (-not $Edge) {
    $kandidat = @(
        'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
        'C:\Program Files\Microsoft\Edge\Application\msedge.exe'
    )
    $Edge = $kandidat | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $Edge -or -not (Test-Path -LiteralPath $Edge)) { throw 'Edge tidak ditemukan (isi -Edge)' }

# 1. Render HTML dari data contoh.
& $Php (Join-Path $PSScriptRoot 'tangkapan-web\render.php') $Keluaran
if ($LASTEXITCODE -ne 0) { throw "render.php gagal (kode $LASTEXITCODE)" }

# 2. Foto tiap halaman (jendela 1280x2400; tema terang karena render.php memasang data-theme="light").
$halaman = 'beranda', 'habit', 'kerja', 'tugas-rumah'
foreach ($h in $halaman) {
    $png = Join-Path $Keluaran "$h.png"
    $url = 'file:///' + ((Join-Path $Keluaran "$h.html") -replace '\\', '/')
    Remove-Item -LiteralPath $png -ErrorAction SilentlyContinue
    # Edge menulis galat internalnya ke stderr walau berhasil; yang menentukan hanya berkas hasilnya.
    $ErrorActionPreference = 'Continue'
    & $Edge --headless=new --disable-gpu --hide-scrollbars --force-color-profile=srgb --virtual-time-budget=8000 `
        --window-size=1280,2400 "--screenshot=$png" $url 2>$null | Out-Null
    $ErrorActionPreference = 'Stop'
    if (-not (Test-Path -LiteralPath $png)) { throw "Edge tidak menghasilkan $png" }
}

# 3. Potong jadi 1280x640 (nama gambar = nama di situs; y = tepi atas potongan di foto penuh).
Add-Type -AssemblyName System.Drawing
$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$mutu = New-Object System.Drawing.Imaging.EncoderParameters(1)
$mutu.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]85)

$potongan = @(
    @{ Sumber = 'beranda';     Y = 0;    Nama = 'habitflow-web-beranda' },
    @{ Sumber = 'beranda';     Y = 850;  Nama = 'habitflow-web-kesehatan' },
    @{ Sumber = 'habit';       Y = 0;    Nama = 'habitflow-web-habit' },
    @{ Sumber = 'habit';       Y = 1010; Nama = 'habitflow-web-kontribusi' },
    @{ Sumber = 'kerja';       Y = 0;    Nama = 'habitflow-web-follow-up' },
    @{ Sumber = 'tugas-rumah'; Y = 0;    Nama = 'habitflow-web-tugas-rumah' }
)
foreach ($p in $potongan) {
    $img = [System.Drawing.Bitmap]::FromFile((Join-Path $Keluaran "$($p.Sumber).png"))
    try {
        $bmp = $img.Clone((New-Object System.Drawing.Rectangle(0, $p.Y, 1280, 640)), $img.PixelFormat)
        $bmp.Save((Join-Path $Keluaran "$($p.Nama).jpg"), $jpeg, $mutu)
        $bmp.Dispose()
    } finally { $img.Dispose() }
}

Remove-Item -LiteralPath (Join-Path $Keluaran 'tangkapan.sqlite') -ErrorAction SilentlyContinue
Write-Host "Selesai. Gambar ada di $Keluaran :"
Get-ChildItem $Keluaran -Filter 'habitflow-web-*.jpg' | ForEach-Object { '  {0}  {1:N0} byte' -f $_.Name, $_.Length }
Write-Host 'Periksa gambarnya, lalu salin ke roziqrizalcom/public/images/karya/ dan commit di repo situs.'
