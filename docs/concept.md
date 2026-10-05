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

## Tahap 13-16: Usulan pengembangan (diskusi dulu, belum dikoding)

Keempat tahap di bawah ini dicatat 5 Oktober 2026. **Jangan dikoding sebelum dibahas
dan diputuskan bersama.** Setelah keputusan diambil, aturan fiturnya ditulis dulu di
`docs/rancangan.md` (dan `docs/design/README.md` untuk tampilan), lalu tahap ini diberi
kriteria "Selesai jika" seperti tahap lain.

## Tahap 13: Status bar menyatu dengan warna layar (selesai, 5 Oktober 2026)

**Masalah sekarang:** status bar (bar notifikasi di atas) berwarna abu-abu, tidak
mengikuti latar app. Di mode gelap terlihat makin kontras. Splash screen juga tetap
terang walau pilihan tampilan Gelap.

**Keputusan (5 Oktober 2026):**
- App memakai **edge-to-edge**: digambar sampai ke belakang status bar, sehingga warna
  status bar otomatis sama dengan latar layar. Tidak memakai warna status bar manual.
- **Bar gesture bawah** ikut warna bar navigasi app (`surfaceContainer`), tidak hitam lagi.
- **Ikon status bar** (jam, baterai) mengikuti pilihan tampilan di app: gelap di tema
  terang, terang di tema gelap. Bukan mengikuti pengaturan HP.
- **Splash screen** mengikuti pilihan tampilan di app untuk Android 12+. Di Android 8-11
  splash mengikuti tema HP karena batasan sistem, jadi kilasan singkat masih mungkin
  kalau pilihan app berbeda dengan tema HP.
- Isi layar tidak boleh tertutup status bar atau bar gesture. Jarak dari Scaffold tetap
  dipakai, dan bottom sheet detail hari tetap di atas bar gesture.

**Selesai jika:**
- Di keempat tab, status bar berwarna sama dengan latar layar, baik di tema terang
  maupun gelap, dan ikon status bar terbaca.
- Bar gesture bawah berwarna sama dengan bar navigasi.
- Mengganti pilihan tampilan di Tentang langsung mengubah warna ikon status bar.
- Dengan HP terang dan pilihan Gelap, splash di Android 12+ tampil gelap.
- Tidak ada teks atau tombol yang tertutup status bar, bar gesture, atau keyboard
  (dialog tambah to-do dan tambah habit).

**Hasil verifikasi di emulator (Android 16, HP tema terang):** keempat kriteria pertama
terpenuhi di tema terang dan gelap. Splash tampil gelap saat pilihan Gelap. Layar Hari ini
sempat punya Scaffold kedua yang membuat jarak status bar ganda, dan sudah dihapus. Input
teks hanya ada di `AlertDialog` (jendela terpisah), jadi tidak tertutup keyboard. Splash di
Android 8-11 belum diuji di perangkat.

## Tahap 14: Habit wajib bisa diatur sendiri (selesai, 5 Oktober 2026)

**Aturan sebelumnya:** habit wajib ditentukan saat seed (Sholat 5 waktu dan Baca
Al-Quran), tidak bisa dihapus, dan tidak bisa diubah statusnya.

**Keputusan (5 Oktober 2026):**
- Habit mana pun bisa dijadikan wajib atau tidak wajib dari layar Kelola habit.
- Arti wajib tetap: **tidak bisa dihapus** dan **tampil paling atas** dengan label
  "Wajib". Untuk menghapus, matikan dulu status wajibnya.
- Wajib **tidak** mengubah aturan level dan streak. Unit test domain tidak berubah.
- Toggle berupa **switch "Wajib" di setiap kartu** Kelola habit.
- Menyalakan wajib langsung berlaku. **Mematikan wajib meminta konfirmasi** lewat
  dialog, karena setelah itu habit bisa dihapus.
- Habit baru dibuat tidak wajib. Dua habit seed tetap wajib sebagai nilai awal.
- Kolom `isMandatory` sudah ada, jadi tidak perlu migrasi skema. Cukup query update
  di DAO dan aksi di repository dan ViewModel.

**Selesai jika:**
- Switch wajib tampil di semua kartu Kelola habit dan mencerminkan status sekarang.
- Menyalakan wajib pada habit biasa: label "Wajib" muncul, tombol Hapus hilang, dan
  habit pindah ke kelompok atas di layar Hari ini dan Kelola habit.
- Mematikan wajib memunculkan dialog. Batal membuat status tetap wajib. Setuju membuat
  tombol Hapus muncul lagi dan habit kembali ke urutan biasa.
- Habit yang sedang wajib tetap ditolak dihapus di repository, walau dipanggil langsung.
- Riwayat centang, level, dan streak tidak berubah saat status wajib diganti.
- Status wajib tetap setelah app ditutup dan dibuka lagi.

