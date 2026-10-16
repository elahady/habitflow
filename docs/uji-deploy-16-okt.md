# Deploy dan uji di HP: jadwal, libur, dan angka EOD (disusun 16 Oktober 2026)

Daftar langkah untuk menaikkan perubahan 16 Oktober 2026 ke produksi lalu mengujinya di HP. Semuanya sudah di-push ke `origin main`
(sampai commit `e8178b4`) dan lulus tes otomatis (server 394, Android 660), tapi **belum di-deploy dan belum dicoba di perangkat**.
Cara deploy yang umum ada di [server/DEPLOY.md](../server/DEPLOY.md); dokumen ini hanya daftar untuk rilis ini. Rincian tiap fitur ada di
`docs/concept.md`: tahap 29 langkah 4 dan 5, serta "Sisa tahap 33".

## Yang naik ke produksi

- **2 migrasi:** `2026_10_16_120000_create_days_off_table` dan `2026_10_16_130000_add_eod_counts_to_work_days_table`. Keduanya hanya menambah.
- **Endpoint:** `PUT /api/v1/schedule-blocks` (ganti seluruh jadwal), `PUT` dan `GET /api/v1/days-off`, dan `eod_done_count` / `eod_total_count` di
  `PUT /api/v1/work-days/{date}`.
- **Tampilan web:** kartu "Jadwal hari ini" bergaya `hf-card`, keterangan libur, dan angka "X dari Y selesai" di riwayat rekap.
- **Android:** mengirim jadwal, waktu sholat 7 hari, libur 14 hari, dan angka EOD; **Room 11 ke 12** (dua kolom nullable di `work_days`).
- **Tidak ada** paket Composer atau variabel `.env` baru.

## A. Sebelum deploy (di PC dev)

- [ ] `git log -1` menunjukkan `e8178b4` atau lebih baru.
- [ ] Di `server/`: `C:\xampp\php\php.exe artisan test` lulus semua (394). Jangan deploy kalau ada yang merah.
- [ ] Bangun APK debug (lihat C.1); boleh disiapkan sambil deploy berjalan.

## B. Deploy server (dari PC host Hyper-V)

IP VM berubah-ubah dan hanya terjangkau dari PC host.

- [ ] **1. Cari IP VM.** `Get-NetNeighbor -AddressFamily IPv4 | Where InterfaceAlias -match 'Default Switch'` (MAC VM berawalan `00-15-5D`).
  Cocokkan sidik jari sebelum login: `ssh-keyscan -t ed25519 <ip> | ssh-keygen -l -f -` harus `SHA256:iM1PvGgSv/Xk98d3I7fQHsyC4xud25STtq32XoDULC8`.
- [ ] **2. Cadangan dulu** (pola deploy sebelumnya; nama berkas bebas):
  - `mysqldump` database ke `~/backups-habitflow/habitflow-pra-hari-libur-<tanggal>.sql`.
  - Arsip kode lama ke `~/backups-habitflow/kode-pra-hari-libur-<tanggal>.tar.gz`.
  - Tag image berjalan: `docker tag <image-yang-berjalan> habitflow:pra-hari-libur`.
- [ ] **3. Buat arsip dari folder `server/`.** Langkah ini pernah salah folder (arsip `C:\Windows\System32`):
  ```bash
  test -f artisan && test -f docker-compose.yml || { echo "BUKAN folder server/"; exit 1; }
  tar -czf /tmp/habitflow-deploy.tar.gz --exclude='vendor' --exclude='node_modules' \
    --exclude='database/database.sqlite' --exclude='.env' --exclude='.env.production' .
  ls -la /tmp/habitflow-deploy.tar.gz                                         # sekitar 300 KB
  tar -tzf /tmp/habitflow-deploy.tar.gz | grep -ciE '\.(dll|exe)$'            # harus 0
  ```
- [ ] **4. Kirim dan terapkan:** `scp` arsip ke VM, ekstrak di `~/apps/habitflow`, lalu `docker compose build` dan
  `docker compose up -d --force-recreate` (ini sekaligus mengulang worker antrean).
- [ ] **5. Migrasi:** `docker exec habitflow php artisan migrate --force`. Harus menjalankan tepat 2 migrasi di atas tanpa galat.
- [ ] **6. Cek cepat tanpa token.** Rute ada kalau jawabannya 401, bukan 404 atau 405:
  - `curl -s -o /dev/null -w "%{http_code}" https://habitflow.roziqrizal.com/api/v1/ping` harus `200`.
  - `curl -s -o /dev/null -w "%{http_code}" -X PUT https://habitflow.roziqrizal.com/api/v1/days-off` harus `401`.
  - Hal yang sama untuk `-X PUT .../api/v1/schedule-blocks`: harus `401`.
- [ ] **7. Cek web setelah login.** Kartu di beranda berjudul "Jadwal hari ini". Sebelum HP mengirim, isinya "Belum ada jadwal di server…" (normal).

**Rollback:** ekstrak arsip kode lama ke `~/apps/habitflow`, lalu `docker compose up -d --build --force-recreate`. Dump database dipakai hanya
kalau migrasi merusak data; kedua migrasi ini hanya menambah, jadi mestinya tidak perlu.

