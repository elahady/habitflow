# Operasional server Habitflow

Tahap 31K, Gerbang D di [rilis-play-store.md](rilis-play-store.md). Semua pengguna bergantung pada satu VM di rumah, jadi dokumen ini
menjawab: bagaimana data dicadangkan, bagaimana tahu kalau ada yang rusak, dan bagaimana pulih. Cara deploy kode ada di
[server/DEPLOY.md](../server/DEPLOY.md); infrastruktur VM (Hyper-V, Cloudflare Tunnel, MySQL bersama) ada di repo `server`
(`DOCKER-INVENTORY.md`). Prosedur kebocoran data: [insiden-data.md](insiden-data.md).

**Status (15 Oktober 2026): skrip dan perintah sudah ditulis dan diuji dengan tiruan, tetapi BELUM dipasang di VM.** Yang butuh
keputusan atau akun dari pemilik ditandai ☐. Sebelum bagian mana pun di sini dianggap selesai, buktinya (hasil latihan pemulihan
nyata, peringatan uji yang sampai ke ponsel) harus ada di sini atau di `concept.md` 31K.

## 1. Apa yang ada di repo

| Berkas | Fungsi |
|---|---|
| `server/ops/backup.sh` | Dump MySQL + berkas snapshot, dikompres, dienkripsi (gpg AES256), dikirim ke luar VM (rclone), diverifikasi ukurannya, lalu rotasi (30 hari di tujuan, 7 hari di VM). Gagal berarti kode keluar bukan 0, ping gagal, dan notifikasi ke ponsel. |
| `server/ops/restore-drill.sh` | Latihan pemulihan: unduh backup terbaru, cek checksum, dekripsi, pulihkan ke **MySQL sementara** (bukan `shared-mysql`), bandingkan jumlah baris tiap tabel dengan manifest, cek jumlah berkas snapshot. |
| `server/ops/monitor.sh` | Tiap 10 menit: container, situs (di VM dan lewat internet), disk, kesegaran backup dan latihan, antrean email. Peringatan ke ponsel saat masalah muncul, diulang tiap 6 jam, dan dikabari saat pulih. |
| `server/ops/ops.env.example` | Contoh pengaturan (`~/.habitflow-ops.env` di VM, tidak di-commit). |
| `server/ops/uji-ops.sh` | Uji ketiga skrip dengan tiruan Docker, rclone, dan curl; 62 pemeriksaan, tiap pelanggaran dicoba. Jalankan setiap kali skrip diubah: `bash server/ops/uji-ops.sh`. |
| `php artisan habitflow:revoke-all-sessions` | Cabut semua token app dan putuskan semua sesi web (menahan insiden). |
| `php artisan habitflow:reencrypt-snapshots` | Tulis ulang snapshot dengan `APP_KEY` baru (memutar kunci, bagian 5). |
| `CountSentMail` (di aplikasi) | Email ke pemilik saat email terkirim mencapai 80% batas harian (`MAIL_DAILY_LIMIT`). Tidak diduplikasi di `monitor.sh`. **Hanya jalan kalau `OWNER_EMAIL` terisi; di `.env.production` lokal 15 Oktober 2026 kosong, jadi periksa juga di VM dan isi.** |

Yang dibackup: seluruh database `habitflow` di `shared-mysql` (~210 MB) dan berkas `storage/app/private/snapshots` di container
`habitflow` (snapshot terenkripsi dengan `APP_KEY`). **Backup tanpa `APP_KEY` yang sama tidak berguna untuk snapshot** (bagian 4).

## 2. Memasang di VM (sekali)

☐ **Keputusan 1, tujuan backup di luar VM.** VM dan PC host satu rumah, satu listrik, satu kemungkinan pencurian atau banjir, jadi
folder di PC host **bukan** "di luar VM" yang dimaksud gerbang D. Pilih layanan di luar rumah yang didukung rclone (Google Drive
pribadi, Backblaze B2, Cloudflare R2, dan lain-lain). Data di sana sudah terenkripsi gpg sebelum keluar dari VM, jadi layanan
tidak perlu dipercaya. Usulan: yang gratis atau murah dan bisa Anda akses dari ponsel untuk memeriksa.