**Hasil verifikasi di emulator:** "Tanpa minuman manis" dinyalakan wajib, lalu naik ke
kelompok atas dan tombol Hapus hilang. "Sholat 5 waktu" dimatikan: Batal membuat status
tetap wajib, Lepas membuatnya pindah ke urutan biasa dengan tombol Hapus. Setelah app
ditutup paksa, urutan di Hari ini ikut berubah, centang hari ini tetap, dan skor tetap
4/9 Level 1. Penolakan hapus di repository tidak diubah (`deleteIfNotMandatory`).

## Tahap 15: Daily scrum di dalam app (dibatalkan, diganti tahap 19)

> **Ditinjau ulang 5 Oktober 2026.** Konsep di bawah ini tidak jadi dikoding. Kebutuhan
> sebenarnya adalah mencatat target kerja dan follow-up secepat mungkin, dengan daily scrum
> dan EOD dibangun dari daftar follow-up. Lihat `docs/visi-super-app.md` dan tahap 19.
> Bagian di bawah disimpan sebagai riwayat diskusi.


**Keputusan (5 Oktober 2026).** Aturan fitur lengkap ada di `docs/rancangan.md` bagian
Daily scrum.

| Pertanyaan | Keputusan |
|---|---|
| Untuk siapa | Pribadi, disimpan lokal, bisa dibagikan sebagai teks |
| Isi | Tiga pertanyaan klasik: Kemarin, Hari ini, Hambatan |
| Hubungan dengan to-do | Terisi otomatis sekali dari to-do, lalu bisa diedit. To-do tidak berubah |
| "Kemarin" kalau kemarin kosong | Hari terakhir yang punya to-do selesai atau daily scrum |
| Level dan streak | Tidak berpengaruh |
| Letak | Kartu di layar Hari ini, di bawah To-do. Tap membuka layar isi daily scrum |
| Riwayat | Di bottom sheet detail hari Kontribusi, di bawah habit dan to-do |
| Edit hari lama | Tidak bisa, hanya hari ini |
| Bagikan | Tombol "Bagikan" membuka share sheet Android dengan teks rapi |
| Pengingat | Ada. Diatur di Tentang (switch dan jam), awalnya mati. Hanya muncul kalau belum diisi |

**Rencana teknis:**
- Tabel baru `daily_scrums`: `date` (ISO, primary key), `yesterdayDate` (tanggal sumber
  "Kemarin", boleh null), `yesterday`, `today`, `blockers` (teks), `updatedAt`.
- Database naik dari versi 1 ke 2 dengan `Migration` yang membuat tabel. **Jangan**
  memakai `fallbackToDestructiveMigration`, karena data habit dan to-do harus tetap.
- Isian otomatis dan pencarian "hari terakhir yang ada isinya" ditulis di `domain/`
  sebagai Kotlin murni dengan unit test, seperti aturan inti lain.
- Pengingat memakai `AlarmManager` (alarm tidak presisi, tanpa izin exact alarm) dan
  `BroadcastReceiver`, tanpa dependency baru. Alarm dijadwalkan ulang setelah HP
  dinyalakan ulang (`BOOT_COMPLETED`) dan saat jam atau zona waktu berubah. Saat alarm
  berbunyi, receiver memeriksa database: kalau daily scrum hari ini sudah ada, notifikasi
  dilewati.
- Izin `POST_NOTIFICATIONS` (Android 13+) diminta saat switch pengingat dinyalakan.
  Kalau ditolak, switch kembali mati dengan pesan singkat.
- Tap notifikasi membuka app langsung ke layar daily scrum hari ini.

**Selesai jika:**
- Update dari versi lama (database versi 1) tidak menghapus habit, riwayat, dan to-do.
- Membuka daily scrum hari ini pertama kali: Kemarin dan Hari ini terisi dari to-do
  sesuai aturan, Hambatan kosong. Label Kemarin menyebut tanggal sumbernya.
- Senin dengan Sabtu-Minggu kosong mengambil "Kemarin" dari Jumat.
- Isi bisa diedit dan tetap tersimpan setelah app ditutup. Mengubah to-do setelah itu
  tidak mengubah daily scrum.
- Kartu di Hari ini menunjukkan "Belum diisi" atau ringkasan isi.
- Detail hari Kontribusi menampilkan daily scrum hari itu (hanya baca), atau tidak
  menampilkan apa-apa kalau tidak ada.
- Tombol Bagikan membuka share sheet dengan format teks yang disepakati.
- Pengingat: mati secara default. Dinyalakan meminta izin notifikasi. Notifikasi muncul
  di jam yang dipilih kalau belum diisi, dan tidak muncul kalau sudah diisi. Tetap
  berjalan setelah emulator di-restart.
- Level, streak, dan unit test domain yang ada tidak berubah.

## Tahap 16: Kotak heatmap lebih mudah ditekan (diputuskan, belum dikoding)

**Masalah:** kotak heatmap 9dp dengan jarak 3dp, jauh di bawah target sentuh nyaman (sekitar
48dp). Tap di celah juga diabaikan, sehingga hanya sekitar 56% area grid yang merespons.

