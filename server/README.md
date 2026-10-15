# Server cadangan Habitflow

Server Laravel kecil untuk **menyimpan cadangan data Habitflow dari satu HP** (tahap 19B). Tidak ada
akun, web, atau akses dari laptop: HP mengirim satu berkas JSON (snapshot) dan bisa mengunduhnya lagi
saat ganti HP. Kontrak API dan format snapshot ada di [`../docs/concept.md`](../docs/concept.md)
(tahap 19B).

- Laravel 12, PHP 8.2 atau lebih baru. SQLite untuk metadata, berkas snapshot di `storage/app/private`.
- Login dengan **token akun** (`Authorization: Bearer <token>`, didapat lewat `POST /api/v1/auth/login` atau `auth/google`).
  Snapshot terikat ke akun sejak tahap 31E; token per-HP tahap 19B tidak lagi diterima. Hanya hash SHA-256 yang disimpan.
- Isi snapshot **dienkripsi** dengan `APP_KEY` sebelum ditulis ke disk. 14 snapshot terakhir disimpan.
- Tanpa antrean, cron, Redis, atau layanan lain. Satu permintaan per sinkron, jadi ringan untuk VPS yang
  sama dengan Al-Kaukaba.

## Peringatan penting: cadangkan `APP_KEY`

Snapshot hanya bisa dibuka dengan `APP_KEY` di `.env`. **Kalau kunci hilang atau diganti, semua snapshot
tidak bisa dibaca lagi.** Simpan salinan `.env` di tempat aman di luar VPS (misalnya pengelola kata
sandi). Cadangkan juga folder `storage/app/private/snapshots` dan `database/database.sqlite` kalau mau
menjaga riwayat 14 snapshot saat server dipindah.

## Pasang di VPS

Prasyarat: PHP 8.2+ dengan ekstensi `openssl`, `mbstring`, `pdo_sqlite`, `fileinfo`, Composer, dan web
server (Nginx atau Apache) dengan HTTPS. Habitflow menolak alamat `http://` (kecuali emulator saat debug).

```bash
git clone https://github.com/elahady/habitflow.git
cd habitflow/server
composer install --no-dev --optimize-autoloader
cp .env.example .env
php artisan key:generate          # catat dan cadangkan APP_KEY (lihat peringatan di atas)
touch database/database.sqlite
php artisan migrate --force
```

Akun dibuat lewat app atau web (daftar atau Google). Kalau ini pemasangan lama yang punya snapshot token 19B,
klaim ke akun pemilik: lihat bagian "Klaim snapshot lama" di bawah.

Sesuaikan `APP_URL` di `.env`. Arahkan web server ke folder `server/public`, misalnya Nginx:

```nginx
server {
    server_name habitflow.contoh.com;          # subdomain pilihan Anda
    root /var/www/habitflow/server/public;
    index index.php;
    client_max_body_size 6m;                   # batas snapshot 5 MB

    location / { try_files $uri /index.php?$query_string; }
    location ~ \.php$ {
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $realpath_root$fastcgi_script_name;
        fastcgi_pass unix:/run/php/php8.2-fpm.sock;   # sesuaikan dengan PHP-FPM di VPS
    }
    location ~ /\.(?!well-known) { deny all; }
}
```

Pasang sertifikat HTTPS (misalnya `certbot --nginx -d habitflow.contoh.com`). Pastikan `storage` dan
`bootstrap/cache` bisa ditulis oleh pengguna PHP-FPM.

Uji dari mana saja:

```bash
curl -H "Authorization: Bearer <token-akun>" https://habitflow.contoh.com/api/v1/ping
# {"ok":true}
```

## Snapshot lama dan tabel api_tokens (tahap 31E dan 31L)

Snapshot yang dikirim sebelum tahap 31 terikat ke token per-HP (`api_tokens`), bukan akun. Tabel dan perintah klaimnya
(`habitflow:claim-legacy`) dihapus di 31L. Kalau produksi masih punya snapshot lama yang belum diklaim, **klaim dulu dari rilis
server sebelum migrasi `drop_api_tokens`** (commit `b96bc51` atau sebelumnya), karena migrasi itu menolak jalan selama ada
snapshot tanpa akun. Lihat DEPLOY.md.

## Pengembangan dan tes

```bash
composer install
cp .env.example .env && php artisan key:generate
touch database/database.sqlite && php artisan migrate
php artisan test                  # tes fitur API (SQLite di memori)
php artisan serve --host=0.0.0.0  # untuk emulator Android: http://10.0.2.2:8000
```

Emulator Android (build debug) boleh memakai `http://10.0.2.2:8000`; build release hanya menerima HTTPS.

## Batas dan kode galat

| Kode | Arti |
|---|---|
| 401 | Token kosong atau salah |
| 413 | Snapshot lebih dari 5 MB |
| 422 | Isi bukan JSON snapshot yang sah (`schemaVersion` bilangan bulat, `data` objek) |
| 429 | Lebih dari 60 permintaan per menit per token, atau 10 percobaan token salah per menit per IP |