☐ **Keputusan 2, pemberitahuan.** Skrip memakai dua jalur berbasis URL, jadi tidak terikat satu vendor:
- **ntfy** (`ALERT_URL=https://ntfy.sh/<topik-acak-panjang>`): peringatan sampai ke ponsel lewat aplikasi ntfy. Topik acak berfungsi sebagai kata sandi.
- **Healthchecks.io** (atau setara): tiga "check" (backup, latihan, pemantau). Berbunyi kalau ping **tidak datang**, yang satu-satunya cara
  tahu bahwa VM atau cron sendiri mati. Atur penerimanya ke email dan aplikasi ponsel.
- Opsional: UptimeRobot atau setara menembak `https://habitflow.roziqrizal.com/up` dari luar. `monitor.sh` sudah memeriksa URL publik dari
  dalam VM; pemeriksa luar menambah satu hal, yaitu tahu kalau seluruh rumah offline.

Langkah di VM:

```bash
sudo apt install -y rclone gnupg                # gnupg biasanya sudah ada
rclone config                                   # buat remote, lalu uji: rclone lsd <remote>:
umask 077; openssl rand -base64 33 > ~/.habitflow-backup-pass
cp ~/apps/habitflow/ops/ops.env.example ~/.habitflow-ops.env && chmod 600 ~/.habitflow-ops.env   # isi BACKUP_REMOTE, URL ping, ALERT_URL
chmod +x ~/apps/habitflow/ops/*.sh
sudo cp ~/apps/habitflow/ops/logrotate-habitflow /etc/logrotate.d/habitflow     # log cron dirotasi mingguan
```

**Segera salin `~/.habitflow-backup-pass` ke pengelola kata sandi dan satu salinan offline** (bagian 4). Kata sandi yang hanya ada di VM
hilang bersama VM, dan semua backup jadi tumpukan byte acak.

Jadwal (`crontab -e`, pengguna `elahady`; `ops/` ikut terkirim oleh langkah deploy karena ada di dalam `server/`):

```cron
30 3 * * *  /home/elahady/apps/habitflow/ops/backup.sh        >> /home/elahady/backups/habitflow/backup.log  2>&1
*/10 * * * * /home/elahady/apps/habitflow/ops/monitor.sh      >> /home/elahady/backups/habitflow/monitor.log 2>&1
15 4 1 * *  /home/elahady/apps/habitflow/ops/restore-drill.sh  >> /home/elahady/backups/habitflow/drill.log   2>&1
0 5 * * *   docker exec habitflow php artisan queue:prune-failed --hours=48
```

Baris terakhir membersihkan `failed_jobs` (berisi kode verifikasi asli kalau pengiriman gagal, lihat inventaris data). Akibatnya `monitor.sh`
memperingatkan email gagal kirim paling lama 48 jam, lalu mengabari "pulih".

Latihan memakai container MySQL sementara berbatas memori 450 MB. VM hanya 2 GB RAM dan menjalankan project lain, jadi skrip menolak
berjalan kalau memori tersedia di bawah 500 MB. Jalankan latihan pertama manual di jam sepi dan perhatikan memori (`free -m`).

## 3. Pemulihan

**Latihan (tanpa menyentuh produksi):** `ops/restore-drill.sh` (atau `--local`, atau `--file <berkas.tar.gpg>`). Lulus berarti: checksum cocok,
dekripsi berhasil, dump terpulihkan ke MySQL kosong, jumlah baris tiap tabel ada di antara hitungan sebelum dan sesudah dump, dan jumlah berkas
snapshot sama dengan manifest. **Backup yang belum pernah lolos latihan dianggap belum ada.**
☐ Bukti latihan pertama (tanggal, keluaran skrip): ______

**Pemulihan nyata (VM hilang atau data rusak).** Perkiraan waktu: sekitar satu jam kalau kata sandi dan `.env.production` tersedia.
1. VM baru dengan Docker, stack `mysql` dan network `shared-db` (lihat repo `server`), lalu deploy kode (`server/DEPLOY.md`).
2. Pasang `.env.production` **dengan `APP_KEY` yang sama** (dari salinan aman, bagian 4).
3. Unduh dan buka backup (`rclone copyto`, `gpg --decrypt`, `tar -xf`), lalu:
   ```bash
   zcat db.sql.gz | docker exec -i -e MYSQL_PWD=<root> shared-mysql mysql -uroot habitflow     # database kosong, user habitflow sudah dibuat
   docker run --rm -v habitflow_storage:/s -v "$PWD":/b alpine sh -c "mkdir -p /s/app/private && tar -xzf /b/files.tar.gz -C /s/app/private && chown -R $(docker run --rm habitflow:latest id -u www-data) /s/app/private"
   ```
   (pemilik berkas harus `www-data` milik image app; perintah `id -u www-data` di atas mengambilnya dari image, jadi tidak perlu menebak angkanya.)