**Keputusan (5 Oktober 2026): tekan lalu geser (scrubbing).**
- **Mulai:** tahan jari sekitar 0,3 detik di heatmap sampai terasa getar, lalu geser ke
  segala arah, termasuk naik-turun antar hari. Gesekan tanpa menahan tetap untuk scroll
  layar Kontribusi, jadi tidak bentrok.
- **Selama geser:** tooltip di atas jari menunjukkan tanggal dan level kotak di bawah jari
  ("Rabu, 23 Sep · Level 3", atau "Selesai"/"Belum" untuk heatmap per habit). Kotak itu
  diberi bingkai. Tooltip tidak keluar dari tepi layar.
- **Getar halus** saat mode geser aktif dan setiap pindah ke kotak lain, mengikuti
  pengaturan getar HP.
- **Lepas jari:** membuka bottom sheet detail hari untuk kotak terakhir. Lepas di luar grid,
  atau di kotak yang tidak digambar (hari depan, sebelum habit dibuat), berarti batal.
- **Tap biasa tetap berfungsi**, dan tap di celah dihitung ke kotak terdekat.
- Berlaku untuk heatmap gabungan dan per habit, karena keduanya memakai `HeatmapGrid`.
- Ukuran kotak dan jumlah minggu tidak berubah. Node aksesibilitas per kotak tetap.

Ide lain yang dibahas tapi tidak dipilih: tombol hari sebelum dan sesudah di detail hari,
dan memperbesar kotak (mengurangi jumlah minggu atau harus digulir ke samping).

**Selesai jika:**
- Tap di celah antar kotak membuka hari terdekat. Tap di kotak tetap seperti sebelumnya.
- Gesekan cepat tanpa menahan menggulir layar Kontribusi seperti biasa.
- Tahan lalu geser memunculkan tooltip yang mengikuti jari, berganti tanggal setiap pindah
  kotak, dengan getar halus.
- Melepas jari di kotak membuka detail hari yang benar. Melepas di luar grid tidak membuka
  apa pun.
- Di tepi kiri dan kanan grid, tooltip tetap terlihat utuh.
- Berfungsi di heatmap gabungan dan per habit, di tema terang dan gelap.

## Tahap 17-24: HabitFlow sebagai asisten harian (diskusi dulu, belum dikoding)

Arah besarnya ada di [visi-super-app.md](visi-super-app.md). Setiap tahap dibahas dan
diputuskan dulu, lalu aturannya ditulis di `rancangan.md`, baru dikoding. Keputusan yang
sudah diambil saat menyusun visi dicatat di tiap tahap.

### Tahap 17: Jadwal harian dan dashboard
Template blok waktu hari kerja (jam tetap atau berpatokan waktu sholat), layar dashboard
"sekarang dan berikutnya", dan notifikasi tetap di status bar.
Belum dibahas: bentuk dashboard (tab baru atau menggantikan Hari ini), cara mengedit
template, jadwal akhir pekan.

### Tahap 18: Waktu sholat dan alarm Subuh
Sudah diputuskan: mesin **Ephemeris Al Hasib offline** (rumus Al-Kaukaba, Astronomy Engine
sebagai dependency), lokasi **GPS dengan opsi manual**, semua waktu sholat masuk jadwal,
alarm Subuh − 15 menit. Hasil divalidasi terhadap app Al-Kaukaba.
Belum dibahas: suara alarm, tunda alarm, pengingat adzan per waktu bisa dimatikan atau tidak.

### Tahap 19: Catat cepat, follow-up kerja, daily scrum dan EOD
Menggantikan tahap 15. Sudah diputuskan: **follow-up kerja terpisah dari to-do pribadi**
(follow-up tanpa batas, punya tanggal tindak lanjut dan orang terkait, tidak memengaruhi
level), catat cepat versi pertama lewat **tombol + di semua layar**, daily scrum 08.00 dan
EOD 16.00 dibangun dari follow-up, bisa dibagikan lewat share sheet.
Belum dibahas: bentuk layar follow-up, status follow-up, cara Inbox dirapikan.

### Tahap 20: Pengingat kerja
Sudah diputuskan: minum air **setiap 60 menit** di jam kerja dengan tombol "Sudah minum"
(8 gelas mencentang "Air putih 2 liter"), break **setiap 90 menit**, digabung kalau
berdekatan. Mengikuti aturan tiga tingkat notifikasi di visi.

### Tahap 21: Kesehatan
Langkah dari Health Connect (target 8.000, mencentang "Jalan kaki"), berat badan dan BMI,
tensi. Belum dibahas: pedoman kategori tensi, perangkat langkah, frekuensi pencatatan.

### Tahap 22: Kalender dan acara rutin
Acara berulang (misalnya meeting setiap 2 minggu), pengingat −15 menit, kemungkinan
membaca kalender HP.

### Tahap 23: Asupan makan
Mulai sederhana: catat makan dan porsi, bukan hitung kalori.

### Tahap 24: Asisten AI
Opsional dan paling akhir karena butuh internet.

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
