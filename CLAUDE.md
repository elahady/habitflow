# Habitflow

Aplikasi Android untuk memantau habit harian dan to-do kecil, dengan tampilan
kontribusi ala GitHub. Detail rancangan ada di [docs/rancangan.md](docs/rancangan.md).

## Status Pengembangan

_Diperbarui 21 Oktober 2026 (butir MCP saja; sisanya 17 Oktober). **Direkonstruksi dari `git log` dan dokumen repo, belum diverifikasi pemilik** — koreksi butir yang keliru. Potret keadaan sekarang, bukan log: ubah di tempat setiap selesai pekerjaan nyata (skill `catat-progres`); riwayat ada di `git log`._

Catatan progres per fitur ada di [docs/concept.md](docs/concept.md) (aturan di bagian "Catat progres di docs/concept.md" di bawah); bagian ini hanya penunjuk dan ringkasan.

### Sudah selesai
- Versi web live di habitflow.roziqrizal.com; **deploy produksi terakhir 16 Oktober 2026** (jadwal, libur, angka EOD, Tugas Rumah) dan perbaikan dropdown Tugas Rumah, dicatat di [docs/uji-deploy-16-okt.md](docs/uji-deploy-16-okt.md). Uji di HP: Tugas Rumah berhasil, alarm minum berbunyi (commit terakhir 2026-10-16).
- Ringkasan pagi email lewat Claude ke kartu "Ringkasan dari Claude" (baru dipakai pemilik sendiri).
- **MCP Habitflow dengan login OAuth (tahap 34) selesai di kode dan tes (21 Oktober 2026), belum di-deploy.** Skill `habitflow-morning-routine` belum diubah dan masih memakai token REST; ubah hanya setelah MCP live. Langkah deploy dan serah terima: bagian "Pembaruan 21 Oktober 2026" di [docs/concept.md](docs/concept.md) dan "MCP dan OAuth" di `server/DEPLOY.md`.

### Belum selesai / berikutnya
1. **Android belum dirilis ke Play Store**; persiapan ada di [docs/rilis-play-store.md](docs/rilis-play-store.md) dan [docs/uji-tertutup-dan-play-console.md](docs/uji-tertutup-dan-play-console.md).
2. Kalender masih menyusul ke versi web (menurut halaman Karya di roziqrizal.com).
3. Daftar tugas turunan lain: lihat [docs/concept.md](docs/concept.md) dan [docs/visi-super-app.md](docs/visi-super-app.md).

## Baca dulu sebelum mengerjakan apa pun

- [docs/concept.md](docs/concept.md): tahapan pembangunan, kriteria selesai per
  tahap, dan edge case. Ikuti urutan tahapnya, jangan melompat. **Sesi baru: mulai dari
  bagian "Status terkini dan serah terima"** (cari judulnya di berkas itu): apa yang sudah
  live, apa yang belum dicoba di perangkat, langkah berikutnya, dan cara menyiapkan PC baru.
- [docs/visi-super-app.md](docs/visi-super-app.md): arah besar Habitflow sebagai asisten
  harian pribadi (jadwal, waktu sholat, follow-up kerja, kesehatan). Dibaca sebelum
  membahas tahap 17 ke atas.
- [docs/rilis-play-store.md](docs/rilis-play-store.md): gerbang rilis Play Store, inventaris data, dan tes penjaga. Dibaca sebelum menyentuh login, skema database, izin manifest, atau persiapan rilis. Inventaris data diperbarui di commit yang sama dengan perubahan tabel atau izin.
- [docs/operasional-server.md](docs/operasional-server.md) dan [docs/insiden-data.md](docs/insiden-data.md): backup, pemantauan, memutar kunci, dan prosedur kebocoran data. Dibaca sebelum menyentuh server, backup, `APP_KEY`, atau penanganan insiden. Skrip di `server/ops/` diuji dengan `bash server/ops/uji-ops.sh` setiap kali diubah.
- [docs/rancangan.md](docs/rancangan.md): aturan fitur (habit, to-do, level,
  streak, layar). Kalau ada konflik dengan `concept.md`, `rancangan.md` yang menang
  dan `concept.md` diperbarui.

## Catat progres di docs/concept.md

