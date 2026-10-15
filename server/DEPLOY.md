# Deploy — cara kerja dan cara lanjut dari sesi lain

Dokumen ini supaya sesi Claude Code mana pun (atau kamu sendiri manual) bisa lanjut
deploy tanpa perlu re-derive proses dari nol. Ditulis 10 Oktober 2026 setelah Tahap 25-28
dikerjakan.

## Fakta penting

- **Git push ≠ deploy.** Push ke GitHub cuma riwayat/backup kode. Yang bikin perubahan
  tampil di `https://habitflow.roziqrizal.com` adalah proses deploy manual ke VM (lihat
  di bawah).
- **Kode jalan di VM Linux di rumah**, bukan di layanan hosting. VM ini juga menjalankan
  project lain (roziqrizalcom, MySQL bersama). Detail infrastruktur VM (Hyper-V, Cloudflare
  Tunnel, dll) ada di [repo `server`](https://github.com/elahady/build-server) — baca itu
  dulu kalau VM-nya sendiri bermasalah (bukan soal kode habitflow).

## Akses VM

- SSH: `ssh elahady@<ip-vm>` — key-based auth sudah di-setup dari PC Windows ini
  (`~/.ssh/id_ed25519`), tidak perlu password.
- **IP VM bisa berubah** tiap VM restart (DHCP dari Hyper-V Default Switch). Cek IP
  terkini dengan masuk ke VM lewat Hyper-V Manager → Connect, login, jalankan `ip a`,
  lihat baris `inet` di `eth0`. IP terakhir yang diketahui (10 Oktober 2026):
  `172.23.192.247` — **jangan asumsikan ini masih benar**, selalu cek ulang kalau sudah
  lama tidak dipakai.
- Kode project ada di `~/apps/habitflow/` di VM.
- Container MySQL bersama: `shared-mysql` (network Docker `shared-db`).

## Proses deploy (manual, belum ada script otomatis)

**Wajib sebelum langkah apa pun: `php artisan test` di `server/` harus lulus semua** (tahap 31D). Di dalamnya ada penjaga
yang gagal kalau tabel baru belum diputuskan nasibnya saat hapus akun, dan kalau isi permintaan bocor ke log. PHP ada di
`C:\xampp\php`. Jangan deploy kalau ada tes merah, dan jangan menghapus tes supaya hijau.

Setiap ada perubahan kode di `server/`, urutannya:

```bash
# 1. Dari folder server/ di lokal, bikin arsip (exclude yang tidak perlu)
tar -czf /tmp/habitflow-deploy.tar.gz \
  --exclude='vendor' --exclude='node_modules' \
  --exclude='database/database.sqlite' \
  --exclude='.env' --exclude='.env.production' \
  .

# 2. Kirim ke VM
scp /tmp/habitflow-deploy.tar.gz elahady@<ip-vm>:~/apps/habitflow-deploy.tar.gz

# 3. Extract di VM (timpa file lama)
ssh elahady@<ip-vm> "cd ~/apps/habitflow && tar -xzf ../habitflow-deploy.tar.gz && rm ../habitflow-deploy.tar.gz"

# 4. Build image Docker baru
ssh elahady@<ip-vm> "cd ~/apps/habitflow && docker compose build"

# 5. Recreate container (supaya pakai image baru + env terbaru)
ssh elahady@<ip-vm> "cd ~/apps/habitflow && docker compose up -d --force-recreate"

# 6. Jalankan migrasi kalau ada migration baru
ssh elahady@<ip-vm> "docker exec habitflow php artisan migrate --force"
```

**Migrasi `drop_api_tokens` (31L) menolak jalan kalau masih ada snapshot tanpa akun.** Perintah klaim
`habitflow:claim-legacy` sudah dihapus dari kode ini, jadi kalau produksi masih punya snapshot token 19B yang belum
diklaim: deploy dulu rilis server `b96bc51` atau sebelumnya, jalankan
`docker exec habitflow php artisan habitflow:claim-legacy <email> --token=<nama> --dry-run` lalu tanpa `--dry-run`
untuk tiap HP (pemilik, istri), baru deploy rilis ini. Pesan kegagalan migrasi menyebut jumlah snapshotnya; tabel dan
datanya tidak disentuh kalau migrasi menolak.

**Kenapa belum ada script otomatis**: proses ini baru stabil setelah beberapa kali
trial-error (lihat catatan di `docs/concept.md` tiap tahap). Layak dibuatkan
`scripts/deploy.sh` begitu polanya benar-benar tidak berubah lagi — belum dilakukan per
10 Oktober 2026.

## Backup, pemantauan, dan insiden (tahap 31K)

Skrip di `ops/` (backup, latihan pemulihan, pemantau) ikut terkirim bersama deploy karena ada di dalam `server/`. Cara memasang, memulihkan,
memutar `APP_KEY`, dan prosedur kebocoran data ada di [docs/operasional-server.md](../docs/operasional-server.md) dan
[docs/insiden-data.md](../docs/insiden-data.md). Setelah mengubah skrip: `bash ops/uji-ops.sh`.

## Email dan antrean (tahap 31B)

Verifikasi email dan lupa kata sandi mengirim kode 6 angka. Email dikirim lewat **antrean
database**, jadi ada dua proses: container `habitflow` (web) dan `habitflow-queue` (service
`queue` di `docker-compose.yml`, menjalankan `queue:work`). Tanpa yang kedua, email tertahan
di tabel `jobs` dan tidak pernah terkirim.

- Variabel di `.env.production`: `QUEUE_CONNECTION=database`, `MAIL_*` untuk Resend lewat
  SMTP (contoh lengkap di `.env.example`), `OWNER_EMAIL`, `MAIL_DAILY_LIMIT`. Kunci API
  Resend hanya ada di `.env.production` (VM dan salinan lokal), tidak di git.
- Domain pengirim `mail.roziqrizal.com` harus diverifikasi di Resend (SPF, DKIM) dan DMARC
  dipasang di DNS sebelum email sungguhan dikirim. **Belum dikerjakan per 12 Oktober 2026**,
  lihat langkah yang belum dicentang di 31B (`docs/concept.md`).
- Setelah mengubah `.env.production` atau kode, restart juga worker:
  `docker compose up -d --force-recreate` sudah mengulang keduanya. `queue:work` menyimpan
  kode di memori, jadi tanpa recreate worker masih memakai kode lama.
- Email gagal terkirim tiga kali masuk tabel `failed_jobs`. Lihat dengan
  `docker exec habitflow php artisan queue:failed`.
- `composer require resend/resend-laravel` **tidak dipakai**: Resend dipanggil lewat SMTP
  bawaan Laravel sehingga tidak ada paket baru (composer juga tidak ada di PC ini).

## File kredensial — TIDAK ada di git

- `.env.production` (APP_KEY, kredensial database, kredensial Google OAuth) **sengaja
  tidak di-commit** (gitignored). Isinya cuma ada di dua tempat: file lokal di komputer
  ini, dan `~/apps/habitflow/.env.production` di VM.
- **Kalau file lokal hilang**: tarik salinannya dari VM dulu sebelum kerja lagi —
  `scp elahady@<ip-vm>:~/apps/habitflow/.env.production server/.env.production` — jangan
  generate ulang `APP_KEY` baru (itu akan bikin semua data terenkripsi dengan key lama
  tidak terbaca).
- Isi lengkap variabel yang dibutuhkan ada di `.env.example` (placeholder kosong, aman
  di git) — pakai itu sebagai referensi struktur.

## Kalau composer butuh paket baru (socialite, livewire, dll)

PHP/Composer **tidak terinstall di PC Windows ini** — semua `composer require` harus
dijalankan di VM lewat container sementara:

```bash
ssh elahady@<ip-vm> "docker run --rm -v ~/apps/habitflow:/app -w /app --user root serversideup/php:8.3-fpm-nginx composer require <paket>"
```

Lalu **segera** tarik `composer.json`/`composer.lock` balik ke lokal SEBELUM sync file
lain ke VM lagi (kalau tidak, sync berikutnya bisa menimpa balik composer.json yang baru
diupdate - ini pernah kejadian, lihat catatan tahap 25 di `docs/concept.md`):

```bash
scp elahady@<ip-vm>:~/apps/habitflow/composer.json server/composer.json
scp elahady@<ip-vm>:~/apps/habitflow/composer.lock server/composer.lock
```

## Hal-hal yang pernah bikin error (ringkasan — detail lengkap di `docs/concept.md`)

- Livewire full-page component (`Route::get('/', Component::class)`) butuh
  `->layout('components.layout')` eksplisit di method `render()` — tidak otomatis pakai
  `<x-layout>` biasa.
- `bootstrap/app.php` perlu `$middleware->trustProxies(at: '*')` karena traffic masuk
  lewat Cloudflare Tunnel (cloudflared di VM yang sama) - tanpa ini Laravel generate URL
  `http://` bukan `https://`.
- Kolom `remember_token` wajib ada di tabel `users` kalau pakai `Auth::login($user,
  remember: true)`.
- Folder `storage/framework/sessions`, `storage/framework/views` tidak ada di repo (cuma
  dibuat runtime oleh Laravel) - harus dibuat eksplisit di `Dockerfile` kalau storage
  di-mount sebagai Docker volume kosong.

## Catatan: folder `~/apps/habitflow` di VM berisi sampah (ditemukan 11 Oktober 2026)

Folder `~/apps/habitflow/` di VM berisi sekitar 1.500 file `.dll`/sistem Windows (±1,1 GB,
tanggal Jul-Sep, folder diubah 10 Oktober 05:04) selain kode aplikasi. Kemungkinan besar sisa
`tar` yang dijalankan dari folder yang salah (mis. `C:\Windows\System32`) pada deploy
sebelumnya. Tidak merusak aplikasi (deploy 11 Oktober berjalan normal), tapi membuat konteks
`docker compose build` jauh lebih besar dan lambat. **Belum dibersihkan** - butuh
persetujuan dulu karena menghapus file di VM. Pastikan `pwd` adalah `server/` sebelum
menjalankan `tar` di langkah 1 (cek isi arsip dengan `tar -tzf ... | head`).
