# Konsep dan Tahapan Pembangunan HabitFlow

Written for: Roziq dan Claude, sebagai peta kerja. Rancangan fitur ada di
[rancangan.md](rancangan.md). Dokumen ini menjelaskan **urutan membangun**,
dengan kriteria selesai di tiap tahap.

## Prinsip kerja

1. **Logika dulu, tampilan belakangan.** Aturan level, streak, dan pindah
   to-do adalah inti app. Dibuat sebagai Kotlin murni dengan unit test, sebelum
   menyentuh UI.
2. **Satu tahap = satu commit (atau beberapa commit kecil).** Setiap tahap
   selesai harus lolos build dan langsung di-push, sesuai [CLAUDE.md](../CLAUDE.md).
3. **Habit selalu di atas to-do.** Di semua layar, habit tampil lebih dulu dan
   lebih besar.
4. **Offline dan lokal.** Tidak ada akun, server, atau sinkronisasi di versi ini.

## Tahap 0: Persiapan (sudah selesai)

- [x] Repo GitHub `elahady/habitflow` dibuat, branch `main`.
- [x] Gradle wrapper 8.9, AGP 8.5.2, Kotlin 2.0.21, Compose, Room sudah dikonfigurasi.
- [x] Rancangan fitur ditulis di `docs/rancangan.md`.
- [x] `CLAUDE.md` berisi aturan commit dan push.
- [x] **Verifikasi build kosong**: `./gradlew.bat assembleDebug` berhasil tanpa kode
  (`MainActivity` belum ada, jadi tahap ini menambah `AndroidManifest.xml` dan
  activity kosong dulu).

**Selesai jika:** `assembleDebug` berhasil dan APK terpasang di emulator.

## Tahap 1: Manifest dan activity kosong

- `app/src/main/AndroidManifest.xml` dengan `MainActivity` sebagai launcher.
- `res/values/strings.xml` berisi nama app "HabitFlow".
- `res/values/themes.xml` dengan tema dasar tanpa ActionBar.
- `MainActivity` menampilkan teks sederhana dengan `setContent`.

**Selesai jika:** app terbuka di emulator dan menampilkan teks "HabitFlow".

## Tahap 2: Model data dan Room

Tiga tabel sesuai `rancangan.md`:

- `Habit` (`habits`): `id`, `name`, `createdAt` (tanggal ISO), `sortOrder`, `isMandatory` (boolean).
- `HabitEntry` (`habit_entries`): `habitId`, `date` (ISO). Primary key gabungan
  `(habitId, date)`. Satu baris = habit selesai di tanggal itu.
- `Todo` (`todos`): `id`, `title`, `date` (ISO), `done`, `createdAt`.

Komponen:

- `HabitDao`, `HabitEntryDao`, `TodoDao` dengan Flow untuk observasi.
- `HabitDatabase` (versi 1).
- Seed habit awal (sembilan habit dari rancangan, dua di antaranya wajib:
  Sholat 5 waktu dan Baca Al-Quran) lewat `RoomDatabase.Callback` saat database
  pertama kali dibuat.

**Selesai jika:** database terbentuk, tujuh habit awal muncul, dan query untuk
satu tanggal mengembalikan data yang benar (diuji dengan instrumented test
atau dicek manual lewat Logcat).

## Tahap 3: Logika inti sebagai Kotlin murni

Dibuat di paket `domain/` tanpa dependensi Android, supaya bisa diuji dengan JUnit.

### 3a. Hitung level hari

Fungsi `dayLevel(totalHabits, doneHabits, doneTodos): Int`:

- `totalHabits == 0` → 0.
- `doneHabits == 0` → 0.
- `doneHabits * 2 < totalHabits` (kurang dari 50%, dihitung tanpa pembagian) → 1.
- `doneHabits < totalHabits` → 2.
- Semua habit selesai dan `doneTodos >= 2` → 4.
- Semua habit selesai dan `doneTodos < 2` → 3.