4. `docker compose up -d`, lalu `docker exec habitflow php artisan migrate --force` (aman kalau tidak ada migrasi baru).
5. Uji: masuk dengan akun peninjau, dan ambil snapshot (`GET /api/v1/snapshot/latest`) dari satu akun.
6. Data setelah backup terakhir hilang (sampai 24 jam). Pengguna yang membuka app akan mengirim ulang snapshot dari HP, jadi kehilangan nyata terbatas pada
   data web yang belum disinkron ke perangkat. Catat ini kalau terjadi, dan beri tahu pengguna kalau berdampak.

## 4. Rahasia

Hilang berarti data tidak bisa dibuka. Semuanya disimpan di luar repo **dan** punya salinan di dua tempat terpisah (pengelola kata sandi + satu
media offline di tempat berbeda dari PC):

| Rahasia | Kenapa | Di mana sekarang |
|---|---|---|
| `APP_KEY` | Mengenkripsi semua snapshot. **Hilang berarti semua snapshot tidak terbaca**, backup sekalipun. | `.env.production` di VM |
| `.env.production` utuh | Kredensial database, `MAIL_PASSWORD` (Resend), Google client secret | VM (`~/apps/habitflow/`) |
| `~/.habitflow-backup-pass` | Membuka semua backup | VM saja, ☐ **belum ada salinan** |
| `~/.config/rclone/rclone.conf` | Akses ke tujuan backup | VM saja, ☐ **belum ada salinan** |
| Kredensial root MySQL | Pulihkan database | file compose `~/infra/mysql/` di VM |
| Kunci SSH (`~/.ssh/id_ed25519` di PC) | Masuk ke VM | PC Windows |
| Token Cloudflare Tunnel | Situs tersambung ke internet | VM (service `cloudflared`) |
| Kunci unggah Play (31L) | Menerbitkan pembaruan app | belum dibuat |

☐ Isi kolom "salinan" setelah dibuat. Jangan menulis nilainya di sini.

## 5. Memutar `APP_KEY` tanpa kehilangan snapshot

Dipakai kalau kunci diduga bocor, atau sebagai tindakan menahan di [insiden-data.md](insiden-data.md). Laravel mendekripsi dengan kunci lama
selama masih ada di `APP_PREVIOUS_KEYS`, jadi tidak ada saat snapshot tak terbaca.

1. `ops/backup.sh` dulu, supaya ada titik kembali.
2. Buat kunci: `docker exec habitflow php artisan key:generate --show` (hanya mencetak, tidak mengubah apa pun).
3. Di `.env.production`: `APP_PREVIOUS_KEYS=<kunci lama>` dan `APP_KEY=<kunci baru>`. Recreate **kedua** container (`docker compose up -d --force-recreate`: app dan queue).
4. `docker exec habitflow php artisan habitflow:reencrypt-snapshots --dry-run`, lalu tanpa `--dry-run`. Ulangi sampai keluar "0 snapshot ditulis ulang, N sudah memakai
   kunci saat ini, 0 gagal". Kode keluar bukan 0 berarti ada snapshot yang tidak terbaca; **jangan hapus kunci lama** sampai itu dijelaskan.
5. Sesi web yang memakai cookie terenkripsi kunci lama tetap terbaca selama kunci lama ada di `APP_PREVIOUS_KEYS`. Untuk memaksa semua masuk ulang (kasus insiden): `docker exec habitflow php artisan habitflow:revoke-all-sessions --force`.
6. **Backup lama berisi snapshot dengan kunci lama.** Simpan kunci lama di tempat aman selama masih ada backup sebelum rotasi (minimal 30 hari), karena hanya itu yang
   membukanya. Hapus `APP_PREVIOUS_KEYS` dari `.env.production` setelah langkah 4 bersih; kunci lama cukup tinggal di brankas.

