# Deploy — cara kerja dan cara lanjut dari sesi lain

Dokumen ini supaya sesi Claude Code mana pun (atau kamu sendiri manual) bisa lanjut
deploy tanpa perlu re-derive proses dari nol. Ditulis 10 Oktober 2026 setelah Tahap 25-28
dikerjakan. **Diperbarui 18 Oktober 2026**: arsitektur pindah dari VM rumah ke VPS
(lihat `MIGRATION.md` di repo `server`) — bagian "Akses VPS" di bawah sudah disesuaikan,
bagian lain (proses deploy, kredensial) tidak berubah selain path `<ip-vm>` yang sudah
diganti IP statis VPS di contoh perintah.

## Fakta penting

- **Git push ≠ deploy.** Push ke GitHub cuma riwayat/backup kode. Yang bikin perubahan
  tampil di `https://habitflow.roziqrizal.com` adalah proses deploy manual ke VPS (lihat
  di bawah).
- **Kode jalan di VPS IDCloudHost** (Jakarta), bukan layanan hosting managed dan bukan lagi
  VM Hyper-V di rumah (pindah 18 Oktober 2026 setelah motherboard PC rumah rusak — riwayat
  lengkap di `MIGRATION.md` repo `server`). VPS ini juga menjalankan project lain
  (roziqrizalcom, rizqflow, MySQL bersama). Detail infrastruktur VPS (firewall, nginx,
  Cloudflare, dll) ada di [repo `server`](https://github.com/elahady/build-server) — baca
  itu dulu kalau VPS-nya sendiri bermasalah (bukan soal kode habitflow).

## Akses VPS

- **IP**: `103.54.170.198` (statis — beda dari VM lama yang IP-nya dinamis via DHCP
  Hyper-V, catatan "IP bisa berubah tiap restart" sudah **tidak berlaku lagi**).
- SSH: `ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198` — key-based auth,
  **password login dimatikan** di VPS ini (jangan coba kirim/pakai password). Key lokal
  ada di PC Windows ini saja, tidak di repo.
- **Cadangan sebelum deploy**: backup MySQL terjadwal harian (lihat `build-server/CLAUDE.md`)
  di `/home/elahady/backups/mysql/` di VPS. Cadangan kode manual ala `~/backups-habitflow/`
  dari era VM lama **belum ada polanya di VPS baru** — bikin arsip manual dulu kalau mau
  rollback aman untuk perubahan besar.
- Kode project ada di `~/apps/habitflow/` di VPS (path sama dengan VM lama).
- Container MySQL bersama: `shared-mysql` (network Docker `shared-db`).

## Proses deploy (manual, belum ada script otomatis)

**Wajib sebelum langkah apa pun: `php artisan test` di `server/` harus lulus semua** (tahap 31D). Di dalamnya ada penjaga
yang gagal kalau tabel baru belum diputuskan nasibnya saat hapus akun, dan kalau isi permintaan bocor ke log. PHP ada di
`C:\xampp\php`. Jangan deploy kalau ada tes merah, dan jangan menghapus tes supaya hijau.

Setiap ada perubahan kode di `server/`, urutannya:

```bash
# 1. Dari folder server/ di lokal, bikin arsip (exclude yang tidak perlu).
#    PENGAMAN: pastikan cwd benar dan arsipnya kecil. Pada 10 Oktober 2026 satu arsip pernah dibuat dari
#    direktori yang salah dan ±2.400 berkas C:\Windows\System32 (±950 MB) ikut ke VM; baru ketahuan 16 Oktober.
test -f artisan && test -f docker-compose.yml || { echo "BUKAN folder server/"; exit 1; }
tar -czf /tmp/habitflow-deploy.tar.gz \
  --exclude='vendor' --exclude='node_modules' \
  --exclude='database/database.sqlite' \
  --exclude='.env' --exclude='.env.production' \
  .

#    Arsip normal sekitar 300 KB. Lebih dari beberapa MB, atau ada .dll/.exe di dalamnya, berarti salah:
#    ls -la /tmp/habitflow-deploy.tar.gz && tar -tzf /tmp/habitflow-deploy.tar.gz | grep -ciE '\.(dll|exe)$'   # harus 0

# 2. Kirim ke VPS
scp -i ~/.ssh/id_ed25519_vps_jakarta /tmp/habitflow-deploy.tar.gz elahady@103.54.170.198:~/apps/habitflow-deploy.tar.gz

# 3. Extract di VPS (timpa file lama)
ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198 "cd ~/apps/habitflow && tar -xzf ../habitflow-deploy.tar.gz && rm ../habitflow-deploy.tar.gz"

# 4. Build image Docker baru
ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198 "cd ~/apps/habitflow && docker compose build"

# 5. Recreate container (supaya pakai image baru + env terbaru)
# PENTING: container berjalan dengan label compose project "server" (lihat bagian "Hal-hal
# yang pernah bikin error" - insiden 19 Oktober), BUKAN "habitflow". Tanpa -p server, compose
# tidak mengenali container/volume/network yang sudah ada dan membuat set baru yang kosong.
ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198 "cd ~/apps/habitflow && docker compose -p server up -d --force-recreate"

# 6. Jalankan migrasi kalau ada migration baru
ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198 "docker exec habitflow php artisan migrate --force"
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

Service `queue` memakai `healthcheck: disable: true` (16 Oktober 2026): healthcheck bawaan image memeriksa
`curl localhost:8080`, padahal worker tidak punya web server, jadi dulu selalu `unhealthy` walau prosesnya
jalan. Konsekuensinya Docker tidak menandai worker yang hang. Periksa dengan `docker top habitflow-queue`
dan `docker exec habitflow php artisan queue:failed`, dan waspadai tabel `jobs` yang menumpuk.

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
- **Kalau file lokal hilang**: tarik salinannya dari VPS dulu sebelum kerja lagi —
  `scp -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198:~/apps/habitflow/.env.production server/.env.production` — jangan
  generate ulang `APP_KEY` baru (itu akan bikin semua data terenkripsi dengan key lama
  tidak terbaca).
- Isi lengkap variabel yang dibutuhkan ada di `.env.example` (placeholder kosong, aman
  di git) — pakai itu sebagai referensi struktur.

## Kalau composer butuh paket baru (socialite, livewire, dll)

PHP/Composer **tidak terinstall di PC Windows ini** — semua `composer require` harus
dijalankan di VM lewat container sementara:

```bash
ssh -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198 "docker run --rm -v ~/apps/habitflow:/app -w /app --user root serversideup/php:8.3-fpm-nginx composer require <paket>"
```

Lalu **segera** tarik `composer.json`/`composer.lock` balik ke lokal SEBELUM sync file
lain ke VPS lagi (kalau tidak, sync berikutnya bisa menimpa balik composer.json yang baru
diupdate - ini pernah kejadian, lihat catatan tahap 25 di `docs/concept.md`):

```bash
scp -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198:~/apps/habitflow/composer.json server/composer.json
scp -i ~/.ssh/id_ed25519_vps_jakarta elahady@103.54.170.198:~/apps/habitflow/composer.lock server/composer.lock
```

## Hal-hal yang pernah bikin error (ringkasan — detail lengkap di `docs/concept.md`)

- **(19 Oktober 2026) Container berlabel compose project "server", bukan "habitflow".**
  Deploy pertama (18 Oktober, migrasi VM→VPS) sempat dijalankan dengan `docker compose` tanpa
  nama project eksplisit dari dalam struktur lama, jadi Docker memakai nama folder sebagai nama
  project — hasilnya volume `server_storage` dan network `server_default`, bukan
  `habitflow_storage`/`habitflow_default`. (Insiden terkait: deploy pertama **rizqflow** di hari
  yang sama sempat menimpa container `habitflow` selama ±2 menit karena masalah nama project yang
  sama dari arah sebaliknya — lihat `rizqflow/server/DEPLOY.md`.) Dampaknya: `docker compose up
  -d --force-recreate` **tanpa** `-p server` gagal dengan `Conflict: container name "/habitflow"
  already in use` — compose bikin network/volume baru yang kosong lalu nabrak nama container yang
  sudah ada. Fix sementara: selalu pakai `docker compose -p server ...` (sudah dipakai di langkah
  5 di atas). Fix permanen yang belum dikerjakan: tambahkan baris `name: server` di
  `docker-compose.yml` (pola yang sama dipakai `rizqflow/server/docker-compose.yml` dengan
  `name: rizqflow`) supaya tidak bergantung ke orang/Claude mengingat `-p server` tiap deploy.
  **Jangan** ganti ke `name: habitflow` tanpa memindahkan isi volume `server_storage` ke
  `habitflow_storage` dulu (snapshot terenkripsi ada di situ) — kalau cuma ganti nama, deploy
  berikutnya akan memakai volume baru yang kosong dan data lama jadi tidak keakses (bukan hilang,
  cuma "tersembunyi" di volume lama).

- **(19 Oktober 2026) `NGINX_LISTEN_IP_PROTOCOL` wajib `ipv4` di `.env.production`.** Base image
  `serversideup/php:8.3-fpm-nginx` defaultnya listen di IPv4 **dan** IPv6 (`NGINX_LISTEN_IP_PROTOCOL=all`).
  VPS ini kernelnya tidak mendukung IPv6, jadi tiap `docker compose build` yang menarik versi base
  image terbaru bisa bikin nginx gagal start: `nginx: [emerg] socket() [::]:8080 failed (97:
  Address family not supported by protocol)`, container jadi `unhealthy` terus-menerus (healthcheck
  loop tanpa pernah hijau). Fix: pastikan baris `NGINX_LISTEN_IP_PROTOCOL=ipv4` ada di
  `.env.production` sebelum/setelah `docker compose up`. Sama persis dipakai di
  `rizqflow/server/.env.production` (lihat komentar di `rizqflow/server/Dockerfile`).

- **(19 Oktober 2026) `.env.production` sempat hilang dari VPS sekaligus dari PC lokal** —
  penyebab pastinya belum diketahui. Diselamatkan dari environment variable container lama yang
  masih hidup (`docker exec habitflow env`, lalu difilter ke prefix variabel app yang relevan)
  SEBELUM container itu di-recreate — kalau container keburu mati/restart duluan, `APP_KEY` dan
  semua kredensial produksi hilang permanen (data terenkripsi ikut tidak bisa dibaca). Pelajaran:
  anjuran "simpan salinan `.env` di pengelola kata sandi" di bagian atas dokumen ini **belum
  pernah benar-benar dikerjakan** — lakukan sekarang, jangan tunda lagi, supaya kejadian ini tidak
  terulang dengan container yang sudah keburu mati duluan.

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