Pastikan batas 50% dihitung benar. Contoh: 7 habit, 3 selesai = 42% → level 1,
4 selesai = 57% → level 2.

### 3b. Hitung streak

Fungsi `currentStreak(days: Map<LocalDate, Boolean>, today: LocalDate): Int`,
dengan `Boolean` menyatakan apakah semua habit selesai di hari itu.

- Mulai dari hari ini. Kalau hari ini belum lengkap, mulai dari kemarin tanpa
  memutus streak.
- Hitung mundur selama hari berturut-turut lengkap.

### 3c. Pindah to-do

Fungsi `carryOver(todos, today)`: to-do dengan `date < today` dan `done == false`
dipindah ke `today`. To-do yang sudah selesai tidak dipindah.

### 3d. Batas to-do

- Maksimal 5 to-do per hari. Fungsi `canAddTodo(count): Boolean` menolak jika
  sudah 5.

**Selesai jika:** unit test untuk semua kasus di atas lulus, termasuk edge case:

- Nol habit.
- Habit tepat 50%.
- Level 4 dengan 1 to-do selesai (tetap 3).
- Streak dengan hari ini belum selesai.
- Streak putus di tengah.
- To-do selesai tidak dipindah.

## Tahap 4: Repository dan ViewModel

- `HabitRepository` membungkus DAO dan menyediakan operasi:
  - `toggleHabit(habitId, date)`
  - `addHabit`, `renameHabit`, `deleteHabit`
  - `addTodo(title, date)` (menolak jika sudah 5)
  - `toggleTodo(id)`, `deleteTodo(id)`
- `TodayViewModel` menyediakan `StateFlow` untuk:
  - daftar habit beserta status hari ini,
  - daftar to-do hari ini,
  - skor hari ini dan streak.
- Saat ViewModel dibuat, jalankan `carryOver` untuk hari ini, dan ulangi setiap kali
  tanggal berganti (`DayClock`: diperbarui di `onResume` dan saat broadcast
  `DATE_CHANGED`, `TIME_SET`, atau `TIMEZONE_CHANGED`).
- `ContributionViewModel` menyediakan data heatmap sampai 53 minggu (gabungan dan per habit),
  supaya grid bisa menampilkan minggu sebanyak lebar layar.

**Selesai jika:** perubahan di database langsung memperbarui `StateFlow`.
Diverifikasi dengan unit test memakai repository palsu.

## Tahap 5: Tema dan warna

- `ui/theme/Color.kt`: palet hijau sage dari homepage roziqrizal.com
  (`--color-primary` dan turunannya di `resources/css/app.css`).
- Level 0-4 sebagai gradasi sage. Level 0 abu-abu netral.
- `ui/theme/Theme.kt`: Material 3 light dan dark.
- `ui/theme/Type.kt`: font sistem dulu. Kalau Anda mau font Manrope dan Libre
  Caslon seperti di homepage, itu tahap terpisah.

**Selesai jika:** tema terlihat benar di light dan dark mode.

## Tahap 6: Komponen heatmap

- `HeatmapGrid` memakai `Canvas`: 7 baris (Senin sampai Minggu), minimal 26 kolom
  (minggu) dan ditambah sampai lebar kartu terisi (maksimal 53), ukuran kotak 9dp dengan
  jarak 3dp. Grid di tengah supaya ruang kiri dan kanan seimbang.
- Kolom terakhir adalah minggu ini. Hari setelah hari ini tidak digambar.
- Input: fungsi `levelFor(date): Int`.
- Tap pada kotak memanggil `onDayClick(date)`.
- Legenda "Less ... More" di bawah grid.

**Selesai jika:** grid muncul dengan benar di lebar layar 360dp, dan tap pada
kotak mengembalikan tanggal yang tepat.

## Tahap 7: Layar Hari ini

