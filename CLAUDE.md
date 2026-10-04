# HabitFlow

Aplikasi Android untuk memantau habit harian dan to-do kecil, dengan tampilan
kontribusi ala GitHub. Detail rancangan ada di [docs/rancangan.md](docs/rancangan.md).

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

## Konvensi

- UI memakai Jetpack Compose dan Material 3.
- Warna mengikuti palet hijau sage dari homepage roziqrizal.com. Level warna
  hari mengikuti aturan di `docs/rancangan.md`.
- Habit tetap menjadi prioritas. To-do tidak boleh menaikkan level hari ke 4
  tanpa habit lengkap.