- **Setiap menyelesaikan satu langkah kerja** (kode, keputusan, atau hasil
  verifikasi) - sekecil apa pun - update status langkah/tahap terkait di
  `docs/concept.md` (ganti jadi ✅, tambah catatan "belum dikerjakan"/"belum
  diverifikasi", atau catat keputusan baru) **sebelum lanjut ke langkah
  berikutnya**, bukan ditumpuk di akhir sesi.
- Tujuannya: sesi mana pun (termasuk setelah `/clear`, atau Claude/mesin lain)
  bisa baca `docs/concept.md` saja dan tahu persis apa yang sudah selesai, apa
  yang masih jalan, dan apa langkah berikutnya - tanpa re-derive dari git log
  atau tanya ulang ke user.
- Update docs ini **masuk ke commit yang sama** dengan kode yang diselesaikan
  (atau commit terpisah kecil tepat setelahnya), ikut aturan Git di bawah -
  jangan nunggu "nanti didokumentasikan belakangan".

## Aturan Git

- **Setiap perubahan langsung di-commit lalu di-push** ke `origin main`, tanpa
  menunggu diminta. Berlaku untuk kode, dokumentasi, dan konfigurasi.
- Satu perubahan logis = satu commit. Jangan menumpuk beberapa perubahan jadi
  satu commit besar di akhir sesi.
- Stage file satu per satu (`git add <file>`), jangan `git add -A` atau `git add .`.
- Pesan commit dalam bahasa Indonesia, ringkas, dan menjelaskan apa yang berubah.
- Operasi destruktif (`git push --force`, `git reset --hard`, menghapus branch)
  wajib konfirmasi dulu.
- Setiap commit ditutup dengan baris:
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`

## Build

- JDK 17 dipakai untuk Gradle. Di mesin ini: `C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot`.
- Android SDK di `C:/Android/Sdk` (ada di `local.properties`, tidak di-commit).
- Build dan install debug dari terminal, bukan Android Studio:
  `JAVA_HOME=... ./gradlew.bat installDebug --console=plain`
- **Kalau user minta "build dan taruh di folder personal"** (atau sejenisnya): jalankan
  `./gradlew.bat :app:assembleDebug --console=plain -q`, lalu salin `app/build/outputs/apk/debug/app-debug.apk` ke
  Google Drive `G:/My Drive/01-Personal/` (di Git Bash: `/g/My Drive/01-Personal/`) dengan **satu nama tetap
  `habitflow-debug.apk`, ditimpa setiap build** (`cp -f`), supaya tidak menumpuk sampah. Jangan membuat berkas
  bertanggal atau berakhiran `-b`, `-c`, dst. Tulis jam build di jawaban agar user tahu itu versi terbaru.

## Sebelum mengunggah AAB (wajib)

- Jalankan `powershell -File scripts/cek-semua.ps1`: seluruh tes unit Android lalu `scripts/cek-rilis.ps1`
  (manifest rilis, izin, `targetSdk`, `https`, aturan cadangan, client ID, rahasia di repo, `versionCode`).
  **Kode keluar bukan 0 berarti jangan unggah.** Jangan mem-pipe keluarannya ke `tail`: daemon Gradle menahan pipa.
- Berkas isian pemilik di `scripts/`: `target-sdk-minimum.txt` (cek syarat Play saat rilis), `versi-terakhir.txt`
  (naikkan setelah unggahan berhasil), `izin-disetujui.txt` (ubah bersama inventaris izin), `client-id-uji.txt`.
- Setelah mengubah `cek-rilis.ps1`, jalankan `scripts/uji-cek-rilis.ps1`: ia melanggar tiap pemeriksaan dengan sengaja
  dan memastikan skrip benar-benar gagal. Skrip yang tidak pernah terbukti gagal tidak bisa dipercaya lulus.

## Konvensi

- UI memakai Jetpack Compose dan Material 3.
- Desain (warna, font, jarak, bentuk, komponen) mengikuti
  [docs/design/README.md](docs/design/README.md). Nilai baru di layar harus dicatat di
  sana dulu. Level warna hari mengikuti aturan di `docs/rancangan.md`.
- Habit tetap menjadi prioritas. To-do tidak boleh menaikkan level hari ke 4
  tanpa habit lengkap.