- Header: tanggal, skor "Habit x/y · To-do x/5", dan streak.
- Bagian **Habit**: checklist, item lebih besar, tap untuk centang.
- Bagian **To-do**: daftar kecil di bawah habit, dengan tombol tambah.
  Tombol nonaktif saat sudah 5.
- Dialog tambah to-do dengan satu field judul.
- Empty state untuk to-do kosong: teks ringan, bukan pesan error.

**Selesai jika:** centang habit dan to-do langsung terlihat, dan batas 5 berfungsi.

## Tahap 8: Layar Kontribusi

- Heatmap gabungan (level hari) di bagian atas.
- Heatmap per habit di bawahnya, satu baris per habit.
- Tap kotak membuka bottom sheet berisi:
  - tanggal,
  - daftar habit yang selesai dan yang belum,
  - daftar to-do hari itu dan statusnya.
- Tampilkan streak saat ini dan streak terpanjang.

**Selesai jika:** detail hari yang diklik sesuai dengan data.

## Tahap 9: Layar Kelola habit

- Daftar habit dengan tombol tambah.
- Tap nama untuk ubah nama.
- Hapus dengan dialog konfirmasi. Menghapus habit juga menghapus riwayat
  centangnya, dan dialog harus menyebut itu.
- Urutan habit bisa diubah (tahap opsional, bisa ditunda).

**Selesai jika:** tambah, ubah, dan hapus berfungsi dan perubahan langsung
tampil di layar Hari ini.

## Tahap 10: Navigasi dan layar Tentang

- Bottom navigation: **Hari ini**, **Kontribusi**, **Habit**, **Tentang**.
- Layar Tentang: nama app, versi, dan teks "Dibuat oleh Roziq Rizal".

**Selesai jika:** keempat tab bisa dibuka dan kembali ke tab sebelumnya dengan benar.

## Tahap 11: Kualitas dan verifikasi (sudah selesai, 4 Oktober 2026)

- Tes unit untuk `domain/` lulus semua.
- Verifikasi manual di emulator, dengan skenario:
  1. Centang semua habit → level 3.
  2. Tambah 2 to-do selesai → level 4.
  3. Ubah tanggal perangkat ke hari berikutnya → to-do yang belum selesai pindah.
  4. Hapus habit → riwayat hilang dari heatmap.
- Layar bisa dipakai di lebar 360dp tanpa scroll horizontal.
- `contentDescription` di semua ikon dan kotak heatmap.
- Build release (`assembleRelease`) berhasil. Signing dibahas di tahap 12.

**Selesai jika:** semua skenario di atas berhasil dan dicatat di commit message.

## Tahap 12: Rilis (sebagian selesai, 5 Oktober 2026)

- [x] Ikon app. Dibuat oleh `docs/design/ikon/generate.py`, aturannya di
  `docs/design/README.md`.
- [x] Keystore dan signing release. Keystore ada di `~/.habitflow/`, di luar repo.
  `assembleRelease` menghasilkan APK yang sudah ditandatangani.
- [x] README dengan cara build dan screenshot.
- [ ] Play Store listing, bila nanti dibutuhkan. PNG 512 untuk listing sudah ada di
  `docs/design/ikon/ikon-512.png`.

## Daftar pertanyaan terbuka

- Urutan habit bisa diubah atau tidak (tahap 9, opsional).
- Apakah streak terpanjang disimpan permanen atau dihitung ulang dari data
  (saat ini: dihitung ulang).

## Edge case yang harus diuji

- Habit baru ditambahkan di tengah minggu. Heatmap sebelum tanggal dibuat
  tidak dihitung sebagai gagal.
- Habit dihapus. Riwayatnya ikut hilang dan skor hari dihitung ulang.
- Habit wajib tidak bisa dihapus, baik dari layar Habit maupun dari Kontribusi.
- Ganti zona waktu. Tanggal dihitung dari zona waktu perangkat.
- Pergantian hari saat app terbuka. Carry-over harus berjalan ulang.
- Lebih dari 5 to-do lewat jalur lain (misalnya carry-over). Batas tetap dijaga.