## 6. Keputusan tertulis

☐ **Menunggu persetujuan pemilik** (ditulis sebagai usulan karena ini keputusan risiko, bukan teknis).

**Enkripsi disk VM: usulan tidak mengenkripsi disk di dalam VM.** Alasan: disk terenkripsi (LUKS) butuh kata sandi saat boot, dan VM ini harus bangkit sendiri setelah
listrik padam. Tanpa orang di rumah, server tetap mati sampai ada yang mengetik kata sandi, padahal pengguna sungguhan bergantung padanya. Yang menutup risiko pencurian
fisik: (a) data paling sensitif (snapshot kesehatan dan lokasi) sudah terenkripsi di tingkat aplikasi dengan `APP_KEY`; (b) backup terenkripsi sebelum keluar dari VM;
(c) ☐ **periksa dan nyalakan BitLocker di drive PC host tempat berkas VHDX VM berada**, yang melindungi seluruh isi VM kalau PC dicuri dan tidak mengganggu boot VM karena kuncinya
ada di TPM PC. **Risiko yang diterima:** tabel MySQL (akun, habit, to-do, follow-up, jadwal, tim) berupa teks biasa di dalam VM, jadi orang yang sudah masuk ke VM dapat membacanya. **Per 15 Oktober 2026 data kesehatan (berat, tensi, langkah, makan) belum punya tabel**: hanya ada di dalam snapshot yang terenkripsi `APP_KEY` (lihat `DataInventory`). **Keputusan ini harus ditinjau ulang saat tahap 29-30 membuat tabel kesehatan**, karena sejak itu data kesehatan berupa teks biasa di MySQL, dan pertimbangkan enkripsi kolom di tingkat aplikasi untuk tabel itu. Tinjau ulang juga kalau pengguna melewati lingkaran keluarga dan teman.

**Akses admin database.** Per inventaris 11 Oktober 2026, `shared-mysql` tidak mem-publish port apa pun (3306 dan 33060 hanya di network Docker `shared-db`), dan VM berada di belakang NAT
tanpa port yang diteruskan (akses publik hanya lewat Cloudflare Tunnel). Admin masuk lewat SSH dengan kunci lalu `docker exec`. ☐ Sebelum 31M, verifikasi ulang dari VM dan catat hasilnya:
`sudo ss -tlnp` (tidak ada `0.0.0.0:3306`), `docker ps --format '{{.Names}} {{.Ports}}'`, dan bahwa Portainer (port 9000/9443 terbuka di LAN rumah, tidak di tunnel) memakai kata sandi kuat
atau dimatikan kalau tidak dipakai. `ufw` sengaja tidak dipasang (ROADMAP); layak dipertimbangkan sebagai lapisan tambahan.

## 7. Jadwal pembaruan keamanan

Usulan, ☐ disetujui pemilik. Pekerjaan bulanan (tanggal 1, setelah latihan pemulihan lulus supaya ada titik kembali yang terbukti):

1. **OS VM:** `sudo apt update && sudo apt upgrade`; pasang `unattended-upgrades` (patch keamanan harian otomatis) dan jadwalkan reboot saat sepi kalau kernel berubah.
2. **Image Docker:** `docker compose build --pull` untuk habitflow (menarik ulang `serversideup/php:8.3-fpm-nginx` berpatch terbaru) dan `docker compose pull` untuk `mysql:8.4` di stack mysql; recreate, lalu cek `/up`.
3. **Pustaka PHP:** di `server/`: `composer audit` (kerentanan yang diketahui, wajib bersih) dan `composer outdated --direct`; naikkan patch Laravel dan jalankan `php artisan test` sebelum deploy.
4. **Pustaka Android:** cek peringatan dependensi Gradle dan Play Console (SDK bermasalah) tiap bulan.
5. Catat tanggal dan hasil di bagian bawah dokumen ini.

**Kerentanan kritis** (Laravel, PHP, MySQL, `cloudflared`, atau OpenSSH yang diumumkan terkena eksploitasi): ditambal dalam 72 jam sejak diketahui, tidak menunggu jadwal bulanan.

| Tanggal | Yang dikerjakan | Hasil |
|---|---|---|
| | | |
