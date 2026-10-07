# Server cadangan HabitFlow

Server Laravel kecil untuk **menyimpan cadangan data HabitFlow dari satu HP** (tahap 19B). Tidak ada
akun, web, atau akses dari laptop: HP mengirim satu berkas JSON (snapshot) dan bisa mengunduhnya lagi
saat ganti HP. Kontrak API dan format snapshot ada di [`../docs/concept.md`](../docs/concept.md)
(tahap 19B).

- Laravel 12, PHP 8.2 atau lebih baru. SQLite untuk metadata, berkas snapshot di `storage/app/private`.
- Login dengan **token pribadi** (`Authorization: Bearer <token>`). Hanya hash SHA-256 yang disimpan.
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
server (Nginx atau Apache) dengan HTTPS. HabitFlow menolak alamat `http://` (kecuali emulator saat debug).

```bash
git clone https://github.com/elahady/habitflow.git
cd habitflow/server
composer install --no-dev --optimize-autoloader
cp .env.example .env
php artisan key:generate          # catat dan cadangkan APP_KEY (lihat peringatan di atas)
touch database/database.sqlite
php artisan migrate --force
php artisan habitflow:token hp    # token tampil sekali: salin ke app (Tentang, Sinkron ke server)
```

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
curl -H "Authorization: Bearer <token>" https://habitflow.contoh.com/api/v1/ping
# {"ok":true}
```

## Token

```bash
php artisan habitflow:token hp            # buat token bernama "hp" (tampil sekali)
php artisan habitflow:token hp --revoke   # cabut, misalnya kalau HP hilang
```

Satu token per HP. Mencabut token menghapus token itu beserta semua snapshot-nya (baris database dan
berkas terenkripsinya).

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