## C. Uji di HP

### Persiapan

- [ ] **1. Bangun APK debug:** `./gradlew.bat assembleDebug --console=plain` (JDK 17). Kirim lewat Drive seperti `habitflow-debug-2026-10-16-c.apk`,
  atau `installDebug` kalau HP tersambung USB.
- [ ] **2. Pasang sebagai update, jangan uninstall.** Ini langkah paling berisiko: migrasi Room 11 ke 12 belum pernah jalan di perangkat (hanya SQL-nya
  yang sudah dicocokkan dengan skema bangkitan Room). Setelah terpasang, buka app: tidak boleh crash, dan habit, riwayat, serta catatan EOD harus utuh.
- [ ] **3. Sinkron:** tekan "Sinkron sekarang" (jeda otomatis sekitar 1 menit setelah ada perubahan).

### Uji 1: jadwal dan waktu sholat ke web (tahap 29 langkah 4)

- [ ] Muat ulang beranda web. Kartu menampilkan Sekarang/Berikutnya yang sama dengan kartu di HP.
- [ ] Blok yang berpatokan sholat tampil. Kalau muncul "Waktu sholat hari ini belum dikirim", sinkron lagi dan cek B.6.
- [ ] Footer kartu menyebut zona waktu dan waktu sholat terakhir disinkron.
- [ ] Ubah nama satu blok di "Atur jadwal", sinkron, muat ulang web: perubahannya muncul.
- [ ] Tautan habit di blok tidak tampil di web, jadi tidak bisa dicek visual; cukup pastikan sinkron tidak error.

### Uji 2: libur (tahap 29 langkah 5)

- [ ] Di HP, tekan "Hari ini libur" pada hari kerja, lalu sinkron.
- [ ] Di web, blok yang hanya aktif Senin sampai Jumat hilang dan muncul keterangan "Hari ini libur, jadi blok hari kerja disembunyikan".
  Blok yang juga aktif di akhir pekan (misalnya sholat) tetap tampil.
- [ ] Matikan lagi di HP, sinkron, muat ulang web: blok kerja kembali.
- [ ] Kalau ada libur nasional dalam 14 hari ke depan, tanggalnya sama di HP dan di web (cek di tabel `days_off`, lihat bagian D).

### Uji 3: angka EOD "X dari Y" (tahap 33)

- [ ] Siapkan beberapa follow-up yang dikerjakan hari ini, misalnya 3, dan tandai 1 selesai lebih dulu.
- [ ] Buka EOD: pilih Selesai untuk 1 lagi dan Lanjut besok untuk sisanya, lalu Simpan EOD, lalu sinkron.
- [ ] Angka yang dikirim untuk hari ini tidak terlihat di web (rekap hari ini dihitung langsung). Periksa di database:
  `SELECT date, eod_done_count, eod_total_count FROM work_days ORDER BY date DESC LIMIT 3;` harus `2` dan `3`.
- [ ] Besok pagi, di halaman Follow-up pilih tanggal kemarin: judul rekap menulis "2 dari 3 follow-up selesai", dan baris riwayat menulis
  "2 dari 3 selesai".
- [ ] Selesaikan EOD lagi di hari yang sama: tidak error, angka dihitung ulang.
- [ ] Hari-hari sebelumnya (tanpa angka) tetap menulis "N selesai".

### Uji 4: sisa dari catatan sebelumnya (opsional, sekalian)

- [ ] Catat berat dan tensi baru di HP lalu cek muncul di web (tahap 30 langkah 6).
- [ ] Tema gelap dan lipatan "Selengkapnya" di kartu Ringkasan dari Claude.
- [ ] Senin 19 Oktober 2026: routine 07.00 WIB berjalan terjadwal (PC harus menyala).

## D. Kalau ada yang salah

| Gejala | Kemungkinan penyebab |
|---|---|
| Kartu web kosong setelah sinkron | Endpoint `schedule-blocks` belum aktif, jadi HP menerima 404 dan melewatinya. Ulangi B.6, lalu sinkron lagi. |
| App crash saat dibuka setelah update | Migrasi 11 ke 12 gagal. Ambil logcat dan pasang kembali APK lama (versi database 11); data lama tidak disentuh. |
| Blok kerja tidak hilang saat libur | Tanggal libur tidak terkirim atau zona waktu salah. Cek `SELECT * FROM days_off` di MySQL. |
| Angka EOD tidak muncul | Cek `eod_done_count` di `work_days`. Kosong berarti HP belum mengirim, atau APK-nya masih versi lama. |

## E. Setelah selesai

- [ ] Catat hasil tiap uji di `docs/concept.md` (tahap 29 langkah 4 dan 5, serta "Sisa tahap 33"), seperti sesi sebelumnya.
- [ ] Ubah status "belum di-deploy" di tempat yang sama, termasuk butir 6 di "Status terkini dan serah terima".
- [ ] Catat nama berkas cadangan dan tag image yang dibuat di B.2.
