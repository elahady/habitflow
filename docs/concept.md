# Konsep dan Tahapan Pembangunan Habitflow

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
4. **Akun wajib.** Setelah login, data harian tetap di HP (offline-first) dan server menjadi sumber bersama.
   *(Sebelum tahap 31: "Offline dan lokal. Tidak ada akun, server, atau sinkronisasi di versi ini" — diganti 11 Oktober 2026, lihat tahap 31.)*

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
- `res/values/strings.xml` berisi nama app "Habitflow".
- `res/values/themes.xml` dengan tema dasar tanpa ActionBar.
- `MainActivity` menampilkan teks sederhana dengan `setContent`.

**Selesai jika:** app terbuka di emulator dan menampilkan teks "Habitflow".

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

## Tahap 16: Kotak heatmap lebih mudah ditekan (selesai, 5 Oktober 2026)

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

**Hasil verifikasi di emulator (Android 16, 1080×2400):**
- Tap di celah antara kolom 0 dan 1 membuka Senin, 9 Maret 2026 (kotak pertama).
- Tahan lalu geser ke kolom 22 baris 3 menampilkan tooltip "Kamis, 13 Agu · Level 0" dengan
  bingkai. Geser satu baris ke bawah lalu lepas membuka Jumat, 14 Agustus 2026.
- Gesekan cepat di atas heatmap menggulir layar seperti biasa.
- Lepas di luar grid tidak membuka apa pun.
- Heatmap per habit menampilkan "Senin, 5 Okt · Selesai". Kotak di tepi kanan membuat tooltip
  digeser ke dalam layar dan tetap utuh.
- Di tema gelap, tooltip dan bingkai berganti warna dan tetap terbaca.
- Unit test `heatmapCellAt` (kotak, celah, tepi, luar grid) lulus.
- Getar tidak bisa dirasakan di emulator, jadi perlu dicoba di HP.

## Tahap 17-24: Habitflow sebagai asisten harian (diskusi dulu, belum dikoding)

Arah besarnya ada di [visi-super-app.md](visi-super-app.md). Setiap tahap dibahas dan
diputuskan dulu, lalu aturannya ditulis di `rancangan.md`, baru dikoding. Keputusan yang
sudah diambil saat menyusun visi dicatat di tiap tahap.

### Tahap 17: Jadwal harian, waktu sholat, dan dashboard (sebagian selesai, 7 Oktober 2026)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Jadwal harian.

| Pertanyaan | Keputusan |
|---|---|
| Letak dashboard | Tab Hari ini jadi dashboard: kartu Sekarang/Berikutnya di atas, timeline bisa dibuka-tutup, lalu habit dan to-do. Tetap 4 tab |
| Cara mengatur jadwal | Editor di app (layar Atur jadwal), diisi awal dari tabel di bawah |
| Akhir pekan | Setiap blok punya hari aktif |
| Blok dicentang | Tidak. Blok bisa ditautkan ke habit, "Sudah" di notifikasi mencentang habit itu |
| Notifikasi tetap | Dari blok pertama sampai batas tidur, bisa dimatikan |
| Waktu sholat | **Masuk tahap ini** (mesin Ephemeris dan lokasi). Alarm Subuh tetap di tahap 18 |
| Hari libur | Tombol "Hari ini libur" mematikan blok hari kerja untuk tanggal itu |
| Blok tumpang tindih | Sekarang = blok aktif yang paling akhir dimulai |

**Rencana teknis:**
- Tabel baru: `schedule_blocks` (nama, jenis patokan jam tetap atau waktu sholat, waktu
  sholat dan selisih menit atau jam tetap, durasi menit, hari aktif sebagai bitmask, tingkat
  notifikasi, urutan), `schedule_block_habits` (tautan blok ke habit), dan `days_off`
  (tanggal libur). Database naik ke versi 2 dengan `Migration`, tanpa destructive migration.
- Mesin waktu sholat di `domain/prayer/` sebagai Kotlin murni: rumus Ephemeris dari
  `EphemerisPrayerCalculator.kt` Al-Kaukaba, posisi matahari dari Astronomy Engine
  (MIT, source disalin ke `io/github/cosinekitty/astronomy/` seperti di Al-Kaukaba, karena artefak Maven tidak ditemukan). Unit test memakai contoh Lamongan 1 Januari 2009 dari
  `rumus-hisab-ephemeris.md`, dan pembanding beberapa tanggal dari app Al-Kaukaba.
- Penentuan Sekarang dan Berikutnya, hari aktif, libur, dan tumpang tindih di `domain/`
  dengan unit test.
- Lokasi: izin lokasi kasar, `LocationManager` bawaan (tanpa Google Play Services), lokasi
  terakhir disimpan. Opsi manual: nama kota dan koordinat.
- Notifikasi blok dan pembaruan notifikasi tetap dijadwalkan dengan `AlarmManager` pada
  setiap batas blok (alarm tidak presisi `setAndAllowWhileIdle`, bisa terlambat beberapa menit; sejak tahap 20,
  sebelumnya `setWindow` 5 menit yang tidak berbunyi saat Doze), dijadwalkan ulang
  setelah restart dan saat jam, zona waktu, atau lokasi berubah. Alarm presisi baru dipakai
  di tahap 18 untuk alarm Subuh.
- Tingkat notifikasi di tahap ini: Pengingat dan Info (lihat visi). Tingkat Alarm menunggu
  tahap 18.

**Jadwal awal** (bisa diubah di Atur jadwal):

| Blok | Mulai | Durasi | Hari | Notifikasi | Habit |
|---|---|---|---|---|---|
| Bangun | Subuh − 15 | 15 mnt | Setiap hari | Pengingat (Alarm di tahap 18) | |
| Jamaah Subuh dan ngaji | Subuh | 60 mnt | Setiap hari | Pengingat | Baca Al-Quran |
| Aktivitas fisik | setelah ngaji | s.d. 06.00 | Setiap hari | Info | Jalan kaki 20 menit |
| Mandi dan prepare | 06.15 | 20 mnt | Sen–Jum | Info | |
| Berangkat | 06.35 | 85 mnt | Sen–Jum | Pengingat | |
| Kerja pagi | 08.00 | 240 mnt | Sen–Jum | Pengingat | |
| Sholat Dzuhur | Dzuhur | 15 mnt | Setiap hari | Pengingat | |
| Makan siang dan istirahat | 12.00 | 60 mnt | Sen–Jum | Info | |
| Kerja sore | 13.00 | 180 mnt | Sen–Jum | Pengingat | |
| Sholat Ashar | Ashar | 15 mnt | Setiap hari | Pengingat | |
| EOD | 16.00 | 60 mnt | Sen–Jum | Pengingat | |
| Pulang | 17.00 | 60 mnt | Sen–Jum | Info | |
| Sholat Maghrib | Maghrib | 15 mnt | Setiap hari | Pengingat | |
| Sholat Isya | Isya | 15 mnt | Setiap hari | Pengingat | Sholat 5 waktu |
| Project personal | 19.00 | 120 mnt | Setiap hari | Info | |
| Batas tidur | 22.00 | — | Setiap hari | Pengingat | Tidur sebelum 22.00 |

Catatan: "Aktivitas fisik" berakhir tepat 06.00, jadi durasinya mengikuti waktu Subuh.
Tautan "Sholat 5 waktu" ke blok Isya (sholat terakhir hari itu) adalah usulan dan bisa
diubah.

**Selesai jika:**
- Update dari database versi 1 tidak menghapus habit, riwayat, dan to-do.
- Unit test waktu sholat lulus untuk contoh Lamongan 1 Januari 2009 (selisih paling banyak
  1 menit dari buku), dan hasil untuk lokasi Roziq beberapa tanggal dicatat terhadap app
  Al-Kaukaba.
- Kartu Sekarang/Berikutnya benar untuk pagi, jam kerja, saat Dzuhur di tengah Kerja pagi,
  malam, dan setelah batas tidur, di hari kerja dan akhir pekan.
- Atur jadwal bisa menambah, mengubah, dan menghapus blok, termasuk patokan waktu sholat,
  hari aktif, dan tautan habit. Perubahan langsung terlihat di dashboard.
- "Hari ini libur" menyembunyikan blok hari kerja hanya untuk hari itu.
- Notifikasi blok muncul paling lambat 5 menit setelah blok mulai, sesuai tingkatnya.
  "Sudah" mencentang habit yang ditautkan.
- Notifikasi tetap hilang setelah batas tidur, muncul lagi di blok pertama besok, dan tetap
  berjalan setelah emulator di-restart.
- Lokasi GPS dan manual sama-sama bekerja. Tanpa izin lokasi, app tetap jalan dengan
  lokasi manual.

**Hasil verifikasi (7 Oktober 2026, emulator Pixel 6 API 34):**
- Selesai dan diuji: update database v1 ke v2 (9 habit, 2 centang, 1 to-do utuh, 16 blok jadwal
  terisi, tautan habit dicari lewat nama sehingga habit yang sudah diganti nama tidak tertaut);
  unit test waktu sholat (Lamongan 1 Januari 2009 selisih paling banyak 1 menit dari buku,
  Terbit 2 menit karena ikhtiyat tetap 2 menit yang dikurangkan, sama dengan Al-Kaukaba) dan
  logika jadwal (Sekarang/Berikutnya pagi, jam kerja, Dzuhur di tengah Kerja pagi, malam,
  setelah batas tidur, akhir pekan, libur); kartu Sekarang/Berikutnya dan timeline di layar;
  Atur jadwal membuka dan menampilkan blok; tombol "Hari ini libur" menyembunyikan blok hari
  kerja dan tersimpan; notifikasi tetap muncul; notifikasi blok muncul
  dalam jendela 5 menit (alarm 09.42, muncul 09.47); "Sudah" mencentang habit dan menutup
  notifikasi.
- **Belum diverifikasi:** simpan hasil editor blok (tambah, ubah, hapus) di layar;
  notifikasi tetap hilang setelah batas tidur dan muncul lagi di blok pertama
  besok; alarm tetap berjalan setelah emulator di-restart; lokasi GPS dan dialog lokasi manual;
  perbandingan waktu sholat lokasi Roziq dengan app Al-Kaukaba; jadwal saat HP dalam Doze
  (alarm dengan jendela 5 menit bisa tertunda sampai jendela pemeliharaan).

### Tahap 18: Alarm Subuh dan pengingat adzan (selesai, 7 Oktober 2026)

Mesin waktu sholat dan lokasi ada di tahap 17. Tahap ini menambah tingkat notifikasi
**Alarm** dan pengaturan pengingat adzan.

**Keputusan (5 Oktober 2026):**

| Pertanyaan | Keputusan |
|---|---|
| Waktu alarm | Mengikuti blok Bangun (awal: Subuh − 15), bisa diubah di Atur jadwal |
| Suara | Nada alarm HP, bisa dipilih lewat pemilih nada Android. Volume naik perlahan dalam 30 detik |
| Tunda | 5 menit, paling banyak 2 kali, lalu tombol Tunda hilang |
| Hari libur dan akhir pekan | Tetap bunyi setiap hari. "Hari ini libur" tidak mematikan alarm |
| Matikan sekali | Bisa mematikan alarm untuk satu tanggal saja (misalnya sakit) |
| Pengingat adzan | Switch per waktu sholat (Subuh, Dzuhur, Ashar, Maghrib, Isya), default nyala |

**Rencana teknis:**
- Alarm presisi dengan `AlarmManager.setAlarmClock`, sehingga ikon alarm tampil di status
  bar dan alarm tetap berbunyi saat HP hemat daya. Izin `USE_EXACT_ALARM` (Android 13+,
  untuk app yang punya fitur alarm) dan `SCHEDULE_EXACT_ALARM` untuk Android 12.
- Saat berbunyi, layar alarm penuh muncul di atas lock screen (full-screen intent, layar
  menyala) dengan tombol **Matikan** dan **Tunda 5 menit**. Kalau izin full-screen intent
  tidak ada, alarm tampil sebagai notifikasi heads-up dengan tombol yang sama.
- Suara diputar dengan `AudioAttributes.USAGE_ALARM`, jadi ikut volume alarm dan menembus
  mode senyap. Alarm berhenti sendiri setelah 10 menit tanpa disentuh.
- Alarm dijadwalkan ulang setiap hari (waktu Subuh bergeser), setelah restart, dan saat
  jam, zona waktu, atau lokasi berubah.
- Pengaturan alarm dan adzan ada di Tentang, di bawah pengingat lain.

**Selesai jika:**
- Alarm berbunyi di jam yang benar (blok Bangun) walau app ditutup dan layar terkunci.
- Ikon alarm tampil di status bar saat alarm berikutnya terjadwal.
- Tunda bekerja dua kali, lalu tombol Tunda hilang.
- Alarm tetap bunyi di akhir pekan dan saat "Hari ini libur" aktif, dan tidak bunyi di
  tanggal yang dimatikan sekali.
- Mematikan pengingat Dzuhur menghentikan notifikasi Dzuhur saja.
- Alarm berhenti sendiri setelah 10 menit.
- Alarm tetap terjadwal setelah emulator di-restart.

**Keputusan saat membangun:**
- Switch adzan mematikan notifikasi blok yang mulai tepat di waktu sholat itu (patokan waktu
  sholat dengan selisih 0). Tidak ada notifikasi adzan terpisah, supaya tidak dobel dengan blok
  "Sholat Dzuhur" dan sejenisnya. Blok tetap tampil di timeline.
- Alarm adalah tingkat notifikasi ketiga (Info, Pengingat, Alarm) yang bisa dipasang ke blok
  mana pun, bukan hanya Bangun. Blok Bangun bawaan jadi Alarm (migrasi database 2 ke 3).
- Tanggal alarm yang dimatikan sekali dan nada pilihan disimpan di SharedPreferences, bukan tabel.
- Izin `USE_EXACT_ALARM` (Android 13+) dan `SCHEDULE_EXACT_ALARM` (Android 12) dibutuhkan
  `setAlarmClock`. Tanpa izin itu app crash saat menjadwalkan, jadi ada pengaman: kalau ditolak,
  jatuh ke alarm tidak presisi.

**Hasil verifikasi (7 Oktober 2026, emulator Pixel 6 API 34):**
- Alarm berbunyi tepat di waktunya (10.23.03 untuk alarm 10.23) saat layar terkunci dan app
  ditutup: layar menyala sendiri, layar alarm tampil, suara `USAGE_ALARM` dan getar berjalan.
  Saat layar sedang dipakai, alarm tampil sebagai notifikasi dengan tombol yang sama.
- Ikon alarm tampil di status bar dan "alarm berikutnya" terdaftar di sistem (03.39 besok).
- Tunda bekerja dua kali (bunyi ulang 5 menit kemudian), bunyi ketiga tanpa tombol Tunda.
  Matikan menghentikan suara dan menghapus notifikasi, alarm berikutnya dijadwalkan ulang.
- Alarm tanpa disentuh berhenti sendiri tepat 10 menit (10.41.01 sampai 10.51.01).
- "Matikan untuk tanggal itu" menggeser alarm ke tanggal berikutnya, "Nyalakan lagi" mengembalikan.
- Mematikan pengingat Dzuhur: alarm jadwal 11.19 tetap jalan tetapi tidak ada notifikasi Dzuhur.
- Setelah emulator di-restart, alarm jam dan alarm jadwal terdaftar ulang tanpa membuka app
  (proses dimulai oleh broadcast boot).
- Alarm tetap bunyi di akhir pekan dan hari libur, serta dilewati di tanggal dimatikan: diuji
  lewat unit test `AlarmTest`, belum di layar.

**Belum diverifikasi:** pemilih nada (tombol "Pilih nada" belum diketuk, baru nada bawaan yang
terdengar); layar alarm di perangkat Android 14+ asli, karena izin full-screen intent di sana
bisa perlu diberikan pengguna; Android 12 dengan izin alarm presisi dicabut; perilaku di HP
sungguhan saat Doze semalaman. Catatan: di emulator, cold start pertama app sampai alarm
terdaftar memakan sekitar 25 detik karena Astronomy Engine lambat dimuat. Di HP asli lebih cepat,
tapi kalau terasa lambat, Baseline Profile bisa dipertimbangkan.

### Tahap 19: Catat cepat, follow-up kerja, daily scrum dan EOD (selesai, 7 Oktober 2026)

Menggantikan tahap 15. **Habitflow menggantikan jurnal task harian di Notion** ("Ruang Kerja :
I am a Leader"). Task lama di Notion dihabiskan di sana sampai cut off. Task baru dicatat di
Habitflow, tanpa migrasi data dari Notion.

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Follow-up kerja.

| Pertanyaan | Keputusan |
|---|---|
| Hubungan dengan Notion | Menggantikan task harian Notion sepenuhnya. Task lama tidak dimigrasi |
| Letak | Tab baru **Kerja**, urutan tab: Hari ini, Kerja, Kontribusi, Habit, Tentang |
| Status | Inbox, Aktif, Menunggu, Selesai |
| Catat cepat | Tombol "Catat" di semua layar, masuk Inbox tanpa memilih apa pun |
| Merapikan Inbox | Saat EOD 16.00. Inbox yang belum rapi tampil di dashboard |
| Pengingat follow-up | Tidak per item. Dikumpulkan di daily scrum 08.00 dan dashboard. Item boleh diberi jam khusus |
| Orang terkait | Ketik bebas dengan saran nama yang pernah dipakai, bisa difilter per orang |
| Akses laptop | Tidak perlu. HP saja |
| Backup | Sinkron ke server sendiri di tahap 19B. Sampai itu jadi, Android Auto Backup |

**Rencana teknis:**
- Tabel baru `follow_ups` (judul, status, tanggal tindak lanjut, jam opsional, orang, catatan,
  dibuat, selesai, tanggal dipilih untuk hari ini) dan `work_days` (satu baris per tanggal:
  catatan EOD, waktu daily scrum dan EOD diselesaikan). Migrasi database ke versi berikutnya
  tanpa destructive migration.
- Aturan "follow-up apa yang muncul di daily scrum" dan perpindahan status di `domain/` dengan
  unit test.
- Notifikasi daily scrum dan EOD memakai blok jadwal tahap 17 (Kerja pagi 08.00 dan EOD
  16.00). Tap notifikasi membuka tab Kerja dalam mode daily scrum atau EOD.
- Item yang punya jam khusus memakai notifikasi tingkat Pengingat pada jam itu.
- Pastikan Android Auto Backup aktif untuk database (`allowBackup`, aturan ekstraksi data)
  sebagai pengaman sementara sampai tahap 19B.

**Selesai jika:**
- Tombol Catat ada di semua tab. Mengetik judul lalu Enter menyimpan ke Inbox dalam satu
  langkah, dan bottom sheet langsung siap untuk catatan berikutnya.
- Tab Kerja menampilkan Inbox, Lewat tanggal, Hari ini, Menunggu, dan Nanti dengan jumlahnya.
- Daily scrum 08.00 menampilkan follow-up yang lewat tanggal, jatuh tempo hari ini, dipilih
  saat EOD kemarin, dan Menunggu yang tanggal cek ulangnya hari ini. Item bisa dipilih untuk
  hari ini.
- EOD 16.00: setiap follow-up hari ini diberi status (Selesai, Lanjut besok, Pindah tanggal,
  Menunggu), setiap item Inbox dirapikan, lalu catatan EOD disimpan. Tombol Bagikan membuka
  share sheet dengan ringkasan hari itu.
- Riwayat EOD dan follow-up yang selesai di suatu hari tampil di detail hari Kontribusi.
- Filter per orang menampilkan semua follow-up terkait orang itu.
- Level hari, streak, dan to-do pribadi tidak berubah.
- Update dari versi database sebelumnya tidak menghapus data.

**Keputusan saat membangun:**
- Blok jadwal punya kolom `workAction` (Daily scrum atau EOD). Blok Kerja pagi dan EOD bawaan
  ditandai lewat migrasi 3 ke 4; blok yang sudah diubah pengguna tidak disentuh. Tap notifikasi blok
  bertanda itu membuka layar yang sesuai di tab Kerja. Penanda belum bisa diubah dari editor blok.
- Lewat tanggal yang sudah dipilih hari ini pindah ke Hari ini (bukan ditampilkan dua kali), dan Menunggu
  yang cek ulangnya hari ini masuk Hari ini. "Lanjut besok" memilih item untuk besok sehingga besok pagi
  sudah masuk Hari ini dan siap dipilih di daily scrum.
- EOD: follow-up hari ini awalnya "Lanjut besok" dan Inbox awalnya "Biarkan", supaya Simpan EOD selalu
  bisa ditekan. Tanggal selesai mengikuti zona waktu perangkat.
- Pemilih tanggal dan jam memakai komponen Material 3, jadi bahasanya mengikuti locale perangkat.
- Pengingat jam khusus memakai alarm jadwal yang sama (tidak presisi), bukan alarm presisi.

**Hasil verifikasi (7 Oktober 2026, emulator Pixel 6 API 34):**
- Database v3 berisi data naik ke v4 tanpa kehilangan habit atau blok; Kerja pagi dan EOD tertanda.
- Tombol Catat ada di semua tab; mengetik lalu Enter menyimpan ke Inbox, kolom kosong lagi, sheet tetap
  terbuka dan siap untuk catatan berikutnya (dua item berturut-turut diuji).
- Tab Kerja menampilkan Inbox, Hari ini, Lewat tanggal, Menunggu, dan Nanti dengan jumlah dan keterangan
  yang benar (tanggal, jam, orang, "Lewat n hari", "cek <tanggal>"). Filter per orang (Budi) menyaring semua
  bagian. Kartu ringkasan di Hari ini muncul dengan angka yang benar.
- Daily scrum: kandidat dan "Ambil dari Nanti" tampil, memilih item tersimpan langsung, "Selesai daily
  scrum" mengisi `scrumDoneAt`.
- EOD: Selesai, Lanjut besok, Nanti, dan Hapus (Inbox) tersimpan benar, catatan EOD tersimpan, status
  "selesai" dan "dipilih besok" sesuai di database.
- Detail hari Kontribusi menampilkan bagian Kerja (follow-up yang selesai dan EOD).
- Pengingat follow-up berjam khusus muncul di jendela 5 menit (12.39 muncul 12.44). Pembukaan Daily scrum
  dan EOD lewat intent notifikasi diuji saat app belum dan sudah berjalan.
- Level hari, streak, dan to-do pribadi tidak berubah (follow-up tidak ikut dihitung).

**Belum diverifikasi:** tombol Bagikan (share sheet belum diketuk); EOD dengan "Pindah tanggal" dan
"Menunggu" lewat pemilih tanggal (kodenya diuji unit test, belum di layar); simpan editor follow-up;
Android Auto Backup (aturan sudah dipasang, pencadangan dan pemulihan belum dicoba); tap notifikasi
sungguhan (yang diuji adalah intent yang sama lewat `am start`); sinkron server di tahap 19B.

### Tahap 19B: Sinkron ke server sendiri (selesai dikoding dan diuji lokal, 9 Oktober 2026)

Dikerjakan **tepat setelah tahap 19, sebelum cut off Notion**, karena data kerja tidak boleh
hanya ada di satu HP. Aturan fitur di `docs/rancangan.md` bagian Sinkron ke server.

| Pertanyaan | Keputusan |
|---|---|
| Fungsi | Backup dan pindah HP. Tidak ada akses web atau laptop |
| Arah | Satu arah HP → server, plus pulihkan dari server saat install ulang atau ganti HP |
| Server | **Laravel baru** (Laravel 12, PHP 8.2) di VPS Al-Kaukaba (`202.155.17.2`), terpisah dari app Al-Kaukaba |
| Letak kode server | Folder `server/` di repo ini. Pemilik yang men-deploy ke VPS; kode di repo tidak mengakses VPS |
| Cakupan | Semua data Habitflow: habit, riwayat, to-do, jadwal, follow-up, EOD, pengaturan. Nanti kesehatan |
| Sifat app | Tetap offline-first. HP adalah sumber data. Sinkron berjalan saat ada internet |
| Login | Token pribadi. Dibuat sekali di server lewat perintah artisan, ditempel di Tentang. Tanpa akun |
| Cara sinkron | Snapshot lengkap (satu berkas JSON bernomor versi), bukan per baris |
| Kapan | Otomatis ±5 menit setelah ada perubahan (hanya saat ada internet) dan sekali sehari. Tombol "Sinkron sekarang" |
| Pulihkan | Manual dari Tentang, dengan konfirmasi yang menyebut waktu snapshot. Mengganti seluruh data di HP |
| Perlindungan | HTTPS wajib (kecuali emulator saat debug). Isi disimpan terenkripsi di server dengan kunci app Laravel |
| Beban VPS | Tanpa layanan baru: Laravel stateless di PHP-FPM yang sudah ada, SQLite untuk metadata, berkas snapshot di storage. Satu permintaan per sinkron |

> **Digantikan tahap 31 (11 Oktober 2026):** baris "Login: token pribadi, tanpa akun" di atas adalah keputusan awal tahap ini.
> Sejak tahap 31, login pakai akun (email/kata sandi atau Google) dan wajib. `PUT /snapshot`/`GET /snapshot/latest` pindah
> ke `user-token` (sub-tahap 31E), token pribadi (`api_tokens`) dipensiunkan setelah snapshot lama diklaim ke akun pemilik.
> Baris di bawah dibiarkan sebagai riwayat keputusan tahap 19B.

**Kontrak API** (semua di bawah `/api/v1`, header `Authorization: Bearer <token>`):

| Metode dan jalur | Fungsi |
|---|---|
| `GET /ping` | Uji koneksi dan token. 200 `{"ok":true}` |
| `PUT /snapshot` | Unggah snapshot. 200 `{"id","receivedAt","bytes","unchanged"}`. Kalau isinya sama persis dengan snapshot terakhir, tidak ditulis ulang (`unchanged: true`) |
| `GET /snapshot/latest` | Snapshot terakhir apa adanya. 404 kalau belum ada. Header `X-Received-At` berisi waktu terima (epoch milidetik) |

Kesalahan: 401 token kosong atau salah, 413 lebih dari 5 MB, 422 isi bukan JSON snapshot yang sah,
429 terlalu sering (60 permintaan per menit per token). Server menyimpan 14 snapshot terakhir dan
menghapus yang lebih lama.

**Format snapshot** (`schemaVersion` 1). Server hanya memeriksa bahwa `schemaVersion` bilangan bulat
dan `data` objek; isinya tidak ditafsirkan.

```json
{
  "schemaVersion": 1,
  "createdAt": 1791350953356,
  "deviceId": "uuid",
  "appVersion": "1.0",
  "data": {
    "habits": [{"id": 1, "name": "...", "createdAt": "2026-10-07", "sortOrder": 0, "isMandatory": true}],
    "habitEntries": [{"habitId": 1, "date": "2026-10-07"}],
    "todos": [{"id": 1, "title": "...", "date": "2026-10-07", "done": false, "createdAt": 0}],
    "scheduleBlocks": [{"id": 1, "name": "...", "startType": 0, "startValue": 480, "prayer": null,
      "durationMinutes": 240, "endMinuteOfDay": null, "activeDays": 31, "level": "REMINDER",
      "sortOrder": 0, "workAction": null}],
    "scheduleBlockHabits": [{"blockId": 1, "habitId": 1}],
    "daysOff": ["2026-10-07"],
    "followUps": [{"id": 1, "title": "...", "status": "ACTIVE", "date": null, "time": null, "person": null,
      "note": null, "createdAt": 0, "doneAt": null, "pickedDate": null}],
    "workDays": [{"date": "2026-10-07", "eodNote": null, "scrumDoneAt": null, "eodDoneAt": null}],
    "settings": {"location": {"name": "Surabaya", "latitude": -7.2575, "longitude": 112.7521},
      "persistentNotification": true, "adzan": {"SUBUH": true, "DZUHUR": true, "ASHAR": true,
      "MAGHRIB": true, "ISYA": true}, "themeMode": "SYSTEM"}
  }
}
```

Nada alarm dan tanggal alarm yang dimatikan sekali tidak ikut (khusus perangkat dan sementara).

**Aturan pulihkan:** snapshot dengan `schemaVersion` lebih baru dari yang dikenal app ditolak dengan
pesan untuk memperbarui app. Pemulihan mengganti semua tabel dalam satu transaksi, jadi gagal di
tengah tidak meninggalkan data setengah. Setelah pulih, notifikasi dan alarm dijadwalkan ulang.

**Selesai jika:**
- Server menolak tanpa token (401), dengan isi bukan JSON snapshot (422), dan terlalu besar (413).
- Unggah snapshot yang sama dua kali tidak menulis berkas kedua; yang lama dari 14 snapshot dipangkas.
- Isi di storage terenkripsi (tidak terbaca sebagai teks biasa), dan `GET latest` mengembalikan isi
  yang persis sama dengan yang diunggah.
- Di app: pengaturan URL dan token tersimpan, "Uji koneksi" menunjukkan berhasil atau gagal dengan pesan
  yang jelas, "Sinkron sekarang" mengunggah, dan status terakhir tampil di Tentang.
- Perubahan data memicu sinkron otomatis tertunda; tanpa internet ia menunggu dan jalan saat tersambung.
- Pulihkan di app dengan data berbeda mengembalikan semua tabel persis seperti snapshot, termasuk
  pengaturan, dan gagal dengan aman kalau server tidak terjangkau.
- Round-trip diuji: data di HP → snapshot → data kosong → pulihkan → data sama (unit test untuk
  serialisasi, uji di emulator terhadap server lokal untuk alur utuh).

**Hasil verifikasi (9 Oktober 2026, emulator Pixel 6 API 34, server lokal `php artisan serve`, alamat
`http://10.0.2.2:8000`):**
- Tes server: 18 lulus (token, 401, 413, 422, 429, pemangkasan 14 snapshot, enkripsi, cabut token).
- Perbaikan yang ditemukan: hash snapshot dulu dihitung dari seluruh badan, padahal `createdAt` dan
  `deviceId` berubah di setiap kirim, jadi `unchanged` tidak pernah `true`. Sekarang hash dari
  `schemaVersion` dan `data` saja (ada tesnya).
- Token salah di app: status Tentang menampilkan "Token ditolak server. Periksa token di pengaturan."
- "Uji koneksi" dan "Sinkron sekarang" dengan token benar: snapshot masuk, berkas di storage
  terenkripsi (teks habit tidak terbaca). Sinkron kedua tanpa perubahan: status "Data sudah sama dengan
  di server.", tidak ada baris atau berkas kedua.
- Pulihkan: dialog menyebut waktu snapshot dan isinya (9 habit, 7 follow-up, 1 to-do). Setelah satu
  habit dicentang di HP, "Ganti semua data" mengembalikan Hari ini dari 1/9 ke 0/9, status "Data
  dipulihkan dari server."

**Belum diverifikasi:** sinkron otomatis ±5 menit dan harian lewat WorkManager (belum ditunggu);
menunggu saat tanpa internet; pulihkan dengan server mati; penolakan alamat `http://` di build rilis;
pulihkan ke HP kosong (hanya diuji di HP yang datanya sama); pemasangan di VPS (dikerjakan pemilik).

### Tahap 20: Pengingat kerja (selesai dikoding dan diuji di emulator, 9 Oktober 2026)

Aturan fitur di `docs/rancangan.md` bagian Pengingat kerja. Minum air **setiap 60 menit** di jam kerja
dengan tombol "Sudah minum" (8 gelas mencentang "Air putih 2 liter"), break **setiap 90 menit**, digabung
kalau berdekatan. Mengikuti aturan tiga tingkat notifikasi di visi.

| Pertanyaan | Keputusan |
|---|---|
| Jam kerja | Blok jadwal bertanda `workReminders`. Bawaan Kerja pagi dan Kerja sore. Ikut "Hari ini libur" dan blok sholat tanpa aturan tambahan |
| Waktu pengingat | Air di mulai blok lalu tiap 60 menit sebelum blok berakhir (7 kali di jadwal bawaan). Break di mulai + 90, + 180 |
| Digabung | Air dan break berjarak ≤ 15 menit menjadi "Break + minum" di waktu lebih awal |
| Ditahan | Yang jatuh di blok sholat digeser ke akhir blok sholat, dibuang kalau lewat akhir blok kerja. Meeting menyusul di tahap 22 |
| Bentuk Info | Notifikasi senyap (channel `IMPORTANCE_LOW`) dengan id tetap, menggantikan dirinya sendiri. Bukan teks tambahan di notifikasi tetap, supaya tetap muncul kalau notifikasi tetap dimatikan dan bisa punya tombol "Sudah minum". Ini mempersempit baris "hanya memperbarui notifikasi tetap" di visi |
| Penghitung | Tabel `drink_counts` (tanggal, jenis, jumlah), jenis awal `WATER`. Tahap 23 menambah kopi dan minuman manis di tabel yang sama |
| Habit air | Kolom baru `habits.autoSource` ("WATER"). Tercentang saat hitungan naik melewati 8. Centang manual menang |
| Diabaikan | 3 pengingat air berturut-turut tanpa tambahan gelas di satu blok menghentikan pengingat air sampai blok berikutnya. Break tetap |
| Interval | Tetap 60 dan 90 menit, tidak bisa diubah di pengaturan. Yang bisa diatur: nyala atau mati per jenis di Tentang, dan tanda per blok di editor blok |

**Rencana teknis:**
- `domain/schedule/WorkReminders.kt` (Kotlin murni, unit test): menghitung daftar pengingat dari blok yang sudah
  di-resolve (waktu, jenis `WATER`, `BREAK`, `BREAK_AND_WATER`, blok asal), termasuk gabung dan geser. Fungsi
  kecil untuk pengingat air yang diabaikan dan untuk menentukan kapan hitungan melewati target.
- Database 4 → 5: kolom `habits.autoSource` (diisi "WATER" untuk habit bernama "Air putih 2 liter"), kolom
  `schedule_blocks.workReminders` (diisi nyala untuk Kerja pagi 08.00 dan Kerja sore 13.00 bawaan yang belum
  diubah), dan tabel `drink_counts`. Riwayat tidak disentuh. Jadwal awal untuk instalasi baru ikut diperbarui.
- `ScheduleNotifier.refresh` menambah pengingat kerja ke penentuan alarm berikutnya dan ke `announceStarted`
  (jendela mundur 10 menit yang sama). Tombol "Sudah minum" dikirim ke `ScheduleReceiver` (aksi baru).
- Snapshot 19B: `data.drinkCounts`, `habits[].autoSource`, `scheduleBlocks[].workReminders`, dan
  `settings.workReminders` (air, break). Semuanya **opsional saat dibaca** (snapshot lama tetap bisa dipulihkan),
  jadi `schemaVersion` tetap 1. Tabel `drink_counts` ikut dipantau pemicu sinkron otomatis.

**Selesai jika:**
- Unit test lulus untuk: 7 pengingat air dan 3 break di jadwal bawaan, gabung ≤ 15 menit (dan 16 menit tidak),
  geser keluar blok sholat dan dibuang di luar blok kerja, tanpa pengingat di akhir blok, blok tanpa tanda
  tidak menghasilkan apa-apa, blok mati karena libur tidak menghasilkan apa-apa, hitungan melewati target
  tepat sekali, dan aturan diabaikan tiga kali.
- Pengingat air muncul senyap di waktunya (tanpa bunyi dan pop-up), isinya menyebut jumlah gelas, dan
  menggantikan pengingat sebelumnya, bukan menumpuk.
- "Sudah minum" di notifikasi menambah satu gelas, menutup notifikasi, dan angkanya sama di kartu air.
- Gelas ke-8 mencentang "Air putih 2 liter". Centang manual dibatalkan tidak dicentang ulang gelas ke-9.
- "Hari ini libur" mematikan semua pengingat kerja hari itu. Mematikan switch air atau break di Tentang
  menghentikan jenis itu saja. Tanda blok di editor mengubah jam kerja.
- Migrasi 4 → 5 pada database yang sudah berisi data mempertahankan semua riwayat, dan habit air serta blok kerja
  bawaan mendapat tanda.
- Snapshot: ekspor lalu pulihkan mengembalikan penghitung gelas, autoSource, dan tanda blok. Snapshot lama tanpa
  bidang itu tetap bisa dipulihkan.

**Hasil verifikasi (9 Oktober 2026, emulator Pixel 6 API 34, database yang sudah berisi data tahap 19B):**
- Unit test: 16 tes `WorkRemindersTest` dan 2 tes codec baru, seluruhnya 141 tes lulus.
- Migrasi 4 → 5 pada database nyata: versi jadi 5, 3 centang habit dan 7 follow-up utuh, "Air putih 2 liter"
  bersumber WATER, Kerja pagi dan Kerja sore bertanda, tabel `drink_counts` ada.
- Kartu air: 7 gelas belum mencentang habit, gelas ke-8 mencentang dan menampilkan "Target tercapai". Centang
  dibatalkan manual lalu gelas ke-9: tidak dicentang ulang. "−" ke 8: centang tidak berubah. Turun ke 7 lalu naik ke 8
  lagi: dicentang lagi (aturannya: hanya kenaikan yang melewati 8).
- Notifikasi (dibaca dari `dumpsys notification`): break "Break sebentar" tanpa tombol, air "Waktunya minum" dengan
  "8 dari 8 gelas hari ini" dan tombol "Sudah minum", gabungan "Break + minum" dengan tombol yang sama. Semuanya di
  channel `schedule_info` (importance rendah, tanpa suara, getar, atau pop-up), satu id yang menggantikan.
- "Sudah minum" di notifikasi: gelas naik 8 → 9 dan notifikasi tertutup.
- Blok baru lewat Atur jadwal dengan switch "Pengingat air dan break" menghasilkan pengingat air di jam mulainya.
- Tentang: bagian "Pengingat kerja" tampil di antara Notifikasi dan Alarm. Mematikan Break menyimpan pengaturan.
- Snapshot dari emulator ke server lokal berisi `drinkCounts`, `settings.workReminders`, `autoSource`, dan
  `workReminders` blok. Pulihkan mengembalikan gelas dari 5 ke 3 dan switch Break dari nyala ke mati (nilai snapshot).
- Temuan dan perbaikan: sistem melebarkan jendela alarm tak presisi menjadi 10 menit, dan jendela mundur
  pengumuman (10 menit, awal eksklusif) melewatkan pengingat tepat di batasnya saat alarm berbunyi di ujung
  jendela (pengingat air 10.00 yang berbunyi 10.10 hilang). Jendela mundur naik ke 15 menit; pengingat 11.00
  sesudahnya tidak hilang.

**Belum diverifikasi:** "Hari ini libur" mematikan pengingat dan blok sholat menggeser pengingat (hanya unit test,
belum di layar); mematikan switch Minum air menghentikan pengingat air (yang diuji hanya Break dan penyimpanan
pengaturan); aturan "diabaikan tiga kali" (hanya unit test); menit tepat munculnya notifikasi (jam emulator
melompat); perilaku Doze di HP nyata (hanya diuji dengan Doze paksa di emulator, lihat di bawah).

**Doze (9 Oktober 2026, setelah verifikasi di atas).** Alarm `setWindow` tidak berbunyi selama Doze mendalam, jadi
pengingat di HP yang diam di meja bisa terlambat atau terlewat. Jadwal notifikasi dipindah ke
`setAndAllowWhileIdle` (berlaku juga untuk blok tahap 17 dan 19, dan untuk pengingat follow-up berjam). Alarm
Subuh tidak berubah karena sudah memakai `setAlarmClock`. Di emulator: `dumpsys alarm` menunjukkan alarm jadwal
berflag `ALLOW_WHILE_IDLE_COMPAT` (0x20, sebelumnya 0x0). Dengan `deviceidle force-idle` (status `IDLE`), blok uji
yang mulai 12.27 dengan pengingat air tetap mengumumkan blok dan pengingat airnya. Pembatasan sistem di Doze (sekitar
sekali per 9 menit per app) tidak masalah untuk pengingat per 60 menit. Belum diuji: Doze nyata semalaman dan
dampak baterainya.

### Tahap 21: Kesehatan (selesai dikoding dan diuji di emulator, 9 Oktober 2026)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Kesehatan.

**Keputusan tambahan (9 Oktober 2026)**, yang menggantikan rencana awal bila bertentangan:

| Pertanyaan | Keputusan |
|---|---|
| Baca langkah di latar belakang | **WorkManager periodik 1 jam**, bukan AlarmManager: hemat baterai, bertahan setelah restart, dan tidak butuh izin alarm. Health Connect mewajibkan izin `READ_HEALTH_DATA_IN_BACKGROUND` untuk membaca saat app tidak tampil; kalau fiturnya tidak tersedia atau ditolak, langkah hanya dibaca saat app terbuka dan kartu menjelaskannya |
| Centang otomatis | Sekali per tanggal, saat langkah ≥ 8.000. Tanggal terakhir centang otomatis disimpan, jadi centang yang dibatalkan manual tidak dicentang ulang |
| Letak kartu di Hari ini | Skor, Langkah, Air, lalu Tensi dan Berat berdampingan |
| Sheet catat | Satu komponen `ModalBottomSheet` dengan dua mode (Berat, Tensi), dibuka dari "+ Catat" kartu masing-masing |
| Tinggi dan target | Tinggi ditanyakan di sheet berat kalau belum ada. Tinggi dan target berat juga bisa diubah di Tentang bagian Kesehatan. Disimpan di pengaturan dan ikut snapshot |
| Pengingat | Tingkat Info, Subuh + 60 menit, hanya kalau hari itu belum dicatat, berat hari Senin, tensi harian atau mingguan (Senin) atau mati, digabung kalau sama |
| Pembulatan | BMI satu desimal, kategori dihitung dari angka yang tampil. Kategori tensi dari yang lebih tinggi antara sistolik dan diastolik |
| Nama habit | Migrasi 5 → 6 mengganti "Jalan kaki 20 menit" menjadi "8.000 langkah" dengan `autoSource` STEPS. Habit yang sudah diganti namanya oleh pengguna tidak disentuh |
| Snapshot | `data.weightEntries`, `data.bloodPressureEntries`, dan `settings.health` (tinggi, target, pengingat). Opsional saat dibaca, `schemaVersion` tetap 1 |

| Pertanyaan | Keputusan |
|---|---|
| Sumber langkah | Health Connect, dari HP atau smartwatch apa pun |
| Letak | Input lewat kartu di dashboard Hari ini. Grafik di tab Kontribusi yang diganti nama **Progres** (Habit dan Kesehatan). Tetap 5 tab |
| Langkah dan habit | Habit "Jalan kaki 20 menit" diganti nama **"8.000 langkah"** dan tercentang otomatis saat target tercapai. Tetap bisa dicentang manual |
| Kategori BMI | Kemenkes RI: kurus < 18,5, normal 18,5–25,0, gemuk > 25,0–27,0, obesitas > 27,0 |
| Target berat | Ada target berat dan tren 4 minggu. Default target = batas atas BMI normal |
| Kategori tensi | PERHI/ESH: optimal < 120/80, normal 120–129/80–84, normal-tinggi 130–139/85–89, hipertensi derajat 1 ≥ 140/90, derajat 2 ≥ 160/100, derajat 3 ≥ 180/110 |
| Tensi sangat tinggi | Pesan tenang dengan saran ukur ulang dan hubungi dokter. Tanpa alarm atau warna merah |
| Pengingat | Berat: Senin pagi setelah bangun. Tensi: pagi setelah bangun, frekuensi bisa diatur (harian, mingguan, mati), default mingguan |

**Rencana teknis:**
- Health Connect lewat `androidx.health.connect:connect-client`, izin baca langkah saja.
  Health Connect bawaan Android 14+. Di Android 9-13 perlu app Health Connect, dan app tetap
  jalan tanpa itu (kartu langkah menampilkan cara mengaktifkan). Wajib ada layar penjelasan
  izin (privacy rationale) yang diminta Health Connect.
- Langkah hari ini dibaca saat app dibuka, saat dashboard tampil, dan berkala lewat
  WorkManager (sekitar setiap jam, lihat keputusan tambahan) untuk mencentang habit otomatis walau app tidak dibuka.
- Habit punya sumber otomatis opsional (langkah) dan target. Migrasi mengganti nama habit
  "Jalan kaki 20 menit" menjadi "8.000 langkah" dan memberinya sumber langkah, tanpa
  mengubah riwayat centang.
- Tabel baru `weight_entries` (waktu, kg) dan `blood_pressure_entries` (waktu, sistolik,
  diastolik, nadi opsional, catatan). Tinggi badan dan target berat di pengaturan.
- Kategori BMI dan tensi, tren berat, dan sisa ke target di `domain/` dengan unit test,
  termasuk angka batas tiap kategori.
- Grafik di Progres digambar sendiri dengan Canvas Compose, seperti heatmap, tanpa library
  grafik baru.

**Selesai jika:**
- Dengan izin Health Connect, kartu langkah menampilkan langkah hari ini. Mencapai 8.000
  mencentang habit "8.000 langkah" dalam waktu paling lama sekitar 1 jam walau app tertutup.
- Tanpa Health Connect atau tanpa izin, app tetap jalan dan habit bisa dicentang manual.
- Mencatat berat dan tensi bisa dari dashboard dalam satu bottom sheet. BMI dan kategori
  tampil langsung setelah disimpan.
- Unit test kategori BMI (Kemenkes) dan tensi (PERHI) lulus untuk semua angka batas.
- Tensi ≥ 180/110 memunculkan pesan saran, bukan alarm.
- Tab Progres menampilkan heatmap habit seperti sebelumnya dan grafik berat (dengan garis
  target) serta tensi (sistolik dan diastolik).
- Pengingat berat dan tensi muncul sesuai pengaturan dan bisa dimatikan.
- Migrasi mengganti nama habit tanpa menghapus riwayatnya.

**Hasil verifikasi (9 Oktober 2026, emulator Pixel 6 API 34 dengan Health Connect bawaan versi 14, database yang sudah
berisi data tahap 20):**
- Unit test: 26 tes `domain/health` (setiap angka batas BMI dan tensi, tren, pengingat), 3 tes codec, dan 8 tes pemformat
  teks. Seluruhnya 178 tes lulus.
- Migrasi 5 → 6 pada database nyata: habit "Jalan kaki 20 menit" menjadi "8.000 langkah" bersumber STEPS, 3 centang dan 7
  follow-up utuh, tautan blok "Aktivitas fisik" ke habit itu tetap ada, dua tabel baru ada.
- Izin lewat layar Health Connect asli: hanya "Steps" yang ditawarkan. Sebelum diizinkan kartu menampilkan "Izinkan akses
  langkah". Sesudahnya, 3.240 langkah tampil "3.240 / 8.000" dengan bar, dan 8.200 langkah mencentang habit otomatis.
  Centang yang dibatalkan manual tidak dicentang ulang saat langkah dibaca lagi.
- Layar penjelasan izin (`PermissionRationaleActivity`) tampil lewat intent `ACTION_SHOW_PERMISSIONS_RATIONALE`.
- Sheet berat: tinggi ditanyakan sekali, 72,4 kg dan 170 cm menghasilkan "BMI 25,1 · Gemuk" dan "0,1 kg lagi ke target"
  (target bawaan 72,3). Sheet tensi: 182/112 menghasilkan "Hipertensi derajat 3" dengan saran tenang tanpa warna merah.
- Tab Progres: segmen Habit (isi lama) dan Kesehatan. Grafik berat dengan garis target putus-putus dan tren 4 minggu
  ("turun 1,2 kg"), grafik tensi dengan dua garis yang dibedakan lewat ketebalan.
- Tentang: bagian Kesehatan (tinggi, target 70,5 kg disimpan) dan Pengingat kesehatan.
- Pengingat (lewat jam palsu `DebugClockReceiver`, karena tidak bisa menunggu Senin pagi): Senin dengan berat nyala dan tensi
  mingguan menghasilkan "Timbang dan ukur tensi"; Selasa mingguan tidak ada; Selasa harian hanya "Waktunya ukur tensi";
  hari yang tensinya sudah dicatat tidak ada; semua mati tidak ada. Semuanya senyap di channel Info.
- Snapshot: ekspor berisi `weightEntries`, `bloodPressureEntries`, `settings.health`, dan `autoSource`. Setelah data dan
  pengaturan kesehatan dihapus di HP, pulihkan mengembalikan 10 catatan berat, 9 tensi (termasuk nadi dan catatan), tinggi,
  target, dan pengingat.
- Temuan: (1) petunjuk "izinkan akses latar belakang" di kartu langkah awalnya tampil walau Health Connect di HP ini
  tidak menawarkan izinnya, jadi jalan buntu; sekarang hanya tampil kalau fiturnya tersedia. (2) Satu kali dialog "tidak
  merespons" saat cold start di emulator yang kehabisan memori (sisa RAM sekitar 100 MB, beban 7,8); tidak terulang dan
  tidak ada kerja berat di thread utama dari kode ini, tapi belum diuji di HP nyata.

**Belum diverifikasi:** pembacaan langkah di latar belakang saat app tertutup (kriteria "sekitar 1 jam"): Health Connect di
emulator ini tidak mendukung izin latar belakang, jadi worker per jam hanya terdaftar dan belum terbukti mencentang habit.
Di HP dengan Health Connect yang lebih baru izin itu akan ditawarkan; di HP tanpanya langkah hanya dibaca saat app dibuka,
dan kartu menjelaskannya. Juga belum: tombol "Pasang" dan "Perbarui Health Connect" (emulator ini sudah punya Health Connect
terbaru); pengingat Senin pagi lewat alarm sungguhan (hanya lewat jam palsu); data langkah dari smartwatch.

**Catatan teknis.** Library `androidx.health.connect:connect-client` dipasang di **1.1.0-alpha08**, versi terbaru yang masih
cocok dengan compileSdk 34 dan AGP 8.5.2 (versi 1.1.0 stabil butuh compileSdk 36). Nama konstanta fitur latar belakang di
versi ini `FEATURE_HEALTH_DATA_BACKGROUND_READ`; pindah ke 1.1.0 stabil menunggu pembaruan AGP dan compileSdk. Alat uji
build debug: `DebugStepsReceiver` (tulis dan hapus langkah uji) dan `DebugClockReceiver` (jalankan notifikasi dengan jam
palsu), keduanya hanya ada di `app/src/debug`.

### Tahap 22: Kalender dan acara rutin (diputuskan 5 Oktober 2026, dirinci 9 Oktober 2026, selesai dikoding 10 Oktober 2026, diuji sebagian di emulator)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Kalender.

**Keputusan tambahan (9 Oktober 2026)**, yang menggantikan rencana awal bila bertentangan:

| Pertanyaan | Keputusan |
|---|---|
| Tanggal yang tidak ada di bulan itu | Jatuh di hari terakhir bulan itu (31 dan 29 Februari) |
| Libur nasional di database | **Dihitung, bukan disimpan** di `days_off`: libur efektif = libur manual ∪ (libur nasional − pembatalan). Tabel baru `holiday_cancellations` menyimpan tanggal yang dibatalkan. Daftar yang diperbarui langsung berlaku tanpa migrasi data |
| Cuti bersama | Sama dengan libur nasional, bisa dibatalkan per tanggal |
| Sumber libur | `assets/libur/2026.json` dan `2027.json` dari setneg.go.id (SKB 3 Menteri), dibaca 9 Oktober 2026. Tahun tanpa berkas: tidak ada libur otomatis. Pembaruan lewat update app |
| Acara di tampilan | Timeline dan Sekarang/Berikutnya memuat semua acara berjam, kedua label. Agenda memuat semua acara. Tidak ada kartu "Acara hari ini" terpisah (rancangan awal membedakan Kerja ke Agenda dan Pribadi ke dashboard; timeline sudah memenuhinya) |
| Acara Kerja di hari libur | Mati (timeline, Agenda, pengingat), juga untuk kalender HP berlabel Kerja |
| Pengecualian satu kejadian | Tabel `event_exceptions` berisi lewati atau nilai pengganti penuh (tanggal, jam, durasi, judul). Mengubah seri tidak mengubah kejadian yang sudah diubah satu per satu |
| Pengingat | Tingkat Pengingat, −15 menit bawaan, pilihan Tanpa/5/10/15/30/60, hanya acara Habitflow berjam |
| Kalender HP | `CalendarContract.Instances`, izin `READ_CALENDAR`, dipilih dan dilabeli per kalender di Tentang (bawaan tidak ada, label Pribadi). Tidak ikut snapshot (nomor kalender khusus perangkat) |
| Tautan catat cepat | Kolom `eventId`, `eventDate`, `eventTitle` di `follow_ups`. Acara kalender HP hanya menyimpan judul dan tanggal (tanpa id) |
| Snapshot | `data.events`, `data.eventExceptions`, `data.holidayCancellations`, dan tiga kolom follow-up. Opsional saat dibaca, `schemaVersion` tetap 1 |

| Pertanyaan | Keputusan |
|---|---|
| Sumber acara | Acara dibuat di Habitflow, ditambah membaca kalender HP (hanya baca) |
| Kalender HP yang dibaca | Dipilih sendiri per kalender di pengaturan. Default tidak ada |
| Pengulangan | Sekali, harian, setiap N minggu di hari tertentu, bulanan per tanggal atau per urutan hari (Senin kedua), tahunan. Bisa diberi tanggal berakhir |
| Tampilan | Acara hari ini masuk timeline dan Sekarang/Berikutnya. Tab Kerja punya Agenda 7 hari dan tampilan bulan untuk melompat ke tanggal |
| Libur nasional | Otomatis dari daftar libur nasional dan cuti bersama. "Hari ini libur" aktif sendiri, bisa dibatalkan per tanggal |
| Pengingat | −15 menit hanya untuk acara Habitflow. Acara kalender HP memakai pengingat Google Calendar, supaya tidak dobel |
| Follow-up | Catatan yang dibuat selama acara berlangsung tertaut ke acara itu |
| Label | Setiap acara Kerja atau Pribadi. Libur nasional hanya mematikan blok dan acara Kerja |

**Rencana teknis:**
- Tabel `events` (judul, label, mulai, durasi atau sepanjang hari, aturan pengulangan sebagai
  kolom terstruktur, tanggal berakhir, pengingat menit, catatan) dan `event_exceptions`
  (lewati atau ubah satu kejadian). Perhitungan kejadian acara di rentang tanggal ada di
  `domain/` dengan unit test, termasuk setiap N minggu, Senin kedua, tanggal 31 di bulan
  pendek, dan 29 Februari.
- Kalender HP dibaca lewat `CalendarContract.Instances` (pengulangan sudah dijabarkan oleh
  sistem), izin `READ_CALENDAR`. Setiap kalender HP diberi label Kerja atau Pribadi di
  pengaturan. Tanpa izin, fitur ini mati dan acara Habitflow tetap jalan.
- Daftar libur nasional dan cuti bersama per tahun disimpan sebagai file JSON di app
  (`assets/libur/<tahun>.json`, sumber SKB 3 Menteri), diperbarui lewat update app. Setelah
  tahap 19B, server bisa mengirim daftar terbaru. Libur nasional masuk `days_off` dengan
  sumber "nasional" dan bisa dibatalkan per tanggal (misalnya kantor tetap masuk saat cuti
  bersama).
- `follow_ups` mendapat kolom tautan acara dan tanggal kejadian.

**Selesai jika:**
- Membuat acara "Meeting reguler" setiap 2 minggu hari Selasa 14.00 menampilkan kejadian
  yang benar di Agenda dan timeline, dan pengingat muncul 14.00 − 15 menit.
- Melewati satu kejadian dan mengubah satu kejadian tidak mengubah kejadian lain.
- Unit test pengulangan lulus, termasuk kasus tanggal 31 dan 29 Februari.
- Kalender HP yang dicentang tampil di dashboard dan Agenda tanpa notifikasi dari Habitflow.
  Yang tidak dicentang tidak tampil. Tanpa izin kalender, app tetap jalan.
- Di tanggal libur nasional, blok dan acara Kerja mati otomatis, sholat dan alarm Subuh tetap.
  Membatalkan libur untuk satu tanggal mengembalikan blok Kerja hari itu.
- Catatan cepat saat acara Kerja berlangsung tertaut ke acara itu, dan saat EOD terkumpul per
  acara.

**Hasil verifikasi (10 Oktober 2026, emulator Pixel 6 API 34, akun Google nyata di emulator):**
- Tab Kerja punya tombol ketiga "Agenda". Agenda 7 hari dan tampilan Bulan tampil sesuai desain. Acara "M" (Kerja, mingguan,
  Sabtu 08.00, 60 menit) yang dibuat lewat editor tampil di Agenda dengan keterangan "Kerja · Setiap minggu (Sab)", dan di
  tampilan Bulan titiknya jatuh di 10, 17, 24, dan 31 Oktober.
- Acara yang sama muncul di kartu Sekarang/Berikutnya dengan penanda "Acara · Kerja" dan "Mulai 08.00".
- Tentang, Kalender HP: izin sudah diberikan, kelima kalender akun tampil dengan switch, menyalakannya memunculkan chip Kerja
  dan Pribadi (bawaan Pribadi).
- Catat cepat: pukul 08.01, saat acara berlangsung, sheet Catat menampilkan "Tertaut ke acara: M". Catatan "Notulen" masuk
  Inbox dengan keterangan "Dari acara: M", dan di EOD diberi judul kelompok "Dari acara: M".
- Uji ulang setelah emulator di-boot ulang dan app dipasang ulang (08.37 sampai 09.01): tautan "Tertaut ke acara: M" tetap
  muncul selama acara berlangsung (08.39), catatan baru ("Kirim agenda") tersimpan dengan "Dari acara: M", catatan lama tetap
  ada, dan pukul 09.01, setelah acara selesai, baris tautan hilang dari sheet Catat.
- Unit test seluruh modul lulus (termasuk empat tes baru pengelompokan per acara).
- Temuan: tiga tombol di tab Kerja awalnya membuat "Daily scrum" membungkus jadi dua baris; diperbaiki dengan padding
  dalam 8dp dan teks satu baris. Jam di baris Agenda diperbesar ke bodyLarge supaya sejajar dengan judul.

**Belum diverifikasi:** notifikasi pengingat 15 menit sebelum acara (acara uji dibuat setelah waktu pengingatnya lewat);
menu kejadian (Ubah kejadian ini, Lewati, Kembalikan, Hapus) dan pengulangan setiap 2 minggu; libur nasional pada tanggal
liburnya (dan Batalkan/Libur lagi di kartu Sekarang) karena tidak ada libur di sekitar tanggal uji; tampilnya acara kalender
HP di Agenda dan timeline (kalender akun di emulator tidak punya acara yang dinyalakan); acara Kerja mati saat hari libur.
Semua itu sudah dicakup unit test untuk logikanya, tapi belum dilihat di layar.

### Tahap 23: Asupan makan (diputuskan 5 Oktober 2026, dirinci 10 Oktober 2026, selesai dikoding dan diuji sebagian di emulator, 10 Oktober 2026)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Asupan makan.

**Keputusan tambahan (10 Oktober 2026)**, yang menggantikan rencana awal bila bertentangan:

| Pertanyaan | Keputusan |
|---|---|
| Banyaknya catatan makan | Satu catatan per tanggal dan jenis (sarapan, siang, malam, camilan). Menekan chip yang sudah dicatat membuka catatan itu untuk diubah atau dihapus |
| Jam makan | Jam catatan (bawaan: sekarang) bisa diubah di sheet. Jam inilah yang dipakai aturan "makan malam" |
| Penghitung kopi dan manis | Tabel `drink_counts` yang sama dengan air, jenis `COFFEE` dan `SWEET`. Tombol "+ Kopi", "+ Manis" dan "−" di kartu air |
| Kopi ke-3 | Teks tenang di bawah kartu air (bodySmall, `onSurfaceVariant`): "Kopi hari ini sudah 3 gelas, di atas batas 2." Tanpa warna status dan tanpa dialog |
| Sumber otomatis habit | Empat nilai baru di `HabitAutoSource`: `DINNER`, `NO_FRIED_SWEET`, `NO_SWEET_DRINK`, `COFFEE`. Migrasi 7 ke 8 mengisinya lewat nama habit seed (seperti tahap 20 dan 21) |
| Kapan dievaluasi | Saat batas tidur (alarm blok Batas tidur memicu `ScheduleNotifier.refresh`), dan saat app dibuka untuk kemarin serta hari ini yang sudah lewat batas tidur. Evaluasi **hanya menambah centang, tidak pernah menghapus**, dan bisa diulang aman |
| Centang manual menang | Setiap centang atau batal-centang manual mencatat tanda di tabel `habit_manual_marks` (habit, tanggal). Evaluasi melewati pasangan yang bertanda |
| Hari tanpa catatan makan | "Tanpa gorengan atau camilan manis" **tidak dicentang** kalau hari itu tidak ada catatan makan sama sekali (tidak ada data, bukan bukti). "Makan malam" juga tidak dicentang tanpa catatan malam. Kopi dan minuman manis memakai penghitung, jadi hitungan 0 berarti dicentang |
| Batas tidur | Jam mulai blok "Batas tidur" yang aktif hari itu (22.00 bawaan). Tanpa blok itu, 22.00 |
| Pengingat | Satu notifikasi Info senyap pada Batas tidur − 60 menit (21.00), isi "Belum dicatat: makan siang, makan malam." Hanya sarapan, siang, dan malam yang dihitung (camilan tidak wajib). Tidak muncul kalau ketiganya sudah dicatat. Switch "Pengingat catatan makan" di Tentang, bawaan menyala |
| Ringkasan mingguan | Tab Progres, bagian Kesehatan, tujuh hari terakhir termasuk hari ini: hari Isi Piringku lengkap (karbo, lauk, sayur, buah semua ada di makan siang **atau** malam), jumlah catatan bergorengan, bermanis, dan total kopi |
| Snapshot | `data.meals`, `data.habitManualMarks`, dan jenis baru di `data.drinkCounts`. Opsional saat dibaca, `schemaVersion` tetap 1 |

| Pertanyaan | Keputusan |
|---|---|
| Cara catat | Isi Piringku per waktu makan: tap komponen karbo, lauk, sayur, buah, plus tanda gorengan dan manis. Teks opsional. Tanpa hitung kalori |
| Habit makan | Dicentang otomatis dari catatan, tetap bisa diubah manual |
| Kopi dan minuman manis | Penghitung seperti air, tombol cepat di kartu air dashboard |
| Pengingat | Satu ringkasan Info sebelum batas tidur, hanya kalau ada waktu makan yang belum dicatat |

**Aturan centang otomatis** (diputuskan saat batas tidur, atau saat app dibuka setelahnya):
- "Makan malam selesai 2-3 jam sebelum tidur": tercentang kalau makan malam dicatat paling
  lambat 2 jam sebelum blok Batas tidur (20.00 untuk batas 22.00).
- "Tanpa gorengan atau camilan manis": tercentang kalau tidak ada catatan makan bertanda
  gorengan atau manis hari itu.
- "Tanpa minuman manis": tercentang kalau penghitung minuman manis hari itu 0.
- "Ngopi maksimal 2 gelas (sepulang kerja)": tercentang kalau penghitung kopi ≤ 2. Kopi ke-3
  memunculkan pesan tenang, bukan peringatan.
- Centang manual dari pengguna selalu menang atas hasil otomatis.

**Rencana teknis:**
- Tabel `meals` (tanggal, waktu, jenis: sarapan, siang, malam, camilan, komponen karbo, lauk,
  sayur, buah, tanda gorengan dan manis, catatan) dan `drink_counts` (tanggal, jenis: air,
  kopi, minuman manis, jumlah). Penghitung air dari tahap 20 ikut memakai tabel ini.
- Habit punya sumber otomatis (diperluas dari tahap 21): langkah, makan malam, tanpa
  gorengan/manis, minuman manis, kopi. Aturannya di `domain/` dengan unit test.
- Centang otomatis dijalankan oleh alarm di blok Batas tidur dan saat app dibuka keesokan
  harinya kalau alarm terlewat. Centang manual ditandai supaya tidak ditimpa.

**Selesai jika:**
- Mencatat satu waktu makan selesai dalam beberapa tap dari dashboard.
- Unit test keempat aturan centang otomatis lulus, termasuk batas 20.00 dan kopi tepat 2.
- Habit makan tercentang atau tidak sesuai aturan saat batas tidur, dan centang manual tidak
  ditimpa.
- Ringkasan malam hanya muncul kalau ada waktu makan yang belum dicatat.
- Tab Progres bagian Kesehatan menampilkan ringkasan mingguan: berapa hari Isi Piringku
  lengkap (keempat komponen di makan siang atau malam), jumlah gorengan, manis, dan kopi.

**Hasil verifikasi (10 Oktober 2026, emulator Pixel 6 API 34, database v7 berisi data lama, jam palsu lewat `DebugClockReceiver`):**
- Migrasi 7 ke 8 pada data yang sudah ada berhasil: app terbuka tanpa crash, habit, tensi, dan acara lama utuh, kartu Makan dan
  penghitung Kopi dan Manis muncul.
- Dashboard: "+ Kopi" tiga kali menampilkan "Kopi hari ini sudah 3 gelas, di atas batas 2." tanpa dialog. Sheet Siang dengan keempat
  komponen disimpan dalam beberapa tap dan chip berubah jadi "✓ Siang 09.08".
- Pengingat: dengan jam palsu 21.00 muncul notifikasi "Catat makan hari ini · Belum dicatat: sarapan, makan malam." (siang sudah
  dicatat).
- Centang otomatis: dengan jam palsu 22.00 "Tanpa minuman manis" dan "Tanpa gorengan atau camilan manis" tercentang; "Ngopi maksimal
  2 gelas" (kopi 3) dan "Makan malam" (belum dicatat) tidak. Setelah "Tanpa minuman manis" dibatalkan sendiri, evaluasi jam palsu
  22.30 tidak mencentangnya lagi (centang manual menang).
- Progres, Kesehatan: "Piring lengkap 1 dari 7 hari", "Gorengan 0 · Manis 0 · Kopi 3 gelas", "Dicatat di 1 dari 7 hari".
- Tentang: bagian "Catatan makan" dengan switch pengingat tampil.
- Unit test seluruh modul lulus (267 tes, 0 gagal): 18 tes aturan makan baru termasuk batas 20.00 dan 20.01, kopi tepat 2 dan 3,
  hari tanpa catatan, dan ringkasan mingguan; dua tes snapshot baru untuk bidang tahap 23.

**Belum diverifikasi:** alarm batas tidur sungguhan pukul 22.00 (hanya lewat jam palsu); pengingat 21.00 lewat alarm sungguhan;
"Hapus catatan" dan mengubah catatan yang sudah ada di sheet; pemulihan snapshot yang berisi catatan makan ke HP lain (hanya
dites di tingkat kode JSON); tampilan empat chip Makan yang semuanya tercatat di layar 360dp; centang otomatis untuk "kemarin"
saat app baru dibuka pagi hari; switch pengingat dimatikan lalu notifikasi tidak muncul.

### Tahap 24: Asisten AI (diputuskan, belum dikoding)

Butuh tahap 19B (server dan sinkron) selesai lebih dulu.

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Asisten AI.

| Pertanyaan | Keputusan |
|---|---|
| Fitur | Ringkasan mingguan, tanya jawab atas data sendiri, bantu EOD dan daily scrum, saran pola otomatis |
| Model | **Claude Sonnet 5.5** (`claude-sonnet-5-5`), dipilih Roziq karena lebih hemat |
| Data yang dikirim | Semua data: habit, jadwal, follow-up kerja, kesehatan. Hanya saat fitur AI dipakai |
| Batas biaya | **$5 per bulan**, dihitung di server |
| Jalur | Lewat backend Laravel tahap 19B. HP tidak pernah memegang API key |

**Rencana teknis:**
- Backend Laravel memanggil Claude API dengan SDK resmi Anthropic untuk PHP. API key
  disimpan di environment server. Endpoint Habitflow di server dilindungi token yang sama
  dengan sinkron 19B.
- AI membaca data lewat **tool use**, bukan seluruh data sekaligus. Alat yang direncanakan:
  ambil habit dan riwayat (rentang tanggal), ambil follow-up (status, orang, rentang),
  ambil catatan EOD, ambil kesehatan (langkah, berat, tensi, makan, rentang), ambil jadwal dan
  acara (tanggal). Semua alat hanya membaca. AI tidak pernah mengubah data sendiri.
- Bantu EOD menghasilkan **usulan** (status follow-up, perapian Inbox, draf catatan EOD dan
  rencana besok) yang harus disetujui di HP sebelum disimpan.
- **Prompt caching**: instruksi sistem dan daftar alat dibuat tetap (tanpa tanggal atau data
  yang berubah di dalamnya), lalu diberi cache. Tanggal hari ini dan pertanyaan diletakkan
  setelah bagian yang di-cache.
- Pengaturan request: adaptive thinking, effort `low` untuk tanya jawab dan bantu EOD,
  `medium` untuk ringkasan mingguan dan saran pola. Streaming untuk tanya jawab. Tidak
  memakai forced `tool_choice` (ditolak model ini). Pakai `tool_choice` auto, `strict: true`
  pada alat, dan fallback server-side mode default untuk penolakan.
- Ringkasan mingguan dan saran pola dibuat oleh jadwal di server (Minggu malam), disimpan di
  server, dan diambil HP saat sinkron berikutnya. Saran pola tampil sesekali di dashboard.
- **Anggaran**: server mencatat `usage` setiap request dan menghitung biaya per bulan. $1
  dicadangkan untuk ringkasan mingguan dan bantu EOD. Saat sisa anggaran mencapai cadangan,
  tanya jawab dan saran pola berhenti sampai bulan berikutnya, dengan pesan tenang di app.
- Tanpa internet, fitur AI tidak tersedia dan app tetap jalan seperti biasa.

**Perkiraan biaya** (Sonnet 5.5, $2 input dan $10 output per 1 juta token): ringkasan
mingguan sekitar $0,08 per minggu, bantu EOD sekitar $0,02 per hari kerja, tanya jawab
sekitar $0,025 per pertanyaan. Dengan batas $5, cukup untuk sekitar 5 pertanyaan per hari.
Angka ini perkiraan kasar dan diperiksa ulang dari `usage` setelah sebulan dipakai.

**Selesai jika:**
- HP memanggil AI hanya lewat server. Tidak ada API key Anthropic di APK.
- Tanya jawab menjawab pertanyaan tentang follow-up, habit, dan kesehatan memakai alat, dan
  menyebut tanggal atau angka yang bisa dicek di app.
- Bantu EOD menghasilkan usulan yang bisa disetujui, diubah, atau ditolak per item. Tidak ada
  data yang berubah tanpa persetujuan.
- Ringkasan mingguan muncul di app setelah sinkron Senin pagi.
- `cache_read_input_tokens` lebih dari 0 pada pertanyaan kedua dan seterusnya dalam satu sesi.
- Pencatatan biaya di server cocok dengan `usage` dari API, dan batas $5 menghentikan tanya
  jawab saat cadangan tercapai.
- Tanpa internet, layar AI menampilkan pesan dan bagian lain app tidak terganggu.

### Tahap 25: Infrastruktur MySQL dan Akun Web (selesai, 10 Oktober 2026)

Dibahas dan selesai 10 Oktober 2026. Fondasi untuk tahap 26 dan 27 — dikerjakan lebih dulu
karena keduanya butuh ini. Awalnya didiskusikan bareng 26-27 sebagai "tahap 25" tunggal,
dipecah jadi 3 tahap terpisah supaya progress tiap bagian bisa dilacak sendiri-sendiri.

**Revisi arsitektur login** (dua kali, dalam sesi yang sama): rencana awal "Google Sign-In
saja" ternyata diubah jadi **email/password sebagai cara utama, Google sebagai tambahan**
— baik langsung di halaman Login/Register, maupun di-connect belakangan dari Dashboard.
Keputusan final:

| Pertanyaan | Keputusan |
|---|---|
| Database | Pindah dari SQLite ke **MySQL**, di-deploy sebagai container `shared-mysql` di VM rumah (network `shared-db`, lihat [repo `server`](https://github.com/elahady/build-server)) — dipakai bareng dengan project lain di VM yang sama kalau nanti perlu |
| Login | **Email/password** sebagai cara utama (halaman Login dan Register beneran, dengan validasi dan rate limit). **Google** tersedia di kedua halaman itu juga (tombol "Masuk/Daftar dengan Google") **dan** bisa di-connect/diputus belakangan dari Dashboard untuk user yang sudah daftar pakai password. Satu `GoogleAuthController` menangani keduanya (dibedakan dari `Auth::check()` saat callback) karena Google Cloud Console cuma punya satu redirect URI terdaftar |
| Pencocokan akun | Kalau login pakai Google dan email-nya sudah terdaftar (lewat password), **ditautkan ke akun yang sama** (dicocokkan `google_sub` dulu, lalu `email`) — tidak membuat akun duplikat |
| API Android | **Tetap Google-only**, tidak berubah — `POST /api/v1/auth/google` beda jalur dari web, pakai `UserToken` terpisah dari sesi web. *Digantikan tahap 31: API Android tidak lagi Google-only, tambah email/kata sandi, lihat kontrak 31A* |
| Framework web | **Livewire**, di dalam Laravel app yang sama dengan API (`server/`). Bukan SPA terpisah |
| Repo | **Repo yang sama** (`habitflow`), bukan repo baru — satu codebase, satu migrasi, satu deploy |
| Deploy | Container Docker sendiri di VM (pola sama seperti roziqrizalcom): `serversideup/php:8.3-fpm-nginx`, connect ke `shared-mysql`. Satu container melayani web Livewire **dan** API `/api/v1/...` sekaligus |
| Domain | **`habitflow.roziqrizal.com`**, lewat tunnel `home-server` yang sudah ada |
| Desain halaman Login/Register | Ikon app + font Libre Caslon Text/Manrope + palet sage dari `docs/design/README.md` (bukan desain generik), plus narasi diambil dari `docs/visi-super-app.md` ("asisten harian yang tahu sedang di blok apa") |

**Skema `users` final**: `id`, `google_sub` (nullable, unik), `email` (unik), `password`
(nullable — kosong untuk user yang cuma pernah pakai Google), `name`, `avatar`,
`remember_token`, timestamps.

**Langkah bangun — semua selesai:**

1. ✅ **Infrastruktur**: `shared-mysql` jalan di VM (network `shared-db`), database
   `habitflow` + user dedicated.
2. ✅ **Migrasi dasar + pindah DB**: tabel `users`, `api_tokens`, `snapshots` jalan di
   MySQL. Data lama (token, snapshot 19B) tetap utuh.
3. ✅ **Google OAuth setup**: project `Habitflow` di Google Cloud Console, OAuth consent
   screen (External, test users: Roziq + istri), Client ID **Web** dan **Android**.
   Kredensial di `.env.production` (gitignored), dipetakan lewat `config/services.php`.
4. ✅ **Auth backend**: `RegisteredUserController` (register), `AuthenticatedSessionController`
   (login/logout email-password, rate limit 5x/menit), `GoogleAuthController` (login/
   connect/disconnect Google, satu jalur untuk keduanya). Tabel `user_tokens` untuk API
   Android (mirip `api_tokens` 19B tapi terikat ke user).
5. ✅ **Livewire shell**: `Dashboard` component + layout, halaman Login/Register dengan
   desain dan narasi final.
6. ✅ **Deploy ke VM**: container `habitflow` live, migrasi jalan, domain
   `habitflow.roziqrizal.com` aktif.

**Terverifikasi end-to-end** (10 Oktober 2026): register, login email/password, dan
connect Google semua dicoba langsung oleh pemilik akun dan berhasil.

**Catatan untuk tahap 26-27**: user bisa login tanpa pernah connect Google (cuma
email/password) — kalau nanti ada fitur yang butuh identitas Google tertentu, perlu
dicek `auth()->user()->google_sub` tidak null dulu.

### Tahap 26: To-Do Tim — kolaborasi dengan pasangan (selesai total - web, API, Android, 10 Oktober 2026)

Dibahas 10 Oktober 2026. Butuh tahap 25 (akun, MySQL, Livewire) selesai lebih dulu.

| Pertanyaan | Keputusan |
|---|---|
| Lingkup | **Hanya to-do rumah tangga** yang dibagi tim. Habit, follow-up kerja (tahap 27), jadwal tetap personal, tidak ikut tim |
| Letak di app | **Tab terpisah** ("Tugas Rumah" atau nama lain), bukan menyatu dengan Hari ini/habit yang sudah ada. Desain yang sudah jalan tidak disentuh |
| Kolaborasi | User A buat tim, dapat **link undangan** (token panjang & acak, misal 32 karakter hex — bukan kode pendek yang gampang ditebak). Dikirim manual lewat WhatsApp/apa saja (app tidak punya sistem pesan sendiri). **Sekali pakai** dan **kedaluwarsa 48 jam**. User B buka link, login dengan Google kalau belum, klik "Gabung tim" |
| Jumlah anggota | Tidak dibatasi ketat di desain (bisa 2 untuk pasangan, tapi skema tidak perlu hardcode "cuma 2") |
| Data tim | To-do: judul, **ditugaskan ke siapa** (`assigned_to`, boleh kosong = belum ditugaskan), selesai/belum, siapa yang buat, siapa yang selesaikan, tanggal. Semua anggota tim bisa tambah/edit/hapus/centang/menugaskan item manapun |
| Sinkron | **Bukan** snapshot — realtime-ish lewat polling (misal tiap buka tab/tarik refresh), bukan WebSocket (kompleksitas tidak sepadan untuk kebutuhan to-do rumah tangga) |
| Akses | Dari app Android (tab baru) **dan** web |
| Notifikasi | Saat ditugaskan tugas baru: notifikasi senyap (pola sama seperti pengingat kerja tahap 20), bukan push instan. Diperiksa lewat **perluasan job WorkManager yang sudah ada** (polling ±5 menit untuk sinkron 19B), bukan infrastruktur push baru (Firebase dkk) |

**Rencana teknis:**
- Tabel baru: `teams` (nama), `team_members` (user_id, team_id), `team_invites` (team_id,
  token panjang-acak, dibuat_oleh, kedaluwarsa_pada, dipakai_pada — null kalau belum
  dipakai), `team_todos` (team_id, title, assigned_to nullable, done, created_by, done_by,
  created_at).
- Endpoint: `/api/v1/teams` (buat tim), `/api/v1/teams/invites` (buat link undangan),
  `/api/v1/teams/invites/:token/accept` (klik link → gabung, tolak kalau kedaluwarsa/sudah
  dipakai), `/api/v1/teams/:id/todos` (CRUD to-do tim, termasuk ubah `assigned_to`).
- Token per-HP (19B) dan akun Google (tahap 25) **dua hal berbeda**: token lama tetap jalan
  untuk snapshot personal yang belum pakai akun. User yang mau pakai To-Do Tim wajib login
  Google dulu — bisa jalan berdampingan (app punya 2 jalur: token-only untuk data personal
  lama, akun untuk fitur tim baru).
  *Digantikan tahap 31: login akun wajib untuk semua fitur, bukan cuma To-Do Tim — dua jalur di atas jadi satu jalur akun.*
- App Android: tab "Tugas Rumah" baru dengan daftar to-do tim (kelompokkan per assignee atau
  tampilkan avatar/nama di tiap baris) + tombol tambah, state kosong "Belum gabung tim"
  dengan tombol "Buat tim" / tempel link undangan. Job WorkManager yang sudah ada diperluas:
  tiap siklus polling, cek juga to-do tim yang baru ditugaskan ke user dan belum
  diberitahu, lalu tampilkan notifikasi senyap per item baru.
- Link undangan dibuka dari luar app (WhatsApp dll) perlu **deep link** Android
  (`App Links`/intent filter) supaya langsung buka ke layar "Gabung tim" di app kalau
  terpasang, fallback ke web kalau belum.

**Langkah bangun:**

1. ✅ **Teams + invite (web)** — selesai, 10 Oktober 2026: migrasi `teams`, `team_members`,
   `team_invites` jalan di MySQL. Halaman `/tugas-rumah` (Livewire `Teams`) buat tim dan
   buat link undangan; halaman `/invite/{token}` terima undangan (cek kedaluwarsa/sudah
   dipakai). Nav "Tugas Rumah" ditambah di layout. **Belum ada endpoint API** untuk
   Android di langkah ini - menyusul bareng langkah 3 (To-Do Tim) supaya app Android tidak
   perlu update dua kali.
2. ✅ **Terima undangan dari Android** — selesai, 10 Oktober 2026: deep link (App Links)
   `https://habitflow.roziqrizal.com/invite/{token}` buka layar "Gabung tim" di app lewat
   intent-filter `autoVerify="true"` di `MainActivity` + `server/public/.well-known/assetlinks.json`.
   Tab "Tugas Rumah" juga punya kolom tempel-link manual sebagai cadangan kalau App Links
   belum/tidak terverifikasi di suatu HP (lihat catatan verifikasi di bawah).
3. ✅ **To-Do Tim (web + API)** — selesai, 10 Oktober 2026: migrasi `team_todos`
   (`assigned_to` nullable) jalan di MySQL. Halaman `/tugas-rumah` bisa tambah, centang,
   tugaskan (dropdown anggota), dan hapus to-do. Endpoint API
   (`GET/POST /api/v1/teams/{team}/todos`, `PUT/DELETE /api/v1/teams/todos/{todo}`) sudah
   dibangun bareng, siap dipakai Android begitu langkah 2 dikerjakan.
4. ✅ **Notifikasi tugas** — selesai, 10 Oktober 2026: worker periodik baru `TeamPollWorker`
   (tiap 5 menit, hanya aktif kalau sudah login) memanggil `GET /api/v1/teams` lalu
   `GET /api/v1/teams/{team}/todos`, dan menotifikasi (channel Info, senyap) to-do yang baru
   ditugaskan ke pengguna dan belum pernah dinotifikasi.

**Keputusan saat membangun (10 Oktober 2026):**
- **Tab "Tugas Rumah" penuh dibangun sekaligus** (bukan cuma deep link + notifikasi polos),
  supaya tap notifikasi dan join dari deep link punya tempat untuk menampilkan hasilnya -
  lihat `TeamScreen.kt`. Mencakup: state kosong (buat tim / tempel link undangan), header tim
  + tombol Undang (buka share sheet Android), tambah/centang/tugaskan (dropdown
  anggota)/hapus to-do. Tidak ada rename judul to-do (tidak diputuskan di rancangan).
- **Worker periodik terpisah** (`TeamPollWorker`, 5 menit) dipakai, bukan memperluas
  `SyncWorker` 19B seperti disebut di rencana awal - tanggung jawabnya beda (cadangan
  snapshot vs tim), jadi dipisah supaya tidak bercampur. Pola penjadwalannya sama dengan
  `HabitSyncScheduler` (tahap 28): aktif/nonaktif mengikuti status login.
- **`Account` (tahap 25) ditambah field `id`** (user id numerik dari server) - dibutuhkan
  untuk mencocokkan `assigned_to` to-do tim dengan pengguna yang sedang login. Akun yang
  tersimpan dari sebelum perubahan ini (tanpa `id`) dianggap logout sekali setelah update
  (field hilang → `readAccount()` mengembalikan null) - bukan kehilangan data, cuma perlu
  masuk Google lagi sekali.
- **`MainActivity` jadi `launchMode="singleTask"`** supaya tidak membuat instance baru saat
  dibuka dari deep link ketika app sudah berjalan di latar belakang.
- Tidak ada deteksi konflik (409) untuk to-do tim, sesuai keputusan awal tahap ini
  ("realtime-ish lewat polling").

**Catatan verifikasi App Links (butuh deploy untuk tuntas):**
- Fingerprint SHA-256 di `assetlinks.json` saat ini **hanya debug keystore mesin ini**
  (`~/.android/debug.keystore`, dapat dari `keytool -list -v`). **Release keystore tidak ada
  di mesin ini** (`~/.habitflow/keystore.properties` tidak ditemukan) - begitu APK rilis
  ditandatangani dari mesin yang punya keystore-nya, tambahkan fingerprint SHA-256-nya juga
  ke `assetlinks.json` (daftar `sha256_cert_fingerprints` boleh lebih dari satu).
- Diuji di HP fisik Xiaomi/MIUI dengan **intent eksplisit** (`adb shell am start -a
  android.intent.action.VIEW -d "https://habitflow.roziqrizal.com/invite/test-token" com.roziqrizal.habitflow`)
  supaya tidak bergantung ke verifikasi domain - hasil: app terbuka langsung ke
  `JoinTeamDialog`, menampilkan "Masuk dengan Google dulu untuk bisa bergabung ke tim." (akun
  belum login di sesi uji ini), tidak ada crash.
  *Digantikan tahap 31: setelah login wajib, keadaan "belum login" ini tidak mungkin lagi tercapai di tab manapun — dialog ini dihapus di 31H.*
- ✅ **App Links terverifikasi otomatis** - diuji 10 Oktober 2026 setelah `assetlinks.json`
  di-deploy ke VM (`https://habitflow.roziqrizal.com/.well-known/assetlinks.json` membalas
  200). Catatan deploy: arsip `tar` dari PowerShell Windows (bsdtar) **melewatkan folder
  `.well-known`** (folder berawalan titik) dua kali berturut-turut - solusinya `scp` file itu
  langsung (bukan lewat `tar`) ke `~/apps/habitflow/public/.well-known/` baru `docker compose
  build` + `up -d --force-recreate`. `adb shell pm get-app-links com.roziqrizal.habitflow`
  awalnya menunjukkan status gagal (`1024`, dari percobaan verifikasi saat app pertama
  di-install sebelum file ter-deploy) - `adb shell pm verify-app-links --re-verify
  com.roziqrizal.habitflow` memaksa verifikasi ulang dan statusnya jadi `verified`. Tes akhir:
  `adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d
  "https://habitflow.roziqrizal.com/invite/..."` **tanpa menyebut package** (persis seperti
  tap link dari WhatsApp) langsung membuka `MainActivity` dan menampilkan `JoinTeamDialog`,
  tanpa pemilih app dan tanpa membuka browser.
- **Masih belum diuji**: join sungguhan dengan dua akun Google dan token undangan asli dari
  web (yang diuji di atas cuma token uji/kedaluwarsa dan path "belum login").

**Belum diputuskan** (tidak menghalangi mulai):
- Apakah to-do tim butuh kategori/label, atau cukup daftar datar dulu.
- Apakah anggota tim bisa di-remove, dan siapa yang berhak (pembuat tim saja, atau semua
  anggota).
- Apakah assignee bisa lebih dari satu orang per tugas, atau selalu satu orang/kosong.

**Selesai jika:**
- ✅ User A buat tim, buat link undangan; User B buka link dan gabung, keduanya lihat
  to-do tim yang sama. Link kedaluwarsa atau sudah dipakai ditolak dengan pesan jelas.
  (Diuji di web untuk alur lengkap. Di Android, App Links terverifikasi dan link sungguhan
  membuka dialog gabung langsung di app; join dua akun sungguhan belum diuji - lihat catatan
  verifikasi App Links di atas.)
- ✅ Tambah/centang/hapus/tugaskan to-do tim langsung kelihatan di web setelah
  refresh/buka ulang, dan di Android lewat tab "Tugas Rumah" (polling manual tiap buka tab,
  tidak otomatis realtime).
- ✅ Tugas baru yang ditugaskan ke user memicu notifikasi senyap, lewat `TeamPollWorker`
  tiap 5 menit (kode selesai; belum diuji end-to-end dengan dua akun sungguhan).
- ✅ Data personal (habit, jadwal, dll) tidak terpengaruh sama sekali — masih snapshot
  seperti sebelumnya.

**Status Tahap 26: web, API, dan Android (langkah 1-4) selesai dikoding, lulus
`assembleDebug`/`testDebugUnitTest`, terpasang di HP fisik, dan App Links sudah terverifikasi
+ di-deploy ke VM. Yang belum: uji end-to-end join dua akun Google sungguhan (lihat catatan
verifikasi di atas).**

### Tahap 27: Web untuk Follow-up Kerja (selesai dan diuji sebagian di HP fisik, 11 Oktober 2026)

Dibahas 10 Oktober 2026. Butuh tahap 25 (akun, MySQL, Livewire) selesai lebih dulu. Bisa
dikerjakan sebelum atau sesudah tahap 26 — tidak saling bergantung.

| Pertanyaan | Keputusan |
|---|---|
| Lingkup | **Hanya follow-up kerja** (tahap 19) yang dapat CRUD penuh dari web. Habit, jadwal, kesehatan, EOD, dll **tetap** model snapshot lama (HP sumber data, server cuma backup) |
| Alasan dipisah | Follow-up kerja paling masuk akal dikerjakan dari desktop (kerja di depan laptop, bukan pegang HP). Fitur lain tidak butuh ini, jadi tidak perlu ubah model sync-nya |
| Model data | Follow-up kerja pindah dari "field di dalam snapshot JSON" jadi **tabel sungguhan** di MySQL (`follow_ups`), API CRUD biasa (bukan snapshot) |
| Siapa yang akses | Satu-satunya pemilik data (login dengan akun Google yang sama dari tahap 25) |
| Sinkron ke HP | HP tetap simpan salinan lokal (offline-first tidak berubah), tapi follow-up kerja sekarang **sumber kebenarannya di server**, bukan snapshot HP. HP fetch/push lewat API langsung, bukan ikut snapshot 19B lagi |
| Konflik | **Deteksi, bukan timpa diam-diam.** Client kirim `updated_at` yang terakhir dilihat saat PUT; kalau beda dari yang di server (ada perubahan lain masuk duluan), server tolak (409) dan client tanya user: "Berubah di tempat lain (jam segini) — timpa, atau pakai yang terbaru?" |

**Rencana teknis:**
- Tabel `follow_ups` (MySQL) menggantikan `data.followUps` di snapshot untuk user yang
  sudah pakai tahap ini. Kolom sama dengan struktur snapshot sekarang (`id`, `title`,
  `status`, `date`, `time`, `person`, `note`, `createdAt`, `doneAt`, `pickedDate`) ditambah
  `user_id`, `updated_at`.
- Endpoint baru `/api/v1/follow-ups` (GET list, POST, PUT `:id`, DELETE `:id`) — CRUD biasa,
  beda dari pola snapshot 19B yang cuma `PUT /snapshot`. `PUT` wajib sertakan `updated_at`
  yang terakhir dilihat client (dipakai server untuk cek konflik, lihat baris Konflik di
  atas); tidak cocok → balas `409 Conflict` berisi versi terbaru server, bukan menerima
  begitu saja.
- Snapshot 19B **tetap jalan** untuk data lain (habit, jadwal, dll) — follow-up kerja
  dikeluarkan dari payload snapshot begitu tahap ini aktif, supaya tidak dobel sumber
  kebenaran.

**Langkah bangun:**

1. **Follow-up kerja ke MySQL**: migrasi `follow_ups` (pindah dari field snapshot jadi
   tabel sungguhan). Endpoint CRUD dengan deteksi konflik (`updated_at` + balas 409).
   Keluarkan `followUps` dari payload snapshot 19B supaya tidak dobel sumber kebenaran.

   ✅ **Sisi server selesai (11 Oktober 2026), belum di-deploy ke VM:** migrasi
   `2026_10_11_090000_create_follow_ups_table`, model `FollowUp`, `FollowUpController`, dan
   `/api/v1/follow-ups` (GET dengan filter `?status=`, POST, PUT, DELETE) di balik
   `user-token`. Kolom: `title`, `status` (`INBOX`/`ACTIVE`/`WAITING`/`DONE`, bawaan `INBOX`),
   `date`, `time` (`HH:mm`), `person`, `note`, `done_at`, `picked_date`, `event_id`,
   `event_date`, `event_title`. Waktu dibuat memakai `created_at` Laravel (bukan `createdAt`
   milidetik Android), `doneAt` jadi `done_at` bertipe timestamp. PUT **selalu** wajib
   menyertakan `updated_at` (beda dari habit/to-do yang hanya mewajibkannya saat ubah teks),
   tidak cocok dengan server → 409 berisi `follow_up` versi server. 10 feature test di
   `tests/Feature/FollowUpApiTest.php`, seluruh suite 28 test lulus.
   **Belum:** mengeluarkan `followUps` dari payload snapshot. Server menyimpan snapshot apa
   adanya tanpa menafsirkan isinya, jadi itu perubahan di Android dan dikerjakan bersama
   langkah 3 (selama Android masih mengirim `followUps` di snapshot, tidak ada yang rusak).
2. **Halaman web follow-up kerja**: Livewire CRUD + dialog resolusi konflik saat 409.

   ✅ **Selesai (11 Oktober 2026), belum di-deploy ke VM:** komponen `App\Livewire\FollowUps`
   di `/follow-up` (nav "Follow-up"). Catat cepat ke Inbox; daftar dikelompokkan Inbox, Aktif,
   Menunggu, Selesai (20 terbaru); centang untuk selesai/buka lagi (selesai mengisi `done_at`,
   buka lagi kembali ke Aktif); form ubah berisi judul, status, tanggal, jam, orang, catatan;
   hapus dengan konfirmasi; penanda "Terlambat" untuk Aktif/Menunggu yang tanggalnya lewat.
   **Konflik:** `editingStamp` menyimpan `updated_at` saat form dibuka. Saat Simpan dan di
   server sudah beda, perubahan **tidak** ditulis; muncul panel "Berubah di tempat lain" dengan
   versi server dan dua pilihan: "Timpa dengan punyaku" atau "Pakai yang terbaru" (buang
   perubahan form). Centang selesai/buka lagi dan hapus langsung tanpa cek konflik (aksi
   idempotent, seperti centang habit). 14 test di `tests/Feature/FollowUpsPageTest.php`, seluruh
   suite 42 test lulus. **Belum:** dilihat langsung di browser (hanya diuji lewat test Livewire).
3. **Android ikut pindah**: follow-up kerja di app fetch/push langsung ke endpoint baru
   (bukan ikut snapshot lagi), tangani respons 409 dengan dialog yang sama seperti web.

   ✅ **Kode selesai (11 Oktober 2026), 308 unit test lulus, diuji sebagian di HP fisik (lihat hasil di bawah):**
   Pola sama dengan tahap 28 langkah 5 (Room tetap sumber utama, offline-first, sinkron di latar belakang).
   - **Skema (migrasi Room 9 → 10):** `follow_ups` dapat `remoteId`, `remoteUpdatedAt`, `dirty` (baris
     lama otomatis `dirty = 1` supaya ikut terkirim begitu login), dan `conflictJson` (versi server saat
     bentrok). `pending_deletes` dapat jenis `follow_up`.
   - **Kode:** `HabitSyncClient` (list/create/update/delete follow-up), `RemoteFollowUp.kt` (model server,
     JSON bolak-balik, pemetaan ke entity), `HabitSyncManager` (push hapus → buat → ubah, pull ikut
     `syncNow()` yang sudah jalan saat app dibuka dan tiap 30 menit), `WorkRepository` (menjaga kolom sinkron
     saat simpan, mencatat penghapusan tertunda, `resolveConflict`). Tabel `follow_ups` ikut dipantau debounce
     push 1 menit di `HabitSyncScheduler`.
   - **Konflik 409 - beda dari habit/to-do (server menang):** versi server disimpan di `conflictJson`, baris
     itu dilewati push, dan tab Kerja menampilkan `FollowUpConflictDialog` dengan tiga pilihan: **Pakai
     punyaku** (`remoteUpdatedAt` diganti milik server lalu dikirim ulang, jadi menimpa server), **Pakai yang
     terbaru** (isi lokal diganti versi server, perubahan HP dibuang), atau **Nanti** (muncul lagi saat tab
     dibuka ulang).
   - **Dihapus dari web:** pull menghapus baris lokal yang pernah terkirim, tidak dirty, dan sudah tidak ada
     di server. Kalau barisnya masih dirty, push update menerima 404 lalu `remoteId` dilupakan dan dikirim
     ulang sebagai baru (edit lokal tidak hilang).
   - **Snapshot 19B:** `followUps` **tidak ikut diekspor kalau sudah login** (`Snapshot.followUpsOmitted`,
     kunci `followUps` tidak ditulis). Belum login tetap ikut, supaya follow-up tanpa akun tetap tercadangkan.
     Memulihkan snapshot yang tidak memuat `followUps` tidak menghapus follow-up di HP.
     *Digantikan tahap 31: login wajib berarti keadaan "belum login" tidak ada lagi — cabang `followUpsOmitted` yang mengecek itu dihapus di 31H (lihat `SyncManager.followUpsOnAccount`).*
   - **Batasan yang disadari:** memulihkan snapshot lama (yang memuat `followUps`) saat sedang login
     memasukkan baris tanpa `remoteId`, jadi terkirim sebagai item baru dan bisa dobel dengan yang sudah di
     server. Waktu dibuat dari HP tidak dikirim ke server (server memakai waktunya sendiri), hanya `done_at`
     yang dikirim. Tautan acara (`eventId`) dikirim apa adanya tapi hanya bermakna di HP.
   - **Belum diverifikasi (sebelum uji di HP, lihat hasil di bawah):** migrasi Room 9 → 10 di perangkat berisi data lama; push/pull end-to-end ke
     server; dialog bentrok di layar (uji: ubah di web, ubah di HP sebelum sinkron, lalu sinkron).

   ✅ **Hasil uji di HP fisik (Xiaomi 2311DRK48G, 11 Oktober 2026, akun login):**
   - Update APK di atas versi lama (database v9) berhasil: `user_version = 10`, keempat kolom baru ada
     (`dirty` default 1), app jalan tanpa crash. Tidak ada follow-up lama di HP uji, jadi migrasi baris
     berisi belum teruji.
   - Push buat: dua follow-up dicatat di HP, muncul di web, di HP `remoteId` terisi, `dirty = 0`, dan
     `remoteUpdatedAt` sama dengan `updated_at` server.
   - Bentrok: jaringan HP dimatikan (`adb shell svc wifi/data disable`), judul item diubah di server, judul
     yang sama diubah di HP, jaringan dihidupkan, Sinkron sekarang. Server membalas `PUT` dengan 409, HP
     menyimpan versi server di `conflictJson` tanpa menimpa, dan dialog "Berubah di tempat lain" muncul di tab
     Kerja.
   - "Pakai punyaku": `PUT` berikutnya 200, judul di server berubah ke versi HP, `dirty = 0`, `conflictJson`
     kosong.
   - **Masih belum diuji di HP:** "Pakai yang terbaru", tombol "Nanti", perubahan dari web yang ditarik ke HP
     tanpa bentrok, penghapusan dari web yang ikut menghapus di HP, dan hapus di HP yang ikut menghapus di server.

   ✅ **Server di-deploy ke VM (11 Oktober 2026)** lewat `server/DEPLOY.md`: migrasi `follow_ups` jalan tanpa
   error, container sehat, `/follow-up` mengarahkan ke login (302) dan `/api/v1/follow-ups` tanpa token
   membalas 401. Yang ikut ter-deploy: halaman web follow-up, API, dan toggle tema terang/gelap (commit
   `af9cbff`). **Belum:** APK Android versi baru dipasang di HP dan sinkron follow-up diuji end-to-end.

**Selesai jika:**
- Follow-up kerja bisa di-CRUD penuh dari web, dan HP yang fetch ulang melihat perubahan
  itu.
- Edit bentrok (server sudah berubah sejak client terakhir lihat) ditolak dengan 409 dan
  user diberi pilihan, bukan ditimpa diam-diam.
- Data personal lain (habit, jadwal, dll di luar follow-up kerja) tidak terpengaruh sama
  sekali — masih snapshot seperti sebelumnya.

## Arah baru: Web jadi versi lengkap, bukan cuma tambahan (diputuskan 10 Oktober 2026)

Setelah tahap 26-27 jalan, diputuskan arah yang lebih besar: **web bukan cuma tambahan
kecil (follow-up kerja, to-do tim), tapi harus jadi versi lengkap** dari semua yang ada
di app Android. Konsekuensinya dua hal mendasar berubah dari rencana awal:

1. **Semua fitur**, bukan cuma sebagian, akhirnya dapat tabel database + endpoint CRUD +
   halaman Livewire — dikerjakan bertahap, satu fitur jadi satu tahap, bukan sekaligus.
2. **Model snapshot (tahap 19B) ditinggalkan** untuk data yang sudah dimigrasi. HP dan
   web jadi **setara** — sama-sama baca/tulis langsung ke server, bukan lagi "HP
   mencadangkan ke server". Server/database jadi sumber kebenaran bersama, pola yang
   sama dengan follow-up kerja (tahap 27: deteksi konflik lewat `updated_at` + 409, bukan
   timpa diam-diam) dipakai lagi untuk tiap fitur yang dimigrasi.

Token per-HP tanpa akun (tahap 19B) tetap ada selama masih ada fitur yang belum
dimigrasi, dan endpoint `PUT /snapshot` tetap jalan untuk itu — begitu semua fitur sudah
pindah, baru dipertimbangkan apakah 19B dipensiunkan total.

> **Digantikan tahap 31 (11 Oktober 2026):** paragraf di atas sudah tidak berlaku persis seperti tertulis — sejak tahap 31,
> `PUT /snapshot`/`GET /snapshot/latest` memakai `user-token` (akun), bukan token per-HP tanpa akun (sub-tahap 31E). Token
> lama (`api_tokens`) dipensiunkan setelah snapshot lama diklaim ke akun pemilik, bukan menunggu semua fitur selesai dimigrasi
> seperti rencana awal di atas. Fitur yang belum dimigrasi (jadwal, kesehatan, makan, kalender, pengaturan, EOD) tetap lewat
> `PUT /snapshot`, hanya saja sekarang terikat akun.

### Peta migrasi (fitur → tabel yang dibutuhkan)

| Fitur | Tabel | Sumber tahap | Status |
|---|---|---|---|
| Follow-up kerja | `follow_ups` | 19 | **Tahap 27: selesai (server, web, Android); Android diuji sebagian di HP fisik** |
| To-do tim | `team_todos`, `teams`, `team_invites` | baru | **Selesai (web+API+Android) — tahap 26** |
| Habit + to-do harian | `habits`, `habit_entries`, `todos` | 1-9 | **Selesai (web+API+Android)** — tahap 28 |
| Jadwal harian & libur | `schedule_blocks`, `schedule_block_habits`, `days_off`, `holiday_cancellations` | 17, 22 | Sebagian: server `schedule_blocks`, API, cache waktu sholat, dan kartu web selesai (tahap 29 langkah 1-3); sinkron Android, `days_off`, dan libur belum |
| Daily scrum/EOD | `work_days` | 19 | Belum dimulai |
| Kesehatan (berat, tensi) | `weight_entries`, `blood_pressure_entries` | 21 | **Tahap 30 (satu arah, web hanya membaca, belum dikoding)**. Beda dari pola penuh: input hanya di Android, tanpa deteksi konflik |
| Asupan makan | `meals`, `drink_counts`, `habit_manual_marks` | 20, 23 | Belum dimulai |
| Kalender & acara | `events`, `event_exceptions` | 22 | Belum dimulai |
| Pengaturan (lokasi, adzan, tema, pengingat) | `settings` per user | berbagai | Belum dimulai |
| Langkah (steps) | Sumbernya sensor Health Connect di HP, web tidak punya akses, jadi **tidak ada migrasi dua arah**. Revisi (tahap 30): Android mengirim **total langkah per hari** (7 hari terakhir) ke server hanya untuk ditampilkan di web, tabel `step_days`. Habit yang tercentang otomatis dari langkah tetap ikut sinkron lewat `habit_entries` | 21, 30 | Tahap 30, belum dikoding |
| Asisten AI | Belum diputuskan skemanya — ini fitur interaktif (chat), beda sifat dari data yang disinkron biasa. Didiskusikan terpisah saat gilirannya tiba | 24 | Belum diputuskan |

**Setiap tahap migrasi mengikuti pola yang sama** (sudah terbukti di tahap 26-27):
1. Tabel MySQL + model Eloquent.
2. Endpoint API (`/api/v1/...`) dengan `user-token` middleware, termasuk deteksi konflik
   (`updated_at` + 409) untuk data yang bisa diedit dari 2 tempat.
3. Halaman/komponen Livewire di web.
4. App Android: pindah dari baca/tulis field snapshot ke panggil endpoint baru, keluarkan
   field itu dari payload `PUT /snapshot` supaya tidak dobel sumber kebenaran.
5. Field terkait di snapshot 19B ditandai "sudah dimigrasi, bukan ikut snapshot lagi" di
   dokumen tahap 19B (atau dicatat di tahap migrasinya).

**Urutan disarankan** (belum final, bisa diubah sesuai kebutuhan sebenarnya): follow-up
kerja (27) dan to-do tim (26) dulu (sudah jalan) → **Habit + to-do harian** (paling sering
dipakai, fondasi fitur lain) → Jadwal harian → Kesehatan & Asupan makan → Kalender →
Pengaturan. Asisten AI didiskusikan kapan saja setelah ada waktu, tidak harus urut.

### Tahap 28: Habit + To-do Harian (selesai - web, API, dan sinkron Android — 10 Oktober 2026)

Dibahas 10 Oktober 2026. Fitur paling sering diakses di seluruh app — dicentang berkali-kali
sehari, **harus tetap jalan offline** di Android. Ini beda dari follow-up kerja (tahap 27)
dan to-do tim (tahap 26) yang wajar kalau butuh koneksi internet setiap saat dipakai.

**Kerumitan yang perlu ditandai jujur**: follow-up kerja dan to-do tim bisa langsung
"panggil API, tangani 409 kalau bentrok" karena jarang diedit dan tidak krusial kalau
gagal sesaat. Habit/to-do harian beda — kalau app Android butuh internet tiap kali
centang habit, itu mundur dari prinsip app ("catat dalam 2 detik", offline-first). Jadi
di Android, **database lokal (Room) tetap ada dan tetap sumber data utama untuk
dipakai sehari-hari** — yang berubah adalah cara sinkronnya: bukan lagi "snapshot
sekali gabungan semua data" (19B), tapi **sinkron per-perubahan** (push tiap habit/entry/
todo yang berubah ke server begitu ada internet, tarik perubahan dari server/web secara
berkala) - mirip pola offline-first app modern umumnya, bukan lagi model backup/restore.
Ini pekerjaan Android yang jauh lebih besar dari sekadar "ganti field baca snapshot jadi
panggil API" seperti follow-up kerja.

| Pertanyaan | Keputusan |
|---|---|
| Lingkup | `habits`, `habit_entries` (presensi centang per tanggal), `todos` (harian, maks 5, carry-over to-do belum selesai) |
| Kepemilikan data | Per user (`user_id`), bukan per tim — beda dari `team_todos` |
| Konflik | Sama seperti follow-up kerja: `updated_at` + `409` kalau bentrok. Tapi di Android, konflik jadi jarang terjadi kalau sinkron per-perubahan berjalan cepat (detik/menit), bukan nunggu sinkron manual |
| Web | CRUD habit (nama, wajib/tidak, urutan) + halaman "Hari ini" (centang habit, kelola sampai 5 to-do) — versi sederhana dulu, heatmap kontribusi bisa menyusul |
| Android | **Tidak langsung ganti ke panggil API tiap aksi.** Room tetap utama, ditambah lapisan sinkron baru (queue perubahan lokal → push ke server saat ada internet, tarik perubahan dari server berkala) |

**Rencana teknis:**
- Tabel `habits` (user_id, name, sort_order, is_mandatory, auto_source nullable,
  updated_at), `habit_entries` (habit_id, date, unique[habit_id, date]), `todos` (user_id,
  title, date, done, updated_at).
- Endpoint API: `/api/v1/habits` (CRUD), `/api/v1/habits/{habit}/entries` (tandai/batal
  centang per tanggal), `/api/v1/todos` (CRUD, filter per tanggal). Semua pakai deteksi
  konflik seperti follow-up kerja untuk field yang bisa diedit (nama habit, judul todo);
  toggle centang/selesai cukup idempotent (tidak butuh deteksi konflik serumit itu, aksi
  "centang" dari 2 tempat hasilnya sama).
- Web: Livewire component kelola habit + halaman harian, pola sama seperti `Teams`.
- **Android (bagian paling besar, sesi kerja terpisah)**: rancang ulang lapisan sync -
  outbox pattern (perubahan lokal dicatat sebagai pending, dikirim saat online),
  WorkManager job baru untuk push+pull berkala, resolusi konflik di app kalau 409. Room
  tetap dipakai untuk baca/tulis sehari-hari supaya tetap instan dan offline.

**Langkah bangun:**

1. ✅ **Skema + migrasi**: tabel `habits` (`user_id`, `name`, `sort_order`,
   `is_mandatory`, `auto_source`), `habit_entries` (unique `habit_id`+`date`), `todos`
   (`user_id`, `title`, `date`, `done`) di MySQL.
2. ✅ **Endpoint API**: `GET/POST/PUT/DELETE /api/v1/habits`, `POST
   /api/v1/habits/{habit}/entries` (toggle centang, idempotent), `GET/POST/PUT/DELETE
   /api/v1/todos` (maks 5/tanggal, carry-over saat `GET ?date=hari-ini`). Deteksi konflik
   (`updated_at` + 409) hanya untuk field `name`/`title`; toggle wajib/selesai/centang
   idempotent tanpa cek konflik.
3. ✅ **Halaman web** (`/habit`, `App\Livewire\HabitToday`): checklist habit hari ini +
   to-do (maks 5) di atas, Kelola Habit (tambah, ubah nama, toggle wajib dengan konfirmasi
   saat dimatikan, hapus - disembunyikan untuk habit wajib) di bawah. Versi sederhana,
   tanpa heatmap/level seperti direncanakan.
4. **Keluarkan dari snapshot 19B**: **tidak perlu kode tambahan** - `SnapshotController`
   sudah agnostik terhadap isi `data` (cuma menyimpan blob terenkripsi apa adanya), jadi
   begitu Android berhenti mengisi field `habits`/`habitEntries`/`todos` ke payload
   snapshot (langkah 5), field itu otomatis tidak ikut lagi. Tidak ada migrasi server yang
   dibutuhkan untuk langkah ini.
5. **Android - lapisan sync baru** (sesi kerja terpisah, lebih besar dari langkah 1-3
   digabung): outbox lokal, WorkManager push/pull berkala, UI penanganan konflik.
   **Prasyarat selesai (10 Oktober 2026), outbox belum dikerjakan:**
   - ✅ **Lapisan akun Google di Android** - belum ada sama sekali sebelumnya, padahal
     dibutuhkan bersama oleh tahap 26 langkah 2 (terima undangan tim) dan tahap ini.
     `AccountSettings` (token API di `account.xml`, dikeluarkan dari Auto Backup seperti
     `sync.xml`), `GoogleSignIn` (Credential Manager, minta ID token), `AccountClient`
     (tukar ID token ke token API lewat `POST /api/v1/auth/google`, alamat tetap
     `habitflow.roziqrizal.com`), `AccountManager` (gabungkan ketiganya). Bagian "Akun" di
     Tentang: tombol Masuk dengan Google / Keluar. `assembleDebug` dan unit test lulus.
   - **Pull: hanya saat app dibuka** (diputuskan 10 Oktober 2026) - bukan polling
     WorkManager berkala seperti to-do tim (tahap 26). Habit/to-do personal kurang butuh
     realtime dibanding tugas tim, jadi cukup tarik ulang saat layar Habit dibuka/di-resume;
     push tetap lewat outbox + WorkManager saat online (menyusul).
   - ✅ **Diverifikasi end-to-end (10 Oktober 2026, HP fisik Xiaomi/MIUI, akun Google
     pemilik)**: `local.properties` diisi `GOOGLE_SERVER_CLIENT_ID` dengan Client ID tipe
     **Web** (bukan tipe Android - lihat temuan bug di bawah). Emulator `Pixel6_API34`
     (image `google_apis` non-Play Store, GMS lawas 23.18.18) tidak bisa dipakai untuk
     Credential Manager ("No credentials available") - verifikasi dilakukan di HP fisik
     yang Play Services-nya lengkap. Hasil: Credential Manager mengembalikan ID token,
     server menerima dan membuat sesi, layar Tentang bagian Akun menampilkan nama dan email
     akun dengan "Masuk sebagai ..." dan tombol Keluar.
   - **Bug ditemukan dan diperbaiki saat verifikasi**: `server/.env.production` (sudah live
     di VM) menyimpan `GOOGLE_ANDROID_CLIENT_ID` dengan Client ID **tipe Android**, padahal
     `aud` di ID token dari Credential Manager selalu berisi Client ID **tipe Web**
     (`serverClientId` yang dikirim Android). Akibatnya verifikasi `aud` di
     `GoogleAuthController` pasti gagal. Diperbaiki di `.env.production` lokal **dan** di VM
     (`ssh` + `docker compose up -d --force-recreate`, dicek ulang lewat `php artisan
     tinker`), plus komentar diperjelas di `.env.example` dan `config/services.php` (commit
     `dc74f89`) supaya tidak terulang. Client ID tipe Android tidak pernah dipakai di kode
     mana pun - cukup terdaftar di Google Cloud Console (dicocokkan otomatis lewat package
     name + SHA-1 oleh Google Play Services).
   - ✅ **Outbox lokal, WorkManager push/pull, dan UI status - selesai dan diuji end-to-end
     di HP fisik (10 Oktober 2026).** Room tetap sumber data utama (baca/tulis sehari-hari
     instan, offline-first); sinkron berjalan di latar belakang.

**Skema (migrasi Room 8 → 9):**
- `habits` dan `todos` dapat kolom `remoteId` (null = belum pernah terkirim),
  `remoteUpdatedAt` (nilai `updated_at` server, dikirim balik untuk deteksi konflik), dan
  `dirty` (ada perubahan lokal belum terkirim; baris lama otomatis `dirty = 1` supaya ikut
  terdorong begitu login).
- Tabel baru `habit_entry_outbox` (`habitId`, `date`, `createdAt`): satu baris per
  `toggleHabit`, diproses FIFO lewat `POST habits/{id}/entries` yang juga *toggle* di
  server - replay urutan toggle apa adanya sudah cukup, tidak perlu tahu state absolut.
- Tabel baru `pending_deletes` (`entity`, `remoteId`, `createdAt`): diisi tepat sebelum
  baris lokal dihapus, **hanya kalau `remoteId` sudah tidak null**.

**Kode:** `data/sync/HabitSyncClient.kt` (HttpURLConnection, pola sama dengan
`AccountClient`/`SyncClient`, token akun tahap 25 - bukan token 19B), `HabitSyncManager.kt`
(push lalu pull, mutex, status `StateFlow<HabitSyncStatus>`), `HabitSyncScheduler.kt`
(`InvalidationTracker` pada tabel terkait → debounce 1 menit → `HabitPushWorker`, plus
`HabitPeriodicSyncWorker` tiap 30 menit sebagai jaring pengaman - keduanya hanya aktif
kalau sudah login, pola sama dengan `SyncScheduler` 19B). `domain/sync/HabitSyncMerge.kt`
berisi `entryMergeAction` (Kotlin murni, testable tanpa Room) untuk logika "entry hari ini
perlu disamakan dengan `done` dari server atau dilewati karena ada toggle lokal tertunda".

**Keputusan konflik (409):** server menang - versi server menimpa lokal secara otomatis,
tanpa dialog interaktif (beda dari rencana follow-up kerja tahap 27). Dipilih karena push
per-perubahan cepat membuat konflik jarang terjadi, dan jauh lebih sederhana untuk sesi
ini.

**Batasan yang disadari dan diterima:** riwayat `habit_entries` dari sebelum fitur ini ada
tidak di-backfill ke server (API tidak punya endpoint impor massal) - hanya toggle baru ke
depan yang ikut sinkron.

**Bug ditemukan dan diperbaiki saat verifikasi di HP fisik:** `MainActivity` dan
`ManageHabitsViewModel` awalnya memanggil `pull()` langsung saat layar dibuka (sesuai
rencana awal). Skenario nyata: HP sempat ter-restart saat masih ada penghapusan lokal yang
belum sempat ter-push (dipicu penundaan WorkManager yang sangat panjang di MIUI/Xiaomi,
lihat catatan Doze di bawah) - `pull()` yang berjalan sendirian menemukan item itu masih
ada di server dan **menghidupkannya kembali secara lokal** sebelum penghapusannya sempat
terkirim. Diperbaiki: kedua titik itu sekarang memanggil `syncNow()` (push dulu, baru
pull), supaya perubahan lokal tertunda (termasuk penghapusan) selalu dapat kesempatan
terkirim lebih dulu.

**Catatan Doze/MIUI (ditemukan 10 Oktober 2026):** `dumpsys jobscheduler` di HP Xiaomi/MIUI
yang dipakai uji menunjukkan `HabitPushWorker` (diminta debounce 1 menit) tertunda sampai
`Minimum latency: +59m59s` - jauh lebih agresif dari pembatasan Doze standar Android,
khas pembatasan baterai MIUI. Tombol manual "Sinkron sekarang" (jalan langsung di
foreground, tidak lewat JobScheduler) tetap bekerja instan dan dipakai sebagai jalan pintas
saat verifikasi. Perilaku serupa kemungkinan juga memengaruhi `HabitPeriodicSyncWorker` dan
worker-worker lain (`StepsWorker`, `SyncWorker` 19B) di HP MIUI - belum ada mitigasi
khusus, dicatat sebagai keterbatasan platform, bukan bug kode.

**Hasil verifikasi end-to-end (10 Oktober 2026, HP fisik Xiaomi/MIUI, akun login):**
- Push data lama: 9 habit yang sudah ada sebelum fitur ini (ditandai `dirty` otomatis oleh
  migrasi) berhasil terkirim ke server, termasuk nama yang sudah diubah pengguna
  sebelumnya ("Jamaah 5 waktu").
- Push habit baru: tambah habit dari Android → muncul di server dengan `remoteId` benar.
- Push entry: toggle habit di Android → `habit_entries` bertambah di server.
- Pull: ubah nama habit langsung di server (mensimulasikan edit web) → buka layar Kelola
  Habit di Android → nama baru muncul tanpa edit manual.
- Push to-do baru: tambah to-do dari Android → muncul di server.
- Push delete: hapus habit dan to-do di Android → **setelah fix push-before-pull di atas**,
  keduanya hilang bersih di server juga, tidak ada orphan tersisa.
- 284 unit test lulus (termasuk `HabitSyncClientTest` dengan server soket mini, pola sama
  dengan `SyncClientTest`, dan `HabitSyncMergeTest` untuk `entryMergeAction`).

**Belum dikerjakan**: resolusi konflik interaktif (kalau nanti keputusan "server menang"
dirasa kurang; follow-up kerja tahap 27 kemungkinan butuh ini lebih dulu). Terima undangan
tim dari Android yang disebut di sini sebelumnya sudah dikerjakan - lihat tahap 26 langkah 2.

✅ **Sudah dijalankan ke VM (10 Oktober 2026)** — deploy penuh lewat `server/DEPLOY.md`
(tar+scp, `docker compose build`, `--force-recreate`), lalu `php artisan migrate --force`:
migrasi `habits`/`habit_entries` dan `todos` jalan tanpa error. Container sehat
(`docker ps` healthy), `https://habitflow.roziqrizal.com/login` membalas 200.

**Diputuskan (10 Oktober 2026):** pull Android terjadi saat app dibuka dan saat layar Kelola
Habit dibuka (bukan polling terus-menerus), plus debounce push 1 menit setelah ada
perubahan dan push+pull jaring pengaman tiap 30 menit lewat WorkManager - lihat langkah 5.

**Belum diputuskan:**
- Apakah heatmap kontribusi dibangun juga di web di tahap ini, atau menyusul belakangan.

**Selesai jika:**
- ✅ Habit dan to-do harian bisa di-CRUD penuh dari web.
- ✅ Centang/tambah/hapus dari web kelihatan di Android setelah sinkron berikutnya, dan
  sebaliknya.
- ✅ App Android **tetap bisa dipakai offline** sepenuhnya (centang habit, tambah to-do)
  tanpa internet, dan perubahan itu terkirim otomatis begitu online lagi (sesuai jadwal
  WorkManager - di HP MIUI bisa tertunda signifikan, lihat catatan Doze di langkah 5).
- ✅ Tidak ada data yang hilang saat transisi dari model snapshot ke model baru ini.

### Tahap 29: Migrasi Jadwal Harian ke Web (langkah 1-3 selesai di server dan web 11 Oktober 2026; langkah 4 Android belum)

Lanjutan arah "Web jadi versi lengkap" (tahap 25-28). Baris "Jadwal harian & libur" di
tabel migrasi masih "Belum dimulai" — tahap ini menuliskan rencananya. **Koreksi penting**:
jadwal harian dan waktu sholat **bukan fitur baru** — sudah selesai dan diverifikasi di
Android sejak tahap 17-18 (7 Oktober 2026): tabel `schedule_blocks` (16 blok awal, termasuk
yang berpatokan Dzuhur/Ashar/Maghrib/Isya), mesin hisab Ephemeris Kotlin murni di
`domain/prayer/`, kartu Sekarang/Berikutnya, layar Atur Jadwal, alarm Subuh dan notifikasi
blok. Yang belum ada cuma versi web/server-nya.

**Keputusan (11 Oktober 2026):**

| Pertanyaan | Keputusan |
|---|---|
| Lingkup kerja | Web dan Android sekaligus (pola migrasi penuh 4 langkah seperti tahap 26-28), bukan web dulu lalu Android menyusul |
| Blok di web | Baca saja (read-only) — tidak ada tombol "selesai", tidak menyentuh level atau streak hari. Sama seperti aturan yang sudah berlaku di Android (blok cuma mencentang habit tertaut lewat notifikasi) |
| Data awal | Pakai 16 blok yang sudah ada dan sudah final di Android (tabel di tahap 17), bukan draft `visi-super-app.md` yang lama dan belum menghitung waktu sholat |
| Waktu sholat di web | **Diputuskan opsi (a)** (11 Oktober 2026). Dua opsi yang dipertimbangkan: (a) Android kirim jadwal hari itu yang sudah dihitung (resolved) sebagai cache harian saat sinkron, tanpa PHP perlu tahu hisab sama sekali; (b) port mesin Ephemeris `EphemerisPrayerCalculator.kt` ke PHP supaya web bisa hitung sendiri. Opsi (a) jauh lebih murah untuk mulai, (b) dibutuhkan kalau nanti ada fitur web yang butuh hisab independen dari Android (misalnya kalau Android lama tidak sinkron) |

**Rencana teknis (ikut pola migrasi 4 langkah, lihat tahap 25-28):**
1. Tabel MySQL `schedule_blocks` (+ `schedule_block_habits` untuk tautan habit) meniru
   skema Room Android, model Eloquent.
2. Endpoint API `/api/v1/schedule-blocks` (CRUD + deteksi konflik `updated_at`/409 seperti
   follow-up kerja), plus endpoint kecil untuk cache "jadwal hari ini yang sudah dihitung"
   kalau opsi (a) dipilih.
3. Komponen Livewire: kartu Sekarang/Berikutnya di `/dashboard`, dihitung dari blok + cache
   harian (atau dari hisab PHP kalau opsi b).
4. Android: pindah dari Room lokal murni ke lapisan sync yang sama polanya dengan habit/todo
   (tahap 28) — Room tetap sumber utama dan offline-first, tambah outbox + push cache
   jadwal harian.

**Belum diputuskan:**
- ~~Mekanisme waktu sholat di web~~ diputuskan 11 Oktober 2026: opsi (a), cache dari Android.
- Urutan detail langkah 1-4 vs tahap migrasi lain yang masih "Belum dimulai" (Kesehatan,
  Kalender, dst.) — tahap 29 ini didiskusikan duluan karena nyambung ke permintaan tampilan
  dashboard web.

**Status (11 Oktober 2026):** langkah 1 dan 2 selesai di server, tidak bergantung pada keputusan waktu sholat.
- ✅ Langkah 1: migrasi `schedule_blocks` dan `schedule_block_habits` (tautan ke `habits` server, hilang otomatis
  kalau blok atau habitnya dihapus), model `ScheduleBlock`. Kolom mengikuti `ScheduleBlockEntity` Android.
  `active_days` bitmask Senin = 1 sampai Minggu = 64.
- ✅ Langkah 2: `GET/POST /api/v1/schedule-blocks`, `PUT/DELETE /api/v1/schedule-blocks/{id}` (token `user-token`).
  PUT wajib `updated_at` dan dibalas 409 berisi versi server kalau beda. Tautan habit dikirim sebagai `habit_ids`
  (id habit **di server**) dan menggantikan seluruh tautan blok; habit milik user lain dibalas 422. Blok berpatokan
  sholat wajib `prayer`, jam tetap harus 0-1439 menit dan tanpa `prayer`. Tes: `ScheduleBlockApiTest` (15 tes; seluruh
  suite server 58 tes lulus).
- ✅ **Langkah 3 (server dan web selesai, 11 Oktober 2026), memakai opsi (a)**: keputusan pemilik, Android mengirim waktu
  sholat yang sudah dihitung, server tidak menghitung hisab.
  - Tabel `prayer_days` (satu baris per user per tanggal: `timezone`, `subuh`, `dzuhur`, `ashar`, `maghrib`, `isya`;
    waktu `null` berarti tidak ada). `PUT /api/v1/prayer-days` menerima `timezone` dan `days` (1-14 hari sekaligus,
    kelima waktu wajib ada kuncinya, boleh `null`). Idempotent: kirim ulang menimpa tanggal yang sama tanpa 409.
    `GET /api/v1/prayer-days?from=Y-m-d`. Tes: `PrayerDayApiTest`.
  - `App\Support\Schedule\ScheduleResolver` adalah **salinan PHP** dari `resolveBlocks` dan `nowAndNext` di
    `domain/schedule/Schedule.kt`. Aturannya sengaja sama (hari aktif, patokan sholat plus selisih, pemotongan ke 0-1440,
    urutan mulai/sort_order/id, titik waktu tidak pernah Sekarang). **Kalau salah satu berubah, ubah keduanya.**
    Tes: `ScheduleResolverTest`.
  - Kartu **Jadwal sekarang** (Livewire `ScheduleCard`) di `/`, hanya baca, diperbarui tiap 60 detik. Jam "sekarang"
    dihitung di zona waktu terakhir yang dikirim HP (cadangan `Asia/Jakarta`), bukan UTC server. Kalau waktu sholat hari
    ini belum dikirim, blok yang berpatokan sholat tidak tampil dan kartu menjelaskannya. Tes: `ScheduleCardTest`
    (termasuk uji bahwa memakai UTC membuat tes gagal). Seluruh suite server 87 tes lulus.
  - **Batasan:** "Hari ini libur" belum ada di server (tabel `days_off` belum dibuat), jadi web menganggap tidak libur.
    Kartu belum dilihat langsung di browser, hanya diuji lewat tes Livewire.
- **Belum:** langkah 4 (Android sinkron: kirim blok, tautan habit, dan `prayer_days` 7 hari ke depan). Id habit Android
  (Room) berbeda dari id server, jadi butuh pemetaan id habit lokal ke server sebelum `habit_ids` bisa dikirim. Seed 16
  blok awal untuk user baru di server juga belum ada; sampai langkah 4 jalan, kartu di web kosong ("Belum ada jadwal di
  server").

### Tahap 30: Fokus Kesehatan Harian (langkah 1-2 selesai di Android 11 Oktober 2026; langkah 3-6 belum)

Aturan fitur di `docs/rancangan.md` bagian Fokus kesehatan harian. Mockup web dan mobile:
https://claude.ai/artifact/TWJ8Uus2hKtQQa81wKujG5

Tujuan: dashboard menjawab "apa yang sebaiknya saya lakukan hari ini untuk target kesehatan saya", bukan hanya
menampilkan angka. Android menampilkannya di dashboard Hari ini, web di `/dashboard`.

**Keputusan (11 Oktober 2026):**

| Pertanyaan | Keputusan |
|---|---|
| Bentuk | **Satu fokus per hari**, bukan daftar |
| Cara memilih | **Berbasis aturan**, bukan AI. Deterministik, offline, bisa diuji. Tidak memakai kuota AI tahap 24 |
| Keselamatan | Ada pagar tertulis (rancangan.md): tidak menyarankan turun berat saat kurus atau sudah di target, tanpa kalori atau diet, tensi ≥ 180/110 selalu menang |
| Android dan web | Android: aksi cepat untuk hari ini. Web: gambaran lebih luas dan alasan di balik rekomendasi |
| Sumber logika | **Satu**. Input berat dan tensi hanya di Android. Aturan hanya ditulis di Kotlin (`domain/health/`). Web tidak punya salinan aturan |
| Arah data | **Satu arah, Android → server.** Web hanya membaca. Tanpa deteksi konflik 409, karena tidak ada tulis dari web |

**Usulan teknis (belum diputuskan, ditandai agar bisa dikoreksi sebelum dikoding):**

- Domain murni `domain/health/Focus.kt`: `FocusInput` (berat terakhir dan tren, tinggi, target, tensi dua terakhir,
  langkah hari ini, kopi, manis, makan hari ini, jam sekarang) menjadi `Focus` (kode aturan, judul, kalimat, alasan).
  Konstanta di satu tempat: ambang langkah 50%, 120 langkah per menit, batas 60 menit, berat lama 10 hari, turun cepat
  2,0 kg per 28 hari, jam 10.00 dan 13.00.
- Android menghitung fokus di `HealthViewModel` dari data yang sudah ada, tanpa tabel baru di Room.
- Server menerima kiriman satu arah lewat endpoint baru `PUT /api/v1/health` (idempotent, upsert), berisi: catatan berat
  dan tensi (kunci unik `user_id` + `time_millis`), total langkah 7 hari terakhir (Android membaca per hari dari Health
  Connect), dan hasil fokus terakhir beserta angka dasarnya. Tabel: `weight_entries`, `blood_pressure_entries`,
  `step_days`, `health_focus` (satu baris per user, termasuk `computed_at`).
- Dikirim lewat `SyncScheduler` yang sama, dan setelah catat berat atau tensi. Snapshot 19B tetap menjadi cadangan.
- Web: komponen Livewire hanya-baca di `/dashboard`, mengikuti mockup. Data lebih dari 24 jam diberi keterangan "terakhir
  sinkron ...". Karena MIUI menunda WorkManager sekitar satu jam di HP uji, web boleh tertinggal; tombol sinkron manual
  yang ada dipakai saat perlu.

**Langkah bangun:**

1. ✅ **Domain + unit test.** `Focus.kt` dan tes untuk setiap aturan, urutan prioritas, setiap angka batas, dan setiap
   pagar (BMI kurus, di bawah target, tensi tinggi, data kurang).
2. ✅ **Kartu Fokus di Android.** Kartu hero di atas dashboard Hari ini, "Kenapa ini?", dan catatan nilai baru di
   `docs/design/README.md` lebih dulu.
3. **Server.** Migrasi tabel, endpoint `PUT /api/v1/health`, tes Laravel.
4. **Android mengirim.** Kirim catatan, langkah 7 hari, dan fokus; aman diulang. *Digantikan tahap 31: sekarang pasti ada
   akun (login wajib), jadi langkah ini tidak lagi perlu menangani cabang "belum login".*
5. **Web.** Kartu fokus dan bagian Kesehatan di `/dashboard`.
6. **Verifikasi di HP fisik** dan catat hasilnya di sini.

**Selesai jika:**
- Untuk setiap kondisi di tabel aturan, unit test membuktikan fokus yang benar, dan aturan 1 selalu menang.
- BMI kurus atau berat di bawah target tidak pernah menghasilkan saran menurunkan berat.
- Kartu fokus tampil di atas Hari ini tanpa internet, dan "Kenapa ini?" menampilkan angka yang sama dengan kartu lain.
- Setelah mencatat berat di HP lalu sinkron, web menampilkan angka dan fokus yang sama persis dengan HP.
- Web tidak punya tombol atau form pencatatan berat dan tensi.
- Mengirim berulang tidak menggandakan catatan.

**Belum diputuskan:**
- Nilai konstanta di atas (120 langkah per menit, jam 10.00 dan 13.00, ambang 10 hari dan 2,0 kg) masih perkiraan awal.
- Apakah 7 aturan ini terlalu banyak untuk versi pertama. Aturan 4 dan 7 bisa ditunda tanpa merusak yang lain.
- Label tab bawah di mockup mobile (Hari Ini, Agenda, Kerja, Kemajuan, Tentang) hanya tebakan dari nama layar di kode.
- Urutan terhadap tahap 29 (jadwal ke web). Keduanya tidak saling bergantung.

**Status (11 Oktober 2026):** langkah 1 dan 2 selesai. `domain/health/Focus.kt` dengan `FocusTest` (lulus), kartu `FocusCard` di Hari ini, dan seluruh layar Hari ini disesuaikan dengan mockup mobile (kepala sapaan, Sekarang/Berikutnya dua kolom, Berat/Tensi/Langkah bergaya baru, kemajuan Habit/To-do, ikon tab bawah; nilai desain di `docs/design/README.md`). Terpasang dan terlihat di HP fisik; **belum diuji tiap aturan lewat data nyata di HP** (hanya kondisi "di jalur" yang terlihat). Teks fokus (judul, kalimat, alasan) ada di `ui/FocusCard.kt`, belum dikirim ke server. Langkah 3-6 (server, kirim dari Android, web, verifikasi) belum dikerjakan.

### Tahap 31: Login Wajib (diputuskan 11 Oktober 2026, belum dikoding)

Keputusan pemilik (11 Oktober 2026): **untuk memakai app, pengguna harus login.** Ini membalik asumsi dasar yang dipakai sejak
tahap 1 ("offline dan lokal, tanpa akun") dan yang masih tertulis di beberapa dokumen dan kode. Perubahannya menyentuh banyak
bagian, jadi dikerjakan sebagai satu tahap sendiri (dengan sub-tahap 31A sampai 31M) supaya tidak ada yang tertinggal setengah.
App akan diunggah ke Play Store, jadi tahap ini juga memuat semua kewajiban rilis publik.

Dokumen pendamping (wajib dibaca bersama tahap ini): [rilis-play-store.md](rilis-play-store.md) berisi inventaris data, penjaga
otomatis, gerbang rilis A sampai H, daftar izin, dan perbandingan layanan email.

Mockup (artboard Masuk, Daftar, Lupa kata sandi, Profil, Profil belum masuk, Pengaturan, dan Hari ini dengan lingkaran profil):
https://claude.ai/artifact/TWJ8Uus2hKtQQa81wKujG5. Dua artboard di sana sudah usang dan harus diubah di 31A: **Profil belum masuk**
(tidak ada lagi keadaan itu) dan tombol **"Lewati dulu"** di Masuk dan Daftar.

#### Riwayat diskusi (11 Oktober 2026, urutan terjadinya)

Dicatat lengkap supaya sesi mana pun tahu **apa yang diputuskan, apa yang ditolak, dan kenapa**.

1. **Usulan pemilik.** Tab Tentang diganti Pengaturan. Tombol kanan atas yang berisi foto profil menjadi halaman Profil. Semua hal tentang
   profil yang ada di Tentang (termasuk keluar dari app) pindah ke sana. Nanti ada halaman login dan register.
2. **Tanggapan Claude atas usulan itu.** Setuju untuk Pengaturan dan Profil. Tinggi badan dan target berat diusulkan pindah ke Profil.
   Untuk login dan register, ditunjukkan bahwa cara masuk di app saat itu hanya Google (akun dibuat otomatis di server saat masuk pertama,
   jadi "register" baru bermakna kalau ada akun email dan kata sandi), dan bahwa login sebaiknya tidak jadi gerbang wajib karena app ini
   offline-first.
3. **Jawaban pemilik.** Cara masuk dijadikan seperti web (web sudah punya email dan kata sandi plus Google). Pekerjaan besar email dan kata sandi
   "tidak masalah, kita kerjakan". App akan diunggah ke Play Store. Login tidak wajib: **disetujui** pada saat itu.
4. **Mockup pertama** dibuat di artifact: Masuk, Daftar, Lupa kata sandi, Profil, Profil belum masuk, Pengaturan, dan lingkaran profil di Hari ini
   membuka Profil. Tab bawah di mockup disamakan dengan app (enam tab, ikon di atas label, Pengaturan menggantikan Tentang).
5. **Pertanyaan pemilik: apakah halaman Profil belum masuk perlu?** Claude mengusulkan menghapusnya (lingkaran profil langsung ke Masuk,
   data tubuh pindah ke Pengaturan). Pemilik lalu **lupa kesepakatan login opsional** dan minta pembahasannya diulang, karena aneh kalau
   tanpa login bisa melihat halaman berisi informasi pribadi.
6. **Pembahasan ulang.** Dijelaskan bahwa login tidak melindungi data lokal (data tetap ada di HP dan tidak terenkripsi, siapa pun yang memegang
   HP yang tidak terkunci bisa melihatnya); login hanya menentukan identitas untuk server. Dua pilihan: login wajib atau opsional. Claude
   merekomendasikan opsional **ditambah kunci app (sidik jari atau PIN)** sebagai perlindungan data yang sebenarnya.
7. **Keputusan pemilik: login wajib.** "Kalau dia mau pakai aplikasi ini maka dia harus login. Perubahan itu harus diikuti semua konsep di app ini."
   Pemilik meminta penelusuran bagian yang terdampak dan dijadikan satu tahap sendiri. Dihasilkanlah tahap ini dan inventaris di bawah.
   *Catatan: usulan kunci app (langkah 6) tidak dijawab dan tidak ditolak. Dicatat sebagai pertanyaan terbuka di bawah.*
8. **Verifikasi email dan layanan email.** Claude menunjukkan celah pengambilalihan akun (web menautkan Google ke akun yang emailnya sama, jadi
   penyerang yang mendaftar duluan dengan email korban tetap memegang kata sandinya). Pemilik **setuju verifikasi email wajib**. Soal layanan
   pengirim email, pemilik bertanya apakah bisa membangun mail hosting sendiri. Claude **menyarankan tidak**: port 25 keluar diblokir ISP
   rumahan, IP rumahan tanpa reverse DNS dan tanpa reputasi, Cloudflare Tunnel hanya membawa lalu lintas web, dan kalau email gagal tidak ada
   pengguna baru yang bisa mendaftar. Setelah dibandingkan batas gratisnya, pemilik **menyetujui Resend gratis dengan Brevo gratis sebagai
   cadangan**. Amazon SES ditolak karena gratisnya hanya 12 bulan.
9. **Sesi tidak kedaluwarsa** (keputusan pemilik). Konsekuensinya ditambah "Keluar dari semua perangkat" di Profil, dan ganti kata sandi
   mencabut sesi lain.
10. **Pertanyaan pemilik: apakah menyimpan semua aktivitas pengguna di database jadi kendala di Google Play?** Jawaban: bukan kendala, tapi ada
    kewajiban transparansi dan kontrol pengguna (kebijakan privasi, Data safety, hapus akun, deklarasi Health Connect). Ditambah tanggung jawab
    hukum (UU PDP) dan operasional (server satu VM). Pemilik menyetujui semua usulan dan meminta **konsep tanpa celah supaya tidak ada kesalahan
    yang tidak disengaja**. Hasilnya [rilis-play-store.md](rilis-play-store.md), dengan catatan jujur bahwa nol celah tidak bisa dijamin, yang bisa
    dijamin adalah pemilik, bukti, dan tes yang gagal kalau dilanggar.
11. **Temuan penelusuran syarat rilis** yang sebelumnya tidak ada di rencana: layar persetujuan OAuth Google masih mode uji (orang lain tidak bisa
    login Google), client ID Android harus memakai SHA-1 kunci Play App Signing, peninjau Google butuh akun uji permanen yang sudah terverifikasi,
    akun pengembang pribadi baru wajib uji tertutup 12 penguji selama 14 hari berturut-turut, dua izin dibatasi Google (`USE_EXACT_ALARM`,
    `USE_FULL_SCREEN_INTENT`), `targetSdk` 34 hampir pasti di bawah syarat, Auto Backup membawa database, belum ada keystore rilis, dan file
    snapshot terenkripsi di storage server ikut harus terhapus saat hapus akun.
12. **Kepemilikan tim saat pemilik menghapus akun:** pemilik **setuju** (kepemilikan pindah ke anggota terlama, tim dihapus kalau tidak ada anggota lain).

#### Keputusan akhir

**Diputuskan pemilik:**

| Pertanyaan | Keputusan |
|---|---|
| Aturan | Tanpa login, app tidak bisa dipakai. Layar Masuk menjadi gerbang di depan semua layar |
| Cara masuk | **Email dan kata sandi, plus Google**, sama seperti web (tahap 25). API Android tidak lagi Google-only |
| Verifikasi email | **Wajib** untuk akun email dan kata sandi: kode 6 angka di app, berlaku 10 menit, sekali pakai, disimpan sebagai hash. Akun Google dianggap terverifikasi |
| Penautan Google | Hanya ke akun yang emailnya sudah terverifikasi. Akun email yang belum terverifikasi diambil alih oleh login Google (kata sandi dikosongkan, semua token dicabut) |
| Lupa kata sandi | Kode 6 angka lewat email. Jawaban server sama apakah email terdaftar atau tidak |
| Layanan email | **Resend gratis** (3.000 per bulan, maksimal 100 per hari), **Brevo gratis** (300 per hari) sebagai cadangan. Tanpa server email sendiri |
| Subdomain pengirim email | **`mail.roziqrizal.com`**, terpisah dari email pribadi di domain utama |
| Aturan kata sandi | Minimal 8 karakter, dan ditolak kalau termasuk kata sandi paling umum. Tanpa autentikasi dua langkah di tahap ini |
| R8 (pengecil kode rilis) | **Mati** untuk rilis pertama. Menyalakannya jadi tahap tersendiri sesudah app tayang dan stabil. Risiko yang diterima: unduhan beberapa MB lebih besar dan kode lebih mudah dibaca, jadi tidak boleh ada rahasia di dalam app |
| Sesi | **Tidak kedaluwarsa.** Hanya dicabut oleh Keluar, ganti kata sandi, "Keluar dari semua perangkat", hapus akun, atau dari web |
| Layar baru | Masuk, Daftar, Verifikasi kode, Lupa kata sandi (tombol "Lewati dulu" dihapus) |
| Tab Tentang | Diganti **Pengaturan** (ikon roda gigi). Versi dan "Dibuat oleh" turun ke bagian Tentang paling bawah |
| Lingkaran profil di Hari ini | Membuka halaman **Profil** (layar penuh di atas tab, bukan tab ketujuh) |
| Halaman Profil belum masuk | **Tidak ada**, karena tidak ada keadaan "belum masuk" lagi |
| Tim saat pemilik menghapus akun | Kepemilikan pindah ke anggota terlama. Tidak ada anggota lain: tim dihapus. To-do buatan pemilik tetap ada dengan nama "Pengguna dihapus". Anggota biasa yang menghapus akun hanya keluar dari tim |

**Disetujui pemilik lewat jawaban "setuju dengan semua usulan" (koreksi kalau ternyata tidak termasuk):**

| Pertanyaan | Keputusan | Alasan |
|---|---|---|
| Offline setelah login | **Tetap bisa.** Room tetap sumber data harian, sesi disimpan di HP. Internet hanya dibutuhkan untuk login pertama | Prinsip "catat dalam 2 detik" dan hasil tahap 28 tidak dibuang. Login wajib mengubah siapa yang boleh memakai app, bukan cara app menyimpan data |
| Keluar | **Menghapus semua data lokal** (database, pengaturan, cache), menghentikan alarm, notifikasi, worker, lalu memulai ulang proses app | HP bisa dipakai akun lain. Data akun sebelumnya tidak boleh tersisa |
| Keluar saat ada perubahan belum terkirim | Cek dulu, tampilkan jumlahnya, minta konfirmasi. Tanpa internet: peringatan keras bahwa perubahan itu akan hilang | Menghapus data lokal berarti perubahan yang belum tersinkron hilang permanen |
| Data lama di HP (sebelum tahap ini) | **Diambil alih akun pertama yang login** di pemasangan itu, sekali saja | Pemasangan yang punya data lama kemungkinan hanya HP pemilik (dan mungkin HP istri), belum ada pengguna lain. Jalur gabung tahap 28 dan 27 dipakai ulang |
| Snapshot 19B | **Pindah ke akun**: `PUT /snapshot` dan `GET /snapshot/latest` memakai `user-token`, snapshot terikat ke user. Token per-HP (`api_tokens`) dipensiunkan, snapshot lama diklaim ke akun pemilik lewat satu perintah | Dua sistem login tidak boleh hidup bersamaan. Data yang belum dimigrasi (jadwal, kesehatan, makan, kalender, pengaturan, EOD) masih hanya ada di snapshot |
| "Atur server" dan token di Tentang | **Dihapus.** Alamat server menjadi konfigurasi build | Pengguna Play Store tidak mengisi alamat server dan token. Itu alat pengembang |
| Auto Backup Android | **Keluarkan `habitflow.db` dan `settings.xml`** dari `backup_rules.xml` dan `data_extraction_rules.xml` | Server sekarang cadangannya. Kalau tidak, database akun lama bisa dipulihkan ke HP lain lalu diambil alih akun lain |
| Hapus akun | **Wajib ada**, di app (Profil) dan di web (halaman publik yang bisa dibuka tanpa app). Permanen dan langsung, dengan konfirmasi ulang kata sandi | Syarat Google Play untuk app yang punya pembuatan akun |
| Keluar dari semua perangkat | Tombol di Profil. Ganti kata sandi mencabut semua sesi lain | Sesi tidak kedaluwarsa, jadi harus ada cara mencabut kalau HP hilang |
| Syarat rilis | Seluruh isi [rilis-play-store.md](rilis-play-store.md): penjaga otomatis, gerbang rilis, daftar izin | "Konsep tanpa celah" yang diminta pemilik |

#### Inventaris bagian yang harus berubah (hasil penelusuran 11 Oktober 2026)

*Dokumen*
- `docs/concept.md`: Prinsip kerja nomor 4 ("Tidak ada akun, server, atau sinkronisasi di versi ini"). Tahap 19B (baris Sifat app dan
  Login: token pribadi tanpa akun). Tahap 25 (baris "API Android tetap Google-only"). Bagian Arah baru ("Token per-HP tanpa akun
  tetap ada"). Tahap 27 (snapshot 19B "Belum login tetap ikut, supaya follow-up tanpa akun tetap tercadangkan"). Tahap 26
  (teks "Masuk dengan Google dulu untuk bisa bergabung"). Tahap 28 dan 29 (peta migrasi dan sifat offline). Tahap 30 (langkah 4
  kirim ke server sekarang pasti terjadi karena pasti ada akun). Tahap 32 (`user-token` tidak terpengaruh, cek ulang).
- `docs/visi-super-app.md`: Prinsip nomor 3 ("Offline dulu... HP adalah sumber data. Data disinkron ke server sendiri untuk backup").
- `docs/rancangan.md`: bagian Sinkron ke server (token pribadi, "Tanpa akun", Pulihkan manual di Tentang), bagian Data ("Tidak ada
  akun. Cadangan opsional"), Di luar versi ini, dan bagian Daily scrum yang dibatalkan ("Tidak ada akun dan tidak ada sinkron").
- `docs/design/README.md`: layar Tentang (enam bagian, termasuk Akun dan Sinkron ke server dengan dialog "Atur server"), daftar
  tab, sapaan "tanpa akun hanya sapaan" di Hari ini, `SignInPrompt` di layar Tugas Rumah.
- `README.md` baris 6: "Data disimpan lokal di perangkat, tanpa akun dan tanpa internet."

*Android*
- `MainActivity` dan `HabitFlowApp`: gerbang sesi. Link undangan tim (`pendingInviteToken`) harus bertahan melewati layar Masuk.
- `HabitFlowApplication`: `syncScheduler`, `habitSyncScheduler`, `teamPollScheduler`, `StepsWorker.ensureScheduled`, dan
  `keepNotificationsInSync` jalan sebelum ada login. Semuanya baru boleh hidup setelah login dan dihentikan saat keluar.
  `AndroidManifest`: receiver `BOOT_COMPLETED` tidak boleh menjadwalkan alarm kalau belum ada sesi.
- `AccountManager`, `AccountClient`, `AccountSettings`: login dan daftar email, verifikasi, lupa dan ubah kata sandi, keluar yang
  mencabut token di server, hapus akun, penanganan 401.
- `AccountSection` dan bagian Akun di `AboutScreen`: pindah ke Profil. `AboutScreen` menjadi Pengaturan dengan bagian Data tubuh
  (tinggi dan target berat dari `HealthSettings`).
- `SyncSection`, `SyncViewModel`, `SyncSettings` (alamat dan token), `SyncScheduler`, `SyncManager` (`followUpsOnAccount`),
  `Snapshot.followUpsOmitted`: sistem 19B pindah ke akun, UI "Atur server" dan token dihapus, cabang "belum login" dihapus.
- `TeamScreen` (`SignInPrompt`, dialog undangan "Masuk dengan Google dulu") dan `TeamViewModel` (`account == null`).
- `WorkRepository`, `HabitSyncManager`, `HabitSyncScheduler`: jalur "belum login" dan "gagal diam-diam karena belum login".
- `res/xml/backup_rules.xml` dan `data_extraction_rules.xml`: keluarkan database dan pengaturan.
- `app/build.gradle.kts`: `targetSdk = 34` (di bawah syarat Play untuk rilis baru). `isMinifyEnabled = false` sudah diputuskan tetap mati untuk rilis pertama. `signingConfigs` rilis sudah ada dan membaca `~/.habitflow/keystore.properties`, tinggal kuncinya dibuat.
- `AndroidManifest`: `USE_EXACT_ALARM` dan `USE_FULL_SCREEN_INTENT` (dibatasi Google), tipe foreground service, `READ_CALENDAR`,
  `ACCESS_COARSE_LOCATION`, izin Health Connect.
- Build debug: alur uji lewat adb (receiver debug di `src/debug`) butuh cara masuk tanpa mengetik.

*Server (`server/`)*
- API baru di samping `POST auth/google`: lihat kontrak di 31A.
- Web belum punya: verifikasi email, lupa dan ubah kata sandi, hapus akun, halaman publik hapus akun, halaman kebijakan privasi.
- `routes/api.php`: `PUT /snapshot` dan `GET /snapshot/latest` pindah dari middleware `token` ke `user-token`. `api_tokens`,
  `AuthenticateToken`, dan perintah artisan pembuat token dipensiunkan setelah migrasi snapshot lama.
- Google-only user (kolom `password` kosong) perlu jalur "buat kata sandi".
- Snapshot disimpan sebagai **berkas** di storage: hapus akun harus menghapus berkasnya, bukan hanya barisnya.
- Tabel `sessions` (kalau driver sesi database) dan `email_codes` (baru) ikut daftar hapus akun.

*Rilis Play Store dan operasional*
- Kebijakan privasi (versi untuk Habitflow, bukan hanya yang ada di roziqrizal.com) menyebut data kesehatan, penyimpanan, backup, dan hapus akun.
- Formulir Data safety, deklarasi Health apps dan Health Connect, akun uji untuk peninjau, uji tertutup 14 hari.
- Backup MySQL ke luar VM, latihan pemulihan, pemantauan, prosedur kebocoran data (UU PDP: pemberitahuan paling lambat 3x24 jam).
- Layar persetujuan OAuth Google ke produksi, SHA-1 Play App Signing, keystore rilis dan salinannya.

#### Peta sub-tahap dan urutan

| Sub-tahap | Isi | Bergantung pada |
|---|---|---|
| **31A** | Dokumen, kontrak API, perbarui mockup | - |
| **31B** | Server: akun, verifikasi email, reset kata sandi (API) | 31A |
| **31C** | Server: halaman web (verifikasi, reset, ubah sandi, hapus akun, kebijakan privasi) | 31B |
| **31D** | Server: hapus akun menyeluruh dan penjaga skema | 31B |
| **31E** | Server: snapshot ke akun, pensiunkan token 19B | 31B |
| **31F** | Android: lapisan akun dan sesi | 31B |
| **31G** | Android: gerbang dan layar auth | 31F |
| **31H** | Android: siklus hidup sesi, Keluar, data lama, ganti akun | 31F, 31E |
| **31I** | Android: Pengaturan, Profil, Data tubuh | 31G, 31D |
| **31J** | Skrip cek rilis dan tes pelengkap | 31I |
| **31K** | Operasional server: backup, pemantauan, prosedur insiden | bisa paralel setelah 31B, **wajib sebelum 31M** |
| **31L** | Rilis teknis: targetSdk, izin, penandatanganan, Google Sign-In produksi | 31I |
| **31M** | Uji tertutup 14 hari, formulir Play Console, produksi | 31C, 31D, 31J, 31K, 31L |

Dua jalur bisa jalan bersamaan setelah 31A: **server** (31B, 31C, 31D, 31E) dan **Android** (31F, lalu 31G dan 31H, lalu 31I). **Jalur terpanjang
adalah uji tertutup 14 hari di 31M** yang tidak bisa dipercepat, jadi semua yang mendahuluinya dikerjakan secepat mungkin dan tidak ada yang
menunggu 31M selesai. Aturan umum tiap sub-tahap: satu commit atau lebih per langkah, tes ditulis bersama kodenya, status dicatat di sini
sebelum lanjut (ganti `[ ]` jadi `[x]`), dan inventaris di [rilis-play-store.md](rilis-play-store.md) bagian 1 diperbarui di commit yang sama kalau tabel,
kolom, atau izin berubah.

#### 31A. Dokumen, kontrak API, dan mockup

Status: dokumen dan kontrak API selesai (11 Oktober 2026). Mockup di artifact **sengaja ditunda** (diputuskan pemilik
11 Oktober 2026) — tidak menahan 31B, dikerjakan kapan saja sebelum 31G (Android: gerbang dan layar auth) butuh
acuan layarnya.

- [x] `docs/rancangan.md`: bagian baru **Akun dan login (tahap 31)** ditulis (login wajib, cara masuk, verifikasi, sesi, Keluar, hapus akun, data lama, tim).
  Bagian Sinkron ke server, Data, Di luar versi ini, dan catatan Daily scrum diperbarui dengan catatan "digantikan tahap 31".
- [x] `docs/concept.md`: Prinsip kerja nomor 4 diganti. Catatan "Digantikan tahap 31" ditambah di tahap 19B (baris Login), 25 (API Android Google-only), 26
  (Token per-HP dua jalur, dialog "Masuk dengan Google dulu"), 27 (snapshot `followUpsOmitted`), bagian "Arah baru" migrasi 28/29 (Token per-HP tetap ada), dan
  30 langkah 4. Tahap 32 tidak perlu catatan tambahan — "Belum diputuskan"-nya sudah menyebut dicek ulang setelah 31B.
- [x] `docs/visi-super-app.md` prinsip 3 dan `README.md` baris 6 diperbarui.
- [x] `docs/design/README.md`: bagian baru Pengaturan (gantikan Tentang), Profil, dan Masuk/Daftar/Lupa kata sandi ditulis **dari mockup yang sudah ada**
  (`Login.dc.html`, `Register.dc.html`, `Forgot.dc.html`, `Profile.dc.html`, `Settings.dc.html`). Sapaan "tanpa akun" dan "Atur server" dihapus dari dokumen.
  **Dua temuan saat menulis ulang dokumen ini, keduanya sudah diputuskan pemilik 11 Oktober 2026:**
  1. Mockup `Profile.dc.html` dialog Keluar berbunyi "Data di HP ini tidak dihapus" — bertentangan dengan keputusan akhir tahap 31. **Diputuskan: ikut
     keputusan tahap 31 (data dihapus)**, teks dialog di `docs/design/README.md` sudah diperbaiki; artboard `Profile.dc.html` masih perlu disunting menyusul.
  2. Mockup `Settings.dc.html` tidak memuat Data tubuh (ada di `Profile.dc.html` saja). **Diputuskan: Data tubuh tetap di Profil**, tidak dipindah ke
     Pengaturan — rencana baris di bawah ("pindahkan Data tubuh ke Pengaturan") sudah usang.
  Empat layar/dialog yang belum ada mockup-nya sama sekali (Verifikasi kode, Reset kata sandi lengkap, Sesi berakhir, dialog Keluar dengan perubahan belum
  terkirim) dicatat nilainya sebagai **perkiraan pola**, bukan hasil gambar — lihat `docs/design/README.md` bagian Masuk/Daftar/Lupa kata sandi.
- [x] **Kontrak API** (tabel di bawah) disetujui pemilik apa adanya, 11 Oktober 2026.
- [ ] Mockup di artifact (**ditunda**, tidak menahan 31B): hapus artboard Profil belum masuk dan tombol "Lewati dulu"; perbaiki teks dialog Keluar di
  `Profile.dc.html` jadi "Semua data di HP ini akan dihapus..." (temuan 1); **Data tubuh dibiarkan di Profil, tidak dipindah** (temuan 2, sudah diputuskan);
  tambah artboard Verifikasi kode, Reset kata sandi (kode dan sandi baru), dialog Keluar dengan perubahan belum terkirim, Hapus akun (konfirmasi kata sandi),
  dan layar "Sesi berakhir".

**Kontrak API akun** (semua di bawah `/api/v1`, JSON, pesan galat dalam bahasa Indonesia, kode galat di field `code`):

| Metode dan jalur | Masuk | Keluar | Catatan |
|---|---|---|---|
| `POST auth/register` | `name`, `email`, `password`, `password_confirmation`, `privacy_policy_version` | 201 `{status:"verification_required", email}` | Tanpa token sebelum email terverifikasi. 422 kalau email sudah terverifikasi di akun lain; kalau email ada tapi belum terverifikasi, kirim ulang kode dan tetap 201. Mencatat waktu dan versi persetujuan |
| `POST auth/verify-email` | `email`, `code`, `device_name` | 200 `{token, account}` | 422 `code_invalid` atau `code_expired`. 5 kali salah menghanguskan kode. 429 kalau terlalu sering |
| `POST auth/resend-verification` | `email` | 202 selalu | Maksimal 1 per 60 detik dan 5 per jam per email |
| `POST auth/login` | `email`, `password`, `device_name` | 200 `{token, account}` | 403 `email_not_verified` (app membuka layar Verifikasi). 422 pesan sama untuk email salah dan sandi salah. 429 setelah 5 gagal per menit per IP dan 10 per 15 menit per email |
| `POST auth/google` | `id_token`, `device_name` | 200 `{token, account}` | Sudah ada. Ditambah aturan penautan akun di 31B |
| `POST auth/forgot-password` | `email` | 202 selalu | Jawaban sama apakah email terdaftar atau tidak. Maksimal 3 per jam per email |
| `POST auth/reset-password` | `email`, `code`, `password`, `password_confirmation` | 204 | Mencabut **semua** token akun. App kembali ke layar Masuk |
| `POST auth/logout` | token di header | 204 | Mencabut token ini |
| `POST auth/logout-all` | token di header | 204 | Mencabut semua token akun, termasuk ini |
| `PUT account/password` | `current_password` (tidak perlu untuk akun Google tanpa sandi), `password`, `password_confirmation` | 204 | Mencabut semua token **selain** yang dipakai |
| `DELETE account` | `password`, atau `id_token` Google untuk akun tanpa sandi | 204 | Konfirmasi ulang wajib. Menghapus semua data (31D). Token ikut tercabut |
| `GET me` | token di header | 200 `{account, has_password, google_linked, email_verified}` | Dipakai Profil dan pemeriksaan sesi |

`account` berisi `id`, `name`, `email`, `avatar`. Aturan kata sandi (diputuskan pemilik 11 Oktober 2026): minimal 8 karakter, dan ditolak kalau ada di daftar kata sandi paling umum. Tanpa autentikasi dua langkah di tahap ini.
Tidak ada endpoint yang membedakan "email tidak terdaftar" dari "terdaftar" selain register yang sudah dijelaskan.

**Selesai jika:** tidak ada dokumen yang masih menyatakan "tanpa akun" (✅, 11 Oktober 2026), kontrak API disetujui
pemilik (✅, 11 Oktober 2026), dan mockup mencerminkan keputusan terbaru (**ditunda**, dikerjakan sebelum 31G butuh
acuan layarnya — lihat status di atas).

#### 31B. Server: akun, verifikasi email, reset kata sandi (API)

Status: **kode dan tes selesai (12 Oktober 2026), belum bisa ditutup**: pengiriman email sungguhan (domain pengirim, DNS, kunci Resend) dan uji `curl` ke server produksi belum dikerjakan. Bergantung pada 31A (✅).

- [x] **Migrasi.** `users.email_verified_at`, `users.privacy_consented_at`, `users.privacy_policy_version`. Tabel `email_codes` (`user_id`, `purpose` verify atau reset,
  `code_hash`, `expires_at`, `attempts`, `consumed_at`). Akun yang **sudah ada** diberi `email_verified_at` oleh migrasi satu kali (diuji: `test_migrasi_menandai_akun_yang_sudah_ada_sebagai_terverifikasi`).
  Tabel `jobs` dan `failed_jobs` ikut ditambah untuk antrean. Kolom verifikasi dan persetujuan sengaja tidak masuk `$fillable`, hanya lewat `forceFill`.
- [ ] **Email.** Kode sudah ada (`CodeMail`, teks bahasa Indonesia, lewat antrean database dengan `tries=3`, `config/habitflow.php`, contoh `.env` dan service `queue` di `docker-compose.yml`).
  **Keputusan teknis yang menyimpang dari rencana awal:** Resend dipanggil lewat **SMTP bawaan Laravel** (`smtp.resend.com`, pengguna `resend`, sandi = API key), bukan paket
  `resend/resend-laravel`, karena composer tidak ada di PC ini dan SMTP tidak butuh paket baru. Brevo cadangan juga SMTP, cukup ganti `MAIL_*` (contoh di `.env.example`).
  **Belum dikerjakan (butuh pemilik):** buat akun Resend dan kunci API, verifikasi domain `mail.roziqrizal.com` (SPF, DKIM) dan DMARC di DNS, isi `.env.production`
  (`MAIL_*`, `QUEUE_CONNECTION=database`, `OWNER_EMAIL`), deploy, uji kirim ke Gmail **kotak masuk** (bukan spam) dan satu penyedia lain, uji Brevo sekali.
- [x] **Layanan.** `EmailCodeService` (terbit, verifikasi, batas percobaan, hanguskan kode lama saat terbit baru, batas kirim per email), `GoogleAccountLinker`, dan `CountSentMail`
  (penghitung email terkirim per hari, pemberitahuan ke `OWNER_EMAIL` satu kali per hari pada 80% `MAIL_DAILY_LIMIT`). Logika akun ada di `Api\AuthController` (tidak dipisah jadi `AuthService`).
  Kode disimpan sebagai HMAC-SHA256 dengan `APP_KEY`, terikat ke user dan tujuan. Kode asli hanya ada di email dan di tabel `jobs` sampai email terkirim.
- [x] **Endpoint** sesuai kontrak 31A, dengan pembatas laju: `auth/register`, `auth/verify-email`, `auth/resend-verification`, `auth/login`, `auth/forgot-password`, `auth/reset-password`,
  `auth/logout`, `auth/logout-all`, `PUT account/password`, `GET me`. **`DELETE account` belum ada, dikerjakan di 31D** (butuh penghapusan menyeluruh).
  Aturan yang dipilih saat menulis (tidak ada di kontrak, tolong koreksi kalau tidak setuju): register menolak `privacy_policy_version` selain versi yang berlaku (`config/habitflow.php`);
  email dinormalkan huruf kecil; register untuk email yang belum terverifikasi **menimpa nama dan kata sandi** dengan pendaftar terbaru (yang memegang email itu); lupa kata sandi
  hanya mengirim kode ke akun yang emailnya sudah terverifikasi; kata sandi baru divalidasi **sebelum** kode diperiksa supaya sandi lemah tidak menghanguskan kode; 10 percobaan kode per
  15 menit per email dan 30 per IP (429); ganti kata sandi salah 5 kali dikunci 15 menit.
  `POST auth/google` sekarang mengembalikan `account` (kontrak) **dan** `user` (isi sama) supaya app yang sudah terpasang tidak rusak. Android pindah ke `account` di 31F, lalu `user` dihapus.
- [x] **Aturan penautan Google** (`GoogleAccountLinker`, dipakai API dan web): Google hanya ditautkan ke akun yang `email_verified_at`-nya terisi. Kalau akun dengan email itu ada tapi **belum terverifikasi**,
  login Google mengambil alih: `password` dikosongkan, semua token dicabut, `remember_token` diganti (sesi web mati), `google_sub` dan `email_verified_at` diisi. Login Google hanya diterima kalau klaim
  `email_verified` dari Google benar (API: sudah ada, web: ditambah). **Diuji lewat API; jalur web memakai linker yang sama tapi belum punya tes Feature sendiri (masuk 31C).**
- [x] Pencabutan token: `logout`, `logout-all`, reset dan ganti kata sandi, sesuai kontrak (ganti kata sandi mempertahankan token yang dipakai).
- [x] **Tes Laravel** (`tests/Feature/AccountAuthApiTest.php`, 47 tes): pengambilalihan akun, kode kedaluwarsa, sekali pakai, lima salah menghanguskan, batas kirim
  ulang, kode disimpan sebagai hash, pencabutan sesi untuk semua jalur, jawaban identik untuk email terdaftar dan tidak, login salah, rate limit, Google dan email pada akun
  yang sama, akun lama terverifikasi oleh migrasi. Seluruh suite 134 tes lulus.
- [x] **Celah yang dibiarkan sampai 31C (ditutup di 31C):** pendaftaran **web** (`RegisteredUserController`) masih langsung login tanpa verifikasi dan membuat akun belum terverifikasi. Itu tidak membuka celah baru
  (akun itu bisa diambil alih Google yang sah) tapi berarti web belum memakai alur kode. Dikerjakan di 31C.
- [ ] Catatan untuk sub-tahap lain: **31D** — tes inventaris skema harus mengenal `email_codes` (hapus saat hapus akun) dan `jobs`/`failed_jobs` (bukan data akun, tapi berisi kode asli sementara).
  **31K** — bersihkan `failed_jobs` berkala dan pantau antrean macet.

**Selesai jika:** semua tes lulus (✅), email uji sampai di Gmail **kotak masuk** dan satu penyedia lain (belum), dan alur register, verifikasi, login, lupa dan reset kata sandi jalan dengan `curl` (belum, di server produksi).

#### 31C. Server: halaman web

Status: **kode dan tes selesai (12 Oktober 2026), kecuali hapus akun dan teks hukum yang masih menunggu pemilik.** Bergantung pada 31B (kode ✅, email sungguhan belum).

- [x] Register web memakai alur kode yang sama (halaman `/verifikasi-email`), dengan centang persetujuan kebijakan privasi (teks menyebut data kesehatan). Daftar tidak langsung masuk; masuk setelah kode benar.
  Login akun belum terverifikasi (kata sandi benar) tidak masuk, mengirim kode, lalu diarahkan ke halaman itu. Celah 31B ("web belum memakai alur kode") **tertutup**.
  Aturan akun dipindah ke `AccountService` supaya API dan web memakai batas laju dan kode yang sama.
- [x] `/lupa-kata-sandi` dan `/reset-kata-sandi` (kode dan sandi baru), memakai kartu yang sama dengan Login. Jawaban sama untuk email terdaftar atau tidak. Reset mencabut semua token API dan semua sesi browser.
- [x] Halaman **`/akun`** (tautan di nama pengguna di kepala halaman): ubah kata sandi (atau "Buat kata sandi" untuk akun Google), "Keluar dari semua perangkat", dan Hubungkan/Putuskan Google (pindah dari Beranda).
  **Pemutusan sesi browser sungguhan:** kolom `users.session_epoch` dan middleware `EnsureSessionNotRevoked`. Sebelumnya hanya cookie "ingat saya" yang putus, sedangkan sesi file yang sedang hidup tetap berjalan.
  Ganti kata sandi di web mempertahankan sesi ini dan mengeluarkan yang lain. Dua penjagaan tambahan yang tidak diminta: **Putuskan Google ditolak kalau akun belum punya kata sandi** (akan terkunci), dan
  **Hubungkan Google ditolak kalau akun Google itu sudah dipakai akun lain** (sebelumnya galat 500).
- [x] **Hapus akun di web: dikerjakan di 31D** (halaman Akun dengan konfirmasi kata sandi, dan kode email untuk akun Google). Sengaja ditunda dari 31C supaya tidak ada penghapusan setengah data.
- [~] Halaman publik **`/hapus-akun`**: informasi dan formulir kode email sekarang berfungsi (31D). **Langkah "Profil lalu Hapus akun" di app sudah ada sejak 31I (13 Oktober 2026), jadi halaman ini boleh didaftarkan di Play Console begitu versi app itu dirilis.** Catatan lama: langkah
  "halaman Akun lalu Hapus akun" yang disebutnya belum ada. Jalur "masuk lewat kode email untuk yang sudah mencopot app" juga belum ada: teks sekarang menyuruh masuk di web dengan akun yang sama
  dan memberi alamat email kontak. Kalau pemilik mau jalur kode email yang sungguhan, itu dikerjakan di 31D.
- [~] Halaman publik **`/kebijakan-privasi`** (HTML, URL tetap, versi bernomor dari `PRIVACY_POLICY_VERSION`, tanggal dari `PRIVACY_POLICY_EFFECTIVE`, persetujuan mencatat versinya) **ditulis dari inventaris data oleh Claude, belum ditinjau pemilik.**
  **Harus dibaca dan disetujui pemilik sebelum rilis.** Bagian yang saya isi dengan asumsi dan harus diperiksa:
  1. **Kontak:** diambil dari `PRIVACY_CONTACT_EMAIL` (kosong di `.env.example`). Kalau kosong, halaman tidak menampilkan kontak, jadi Gerbang C belum terpenuhi sampai diisi.
  2. **Lokasi server:** ditulis "server milik pengelola di rumah pengelola di Indonesia". Betulkan kalau tidak persis begitu.
  3. **Cadangan 30 hari terenkripsi di penyimpanan terpisah (Cloudflare R2):** itu **rencana 31K, belum ada**. Kalimatnya baru benar setelah 31K selesai.
  4. **Penghapusan langsung dan permanen:** benar di server sejak 31D; di app sejak 31I (Profil, Hapus akun).
  5. **"Tidak ditujukan untuk anak di bawah 13 tahun":** angka itu saya pilih sendiri, bukan keputusan pemilik.
  6. Pihak ketiga yang disebut: Google, Resend (Brevo cadangan), Cloudflare. Tambahkan kalau ada lagi.
  7. Rujukan UU PDP (pemberitahuan 3 x 24 jam) dari dokumen rilis, **bukan nasihat hukum**. Untuk rilis publik sebaiknya dibaca orang yang paham hukum.
  Prosedur kebocoran data (Gerbang C butir 4) **belum ditulis**, itu 31K.
- [x] Tes Feature (`tests/Feature/AccountWebTest.php`, 39 tes): daftar dengan dan tanpa persetujuan, verifikasi (benar, salah, kedaluwarsa, sekali pakai, laju), masuk (belum terverifikasi, salah, dikunci), lupa dan reset,
  halaman Akun, ganti kata sandi, pemutusan sesi, halaman publik tanpa login, dan Google di web (akun baru, pengambilalihan, email belum diverifikasi, tabrakan, putuskan). Seluruh suite 173 tes lulus.
  **Belum dilihat di browser:** tampilan halaman baru (kode 6 digit, kartu Akun, halaman hukum) hanya diuji lewat HTML, belum dilihat mata.

**Selesai jika:** pemilik bisa daftar, verifikasi, reset kata sandi, dan menghapus akun uji dari browser (**hapus akun belum, 31D**), dan `/hapus-akun` serta `/kebijakan-privasi` terbuka tanpa login (✅).
Daftar, verifikasi, dan reset dari browser baru bisa dicoba nyata setelah email sungguhan hidup (31B).

#### 31D. Server: hapus akun menyeluruh dan penjaga skema

Status: **selesai di server (12 Oktober 2026).** Bergantung pada 31B (kode ✅). Tombol di app Android ada di Profil sejak 31I.

- [x] `AccountDeletionService`, dipakai API dan web, dalam **satu transaksi**: semua tabel di inventaris, `user_tokens`, `email_codes`, snapshot (baris **dan berkas**, berkas dihapus setelah transaksi berhasil),
  email yang tertahan di `jobs` dan `failed_jobs` (dicocokkan dengan alamat email), dan data tim. Tabel `sessions` tidak ada (sesi file), dan sesi browser mati sendiri begitu barisnya hilang.
  Diuji: kalau satu langkah gagal di tengah, tidak ada baris atau berkas yang hilang.
- [x] **Aturan tim:** pemilik dengan anggota lain memindahkan kepemilikan ke anggota yang **paling dulu bergabung**; to-do tim buatan pengguna tetap ada dengan `created_by` kosong (kolom dibuat nullable,
  migrasi `prepare_account_deletion`); tanpa anggota lain, tim dan isinya dihapus. Anggota biasa hanya keluar. Undangan buatan pengguna hangus; undangan yang dia pakai tetap ada tanpa `used_by`.
  **Keputusan yang saya tambahkan:** aturan "to-do tetap ada" berlaku juga untuk to-do buatan **anggota biasa** (spesifikasi hanya menyebut pemilik). Nama "Pengguna dihapus" belum perlu ditampilkan di mana pun karena
  pembuat to-do tim tidak ditampilkan di web maupun app (Android tidak membaca `created_by`); `created_by` kosong sudah mewakilinya.
- [x] Catatan audit **tanpa data pribadi**: tabel `account_deletions` (`id`, `source` = `app` | `web` | `web-code`, `deleted_at`). Diuji bahwa kolomnya hanya itu.
- [x] **Cara konfirmasi** (kontrak 31A + 31C): API `DELETE /account` (kata sandi, atau `id_token` Google untuk akun tanpa sandi, salah 5 kali dikunci 15 menit); web halaman Akun (kata sandi); dan
  **jalur kode email publik** di `/hapus-akun` untuk orang yang sudah mencopot app, lupa kata sandi, atau akun Google (kode 6 angka tujuan `delete`, berlaku 10 menit, sekali pakai, dibatasi seperti kode lain).
  Kode lupa kata sandi tidak bisa dipakai menghapus akun (diuji).
- [x] **Tes inventaris skema** (`SchemaInventoryTest` + `tests/Support/DataInventory.php`): gagal kalau ada tabel yang tidak ada di daftar "dihapus" maupun "bukan data pengguna, beserta alasannya", atau kalau daftar menyebut tabel
  yang sudah tidak ada. **Dibuktikan sekali:** tabel percobaan `weight_entries` membuat tesnya gagal dengan pesan yang menyebut nama tabelnya, lalu lulus lagi setelah dihapus.
- [x] **Tes hapus akun menyeluruh**: pengguna dengan baris di **setiap** tabel dan berkas snapshot, hapus, **nol baris dan nol berkas**. Tes pendamping memastikan `populate()` benar-benar mengisi tiap tabel di daftar
  (kalau tidak, tabel baru yang lupa diisi akan lolos karena memang kosong). Tes lain: data pengguna lain tidak tersentuh.
- [x] Tes kasus tim (tiga kasus + undangan), tes token ikut tercabut (API 401 setelah hapus), dan **tes log** (`LogLeakTest`): isi snapshot yang diterima, ditolak (bukan JSON, struktur salah, terlalu besar, token salah), atau memicu
  galat 500, serta kata sandi dan kode di jalur masuk, daftar, dan hapus akun, tidak masuk log. **Berat dan tensi belum punya endpoint** (tahap 30): tambahkan permintaannya ke tes itu saat endpoint dibuat.
- [x] `php artisan test` jadi langkah wajib sebelum deploy di `server/DEPLOY.md`.
- [x] **Diuji di MariaDB 10.4 (XAMPP, instance sementara), bukan hanya SQLite:** seluruh suite 208 tes lulus di keduanya. **Itu menemukan cacat nyata di 31B** (lihat di bawah). Versi MySQL produksi (`shared-mysql`) belum dicek:
  jalankan `docker exec shared-mysql mysqld --version` dan, kalau mungkin, jalankan suite sekali terhadap database uji di sana sebelum deploy.

**Cacat 31B yang ditemukan dan diperbaiki di sini:** kolom `email_codes.expires_at` bertipe `TIMESTAMP NOT NULL` otomatis mendapat `ON UPDATE CURRENT_TIMESTAMP` di MySQL 5.7 dan MariaDB, sehingga **satu tebakan salah menimpa waktu
kedaluwarsa dengan "sekarang" dan langsung menghanguskan kode**. Di MySQL 8 tidak terjadi. Diperbaiki dengan migrasi `make_expires_at_plain_datetime` (jadi `DATETIME`, juga untuk `team_invites.expires_at` yang punya masalah sama),
plus tes regresi `test_tebakan_salah_tidak_mengubah_waktu_kedaluwarsa_kode`.

**Celah yang diketahui:**
- **Snapshot lama belum terikat ke akun** *(kode ditutup di 31E, tinggal dijalankan di produksi lewat `habitflow:claim-legacy`)*. `snapshots.user_id` baru ditambahkan (nullable) dan sudah ikut terhapus kalau terisi, tapi yang mengisinya adalah **31E** (klaim snapshot token per-HP ke akun pemilik). Sampai klaim dijalankan, snapshot hasil token 19B
  (milik pemilik dan istri) tidak ikut terhapus saat akunnya dihapus. Pengguna Play Store tidak pernah punya token 19B, jadi ini tidak memengaruhi mereka.
- Berkas snapshot yang gagal dihapus setelah transaksi (disk bermasalah) hanya dicatat di log (`Hapus akun: berkas snapshot gagal dihapus`, tanpa isi), tidak dilaporkan ke pengguna. Itu pembersihan manual.
- Email di `jobs`/`failed_jobs` dicocokkan sebagai teks di payload; alamat email yang berisi `/` tersimpan dengan escape JSON dan tidak akan cocok (sangat jarang).
- Backup database yang sudah ada tidak disentuh, sesuai keputusan (hilang saat berputar). Kebijakan privasi sudah menyebutnya.

**Selesai jika:** semua tes lulus (✅) dan menambah tabel tanpa memutuskan nasibnya saat hapus akun membuat tes gagal (✅, dibuktikan dengan mencobanya sekali).

#### 31E. Server: snapshot ke akun, pensiunkan token 19B

Status: **selesai di kode dan tes (12 Oktober 2026), belum di-deploy.** Bergantung pada 31B (kode ✅).

- [x] Migrasi `2026_10_12_130000_move_snapshots_to_accounts`: `snapshots.api_token_id` jadi nullable (snapshot baru tidak punya token). Kolom `user_id` sudah ada dari 31D. Kolom dan tabel `api_tokens` **sengaja
  dibiarkan sampai 31L** supaya rilis server sebelumnya masih bisa dipakai kalau harus jalan mundur. Diuji naik, turun, dan naik lagi di MariaDB 10.4.
- [x] Perintah artisan **`habitflow:claim-legacy {email} [--token=nama] [--dry-run]`**: menautkan snapshot milik token 19B ke akun, dijalankan satu kali per HP (pemilik, lalu istri). Mencetak hitungan sebelum dan
  sesudah. **Keputusan yang saya tambahkan:** opsi `--token` (spesifikasi hanya menyebut `{email}`), karena ada dua HP dan satu email tidak cukup untuk memilih snapshot yang mana; kalau tokennya lebih dari satu dan `--token`
  tidak diisi, perintah gagal dan menyebut pilihannya. Idempoten: hanya baris yang `user_id`-nya masih kosong yang disentuh, jadi snapshot yang sudah milik akun lain tidak dirampas. Batas 14 salinan per akun
  diterapkan setelah klaim. Token lamanya **tidak dihapus** (alasan sama dengan di atas).
- [x] `routes/api.php`: `PUT /snapshot`, `GET /snapshot/latest`, dan `GET /ping` pindah ke `user-token`. Batas ukuran (5 MB), laju (60 per menit), dan retensi 14 salinan tetap, sekarang **per akun**. Berkas baru
  disimpan di `snapshots/u{user_id}/`; berkas lama tetap di `snapshots/{token_id}/` (jalurnya tersimpan di barisnya, jadi tetap terbaca dan ikut terhapus saat hapus akun).
- [x] **Dipensiunkan:** middleware `AuthenticateToken` (alias `token`) dan perintah `habitflow:token` dihapus. Tidak ada lagi cara membuat atau memakai token 19B. Hash isi snapshot kini dibandingkan hanya dengan snapshot
  akun yang sama (isi yang sama milik dua akun berbeda tidak dianggap "tidak berubah").
- [x] Tes: `SnapshotApiTest` ditulis ulang untuk token akun (termasuk token 19B lama menjawab 401, snapshot akun lain tidak terbaca, batas 14 per akun), `ClaimLegacyTest` baru (klaim, dry-run, idempoten, dua HP ke
  dua akun, tidak merampas, token atau akun tidak ada, batas 14, ikut terhapus saat hapus akun), `LogLeakTest` memakai token akun. Suite 218 tes lulus di SQLite dan di MariaDB 10.4.

**Dampak saat deploy (penting):** begitu 31E di-deploy, **app Android yang terpasang sekarang tidak bisa sinkron** (ia masih mengirim token 19B, jawabannya 401) sampai 31H selesai. Itu memang bagian dari rencana
("token lama tidak lagi diterima"), tapi berarti cadangan snapshot dari HP pemilik terhenti sepanjang jeda itu. Urutan aman: (1) pastikan akun pemilik dan istri sudah ada, (2) deploy, (3) jalankan
`habitflow:claim-legacy ... --dry-run` lalu tanpa `--dry-run` untuk tiap HP, (4) catat bahwa sinkron mati sampai 31H. Kalau jeda itu tidak bisa diterima, tunda deploy 31E sampai dekat 31H.

**Selesai jika:** snapshot lama pemilik terbaca lewat akunnya (✅ diuji lewat API), dan token lama tidak lagi diterima (✅). **Belum dikerjakan:** menjalankan klaim terhadap data produksi (butuh deploy, di luar sesi ini).

#### 31F. Android: lapisan akun dan sesi

Status: **selesai di kode dan tes (13 Oktober 2026), belum diuji di perangkat.** Bergantung pada 31B (kode ✅, email sungguhan belum). Sisa yang disebut di bawah menunggu 31G dan perangkat sungguhan.

- [x] `AccountApi` (antarmuka), `AccountClient`, dan `AccountManager`: register, verifikasi, kirim ulang, login email, Google, lupa dan reset, keluar (mencabut token di server), keluar dari semua perangkat,
  ubah kata sandi, hapus akun, dan `me`. Semua hasil bertipe `AccountResult` (`Success`, `Failed(status, code, message, retryAfterSeconds)`, `Unauthorized`, `Offline`); kode galat ada di `AccountErrors`.
  Keputusan yang saya ambil: (1) pesan galat 422 validasi Laravel memakai galat field pertama (field `message`-nya hanya "dan N galat lain"), diberi kode `validation`; (2) **Keluar selalu menutup sesi di HP**
  walau server tidak terjangkau, hasilnya `Offline` memberi tahu pemanggil bahwa token di server belum dicabut (31H memutuskan peringatannya); 401 pada logout dianggap berhasil; (3) **keluar dari semua perangkat
  hanya menutup sesi lokal kalau server benar-benar mencabut**; (4) reset kata sandi hanya menutup sesi yang masih `SignedIn`, sesi `Expired` dibiarkan supaya email-nya tetap tersedia; (5) `SignInResult` ditambah `Offline`
  (`AccountViewModel` disesuaikan); `LoginResult` lama dihapus. `POST auth/google` sekarang mengirim `device_name` dan membaca `account` (bukan `user`), jadi `user` di server boleh dihapus setelah 31F ini terpasang.
- [x] `AccountSettings`: status sesi `SessionState` (`SignedOut`, `SignedIn(account)`, `Expired(email, name)`) dan **`ownerAccountId`**, di `account.xml` (tidak ikut backup). `Expired` membuang token tapi menyimpan email dan nama
  untuk layar Masuk. **`ownerAccountId` sengaja terpisah dari sesi**: bertahan saat sesi berakhir dan saat keluar, hanya `clearOwnerAccountId()` yang menghapusnya (dipanggil bersama penghapusan data lokal di 31H).
  31F **belum mengisi** `ownerAccountId` dari mana pun: itu bagian "Ambil alih data lama" di 31H. `account: StateFlow<Account?>` dipertahankan (null kecuali `SignedIn`) supaya penjadwal latar belakang yang ada berhenti sendiri saat sesi berakhir.
- [x] **Penanganan 401 di satu tempat.** `AccountClient`, `HabitSyncClient` (habit, to-do, dan follow-up ada di klien yang sama), `SyncClient`, dan `TeamClient` melapor ke `UnauthorizedListener` di dalam `request()` mereka,
  jadi tidak ada jalur yang bisa terlewat. `SessionGuard` mengimplementasikannya dan menandai sesi `Expired` tanpa menghapus data lokal. **Yang dilaporkan adalah token yang ditolak**, bukan sekadar "ada 401": `AccountSettings.expire(token)`
  hanya bekerja kalau token itu masih token sesi sekarang, supaya permintaan terlambat dengan token lama tidak mematikan sesi baru. Pendaftaran listener dibuat **wajib** (tanpa nilai bawaan) di `SyncManager`,
  `HabitSyncManager`, `TeamViewModel`, dan `pollTeamTodosForNewAssignments`, jadi klien baru yang lupa dipasang gagal dikompilasi. Diperiksa dengan pencarian kode: satu-satunya yang mengubah sesi karena 401 adalah `SessionGuard`;
  `HabitSyncManager` dan `TeamViewModel` hanya menampilkan pesan dari hasil `Unauthorized`.
- [x] **Konfigurasi build** (`app/build.gradle.kts`): `BuildConfig.API_BASE_URL` dan `GOOGLE_SERVER_CLIENT_ID` per varian. **Keputusan yang saya ambil dan tolong koreksi kalau tidak setuju:** debug **tetap ke server produksi** kecuali
  `API_BASE_URL_DEBUG` diisi di `local.properties` (mis. `http://10.0.2.2:8000` untuk server lokal di emulator), supaya HP pemilik yang memasang build debug tidak putus sinkron; rilis selalu `https://habitflow.roziqrizal.com` dan tidak bisa
  dialihkan. Client ID: `GOOGLE_SERVER_CLIENT_ID_DEBUG` atau `_RELEASE` kalau ada, kalau tidak jatuh ke `GOOGLE_SERVER_CLIENT_ID` yang lama (jadi `local.properties` yang sekarang tetap jalan). `http://` hanya lolos ke emulator dan localhost
  oleh `network_security_config` debug yang sudah ada. **Pembacaan alamat dari `SyncSettings` dihentikan:** cadangan snapshot (`SyncManager`) memakai token akun dan `API_BASE_URL`; `SyncUrl`, `SyncConfig`, dialog "Atur server", dan
  `allowLocalCleartext` dihapus; `SyncSettings` tinggal `enabled`, status, dan id perangkat, dan **menghapus sisa `server_url` dan `server_token` 19B dari `sync.xml` saat app dibuka**. `SyncScheduler` aktif hanya kalau sinkron otomatis menyala **dan** ada sesi
  `SignedIn`, serta dipasang ulang tiap sesi berubah.
- [x] **Debug saja:** `DebugSessionReceiver` di `src/debug` (perintah `set`, `expire`, `clear`, `owner`, `owner-clear`, contoh di KDoc-nya). Diperiksa di manifest hasil gabungan: ada di debug, **tidak ada di rilis**.
  **Belum dijalankan di perangkat atau emulator** (tidak ada yang terhubung saat sesi ini), hanya terkompilasi dan terdaftar. Pemeriksaan otomatis "tidak ada di rilis" adalah tugas 31J.
- [x] Tes (seluruh suite Android: 398 tes lulus): `AccountClientTest` (24: semua jalur dan isian kontrak, kode galat, `Retry-After`, validasi Laravel, jawaban rusak, 401 dengan dan tanpa token, offline, alamat bukan http),
  `AccountManagerTest` (30, klien palsu untuk tiap hasil), `AccountSettingsTest` (14, termasuk `ownerAccountId` tersimpan dan bertahan, token lama terlambat, berkas format lama tahap 25), dan satu tes 401 di setiap klien
  (`SyncClientTest`, `HabitSyncClientTest` untuk habit/to-do dan follow-up, `TeamClientTest`). Satu tes klien-ke-`SessionGuard`-ke-`AccountSettings` menunjukkan 401 benar-benar menghasilkan `Expired`.
  Tes menemukan **cacat nyata** yang sudah diperbaiki: alamat bukan http(s) membuat `openConnection() as HttpURLConnection` melempar `ClassCastException` (bukan `IOException`) dan lolos dari penangkapan; sekarang jadi `Offline`.

**Yang sengaja belum, dan keadaan sementara sampai 31G:** tidak ada layar masuk email, daftar, atau verifikasi di app; `AccountManager` sudah siap tapi belum ada yang memanggilnya selain tombol Google lama di Tentang.
Saat sesi `Expired`, layar-layar lama membaca `account` sebagai kosong dan menampilkan tombol "Masuk dengan Google" seperti saat belum masuk, jadi akun email yang sesinya berakhir **belum bisa masuk lagi lewat UI**
(di HP pemilik dan istri yang memakai Google tidak masalah). Pesan "Sesi berakhir" yang tenang dan email terisi adalah pekerjaan 31G. Cadangan snapshot memakai token akun: **HP yang belum masuk ke akun tidak
mencadangkan** (memang bagian rencana 31E: token 19B sudah tidak diterima server).

**Belum diverifikasi di perangkat:** `DELETE /account` mengirim body JSON lewat `HttpURLConnection`. Itu jalan di JVM (tes), tapi implementasi Android-nya (OkHttp internal) belum dicoba; kalau ternyata menolak body pada DELETE,
perbaikannya ada di `AccountClient.call`. Jalur peningkatan dari versi sebelum 31F (penghapusan `server_token` lama, `account.xml` tanpa penanda `expired`) diuji lewat tes `AccountSettingsTest` dan dibaca dari kode, belum dicoba pada HP yang sungguh diperbarui.

**Selesai jika:** semua hasil termodelkan dan teruji (✅), dan tidak ada klien yang menangani 401 sendiri (✅, `SessionGuard` satu-satunya yang mengubah sesi; diperiksa dengan pencarian kode dan dijaga oleh parameter listener yang wajib).

#### 31G. Android: gerbang dan layar auth

Status: **selesai di kode, tes, dan emulator (13 Oktober 2026); belum diuji di HP fisik dengan email sungguhan.** Bergantung pada 31F (✅). Nilai desain layar yang belum punya mockup ditulis lebih dulu di `docs/design/README.md`.

- [x] **`AuthGate`** (`ui/auth/AuthGate.kt`) membungkus seluruh isi app di `MainActivity`: isi app (dan semua `ViewModel`-nya) baru dibuat setelah `SignedIn`; selain itu hanya layar auth. `Expired` membuka Masuk dengan banner "Sesi berakhir, masuk lagi." dan email terisi.
  **Keputusan:** tidak ada keadaan `Loading`. `AccountSettings` membaca sesinya secara sinkron saat dibuat, jadi sesi sudah diketahui sebelum layar pertama digambar. **Gerbang baru menahan layar**: alarm, notifikasi, dan pekerja latar belakang masih hidup
  selagi belum masuk, dan dialog izin notifikasi masih muncul di atas layar Masuk pada instalasi baru. Itu bagian `SessionManager` di 31H.
- [x] Layar **Masuk**, **Daftar**, **Verifikasi kode**, **Lupa kata sandi**, dan **Reset kata sandi**, plus tombol **Google** (`ui/auth/AuthScreens.kt`, `AuthComponents.kt`). Verifikasi memakai 6 kotak di atas satu kolom teks sungguhan (bisa ditempel), kirim otomatis begitu 6 angka terisi,
  hitung mundur kirim ulang 60 detik dan masa berlaku 10 menit. **Keputusan:** Lupa dan Reset kata sandi dua layar (email, lalu kode dan sandi baru dalam satu layar), bukan tiga. Login yang ditolak `email_not_verified` membuka Verifikasi dan **meminta kode baru otomatis**, karena
  server tidak mengirim kode saat login. Nilai desain: lihat `docs/design/README.md`.
- [x] **`AuthStateMachine`** (mesin keadaan murni, tanpa Android) dan `AuthViewModel` sebagai pembungkus tipisnya: validasi lokal, tombol ditekan dua kali hanya mengirim satu permintaan, galat server dipetakan ke kalimat tenang (galat server 5xx tidak menampilkan kode HTTP, galat jaringan memakai warna netral).
- [x] **Link undangan tim** disimpan di `PendingInviteStore` (`pending_invite.xml`, tidak ikut backup), jadi bertahan melewati gerbang dan proses yang dimatikan, lalu diproses begitu masuk. Layar Masuk dan Daftar menyebut undangan yang menunggu.
- [x] Hari pertama tanpa internet: Masuk dan Daftar bilang "Masuk dan daftar butuh internet.", dan galat jaringan tidak berwarna merah.
- [x] **Persetujuan kebijakan privasi untuk Google (keputusan saya, mohon dikoreksi).** Server hanya mencatat persetujuan kalau `privacy_policy_version` dikirim saat akun Google **dibuat**. Karena tombol Google di layar Masuk bisa membuat akun baru, Android kini selalu mengirim versi
  itu untuk Google, dan layar Masuk menulis di bawah tombolnya "Dengan masuk memakai Google, kamu menyetujui Kebijakan Privasi, termasuk penyimpanan data kesehatanmu di server." (tertaut). Di layar Daftar, tombol Google menolak kalau kotak persetujuan belum dicentang.
  Ini persetujuan lewat kalimat "dengan melanjutkan", bukan kotak centang; untuk data kesehatan pemilik mungkin mau kotak centang di layar Masuk juga, **perlu keputusan sebelum Gerbang C**.
- [x] Aksesibilitas yang dikerjakan: tinggi sentuh minimal 48dp (tombol) dan 44dp (tautan, baris centang), ikon mata berlabel "Tampilkan/Sembunyikan kata sandi", galat dan banner `liveRegion`, jenis keyboard dan aksi IME (Next/Done) per kolom, kolom kode satu kolom sungguhan berlabel.
  **Yang tidak jadi:** isi otomatis lewat `ContentType` (email, kata sandi, kode SMS). API itu `internal` di Compose 1.7.0 yang dipakai proyek ini (publik di 1.8); tanpa menaikkan Compose, isi otomatis bergantung pada heuristik sistem dari jenis keyboard. Dicatat di sini supaya dinilai saat Compose dinaikkan.
- [x] Tes: `AuthStateMachineTest` (48 tes: tiap jalur di spesifikasi, plus tombol dobel, Google di Daftar tanpa persetujuan, sesi berakhir yang tidak mereset isian yang sedang diketik), `PendingInviteStoreTest` (5). Seluruh suite Android lulus.
- [x] **Diuji di emulator** (Pixel 6 API 34) dengan **server tiruan PHP lokal** (bukan server produksi, bukan Laravel sungguhan): Masuk, Daftar (validasi dan pengiriman), Verifikasi (kode salah lalu benar), Lupa lalu Reset, banner Sesi berakhir, dan masuk sampai layar Hari ini. Permintaan ke server diperiksa di log mock (nama dipangkas,
  email huruf kecil, `privacy_policy_version` ikut). Bukti tambahan: sesi uji palsu mendapat 401 dari sinkron habit di dalam app, dan `SessionGuard` mengembalikan ke Masuk dengan banner (31F dibuktikan dari ujung ke ujung).

**Cacat lama yang ditemukan lewat uji ini dan diperbaiki (commit terpisah):** instalasi **baru** langsung crash saat membuat database (`NOT NULL constraint failed: habits.dirty`), karena `SeedCallback` tidak mengisi kolom `dirty` yang sejak tahap 28 `NOT NULL` tanpa nilai bawaan pada database yang dibuat baru (nilai bawaan hanya ada di jalur migrasi).
Berlaku bagi setiap pengguna baru, termasuk Play Store; tidak terlihat sebelumnya karena HP pemilik dan istri memakai database hasil migrasi. Diperbaiki dengan mengisi `dirty = 1`. **Belum ada tes otomatis untuk pembuatan database baru** (Room butuh instrumented test); disarankan masuk ke 31J.
Dua temuan UX juga diperbaiki: keyboard menutup setelah kode salah (kolom kehilangan fokus saat dinonaktifkan), dan tangkapan layar menunjukkan judul tertimpa status bar saat layar digulir dengan keyboard terbuka (kosmetik, dibiarkan).

**Belum:** uji dengan email sungguhan dan Google sungguhan di HP fisik (butuh 31B email hidup dan Client ID); layar Masuk dengan banner dan layar Reset belum dicoba dengan pembaca layar; tidak ada tes UI otomatis (hanya mesin keadaan). Menginstal build debug ke HP pemilik sekarang menampilkan layar Masuk kalau sesinya hilang.
Sesi lama dari tahap 25 (`account.xml` tanpa penanda `expired`) tetap terbaca sebagai `SignedIn` (ada tesnya), jadi HP yang sudah masuk dengan Google tidak diminta masuk lagi.

**Selesai jika:** di HP fisik pengguna bisa daftar, verifikasi lewat email sungguhan, keluar, masuk lagi, dan reset kata sandi (di emulator dengan server tiruan ✅, di HP fisik belum), dan tidak ada jalan masuk ke app tanpa sesi (✅ untuk layar; alarm dan latar belakang di 31H).

#### 31H. Android: siklus hidup sesi, Keluar, data lama, ganti akun

Status: **selesai di kode, tes, dan emulator (13 Oktober 2026); belum diuji di HP fisik.** Bergantung pada 31F (✅) dan 31E (✅ di kode).

- [x] **`SessionManager`** (`session/`) satu-satunya yang menyalakan dan mematikan latar belakang menurut sesi: sinkron, polling tim, pekerja langkah, alarm jadwal, alarm jam (Subuh), notifikasi, dan alarm yang sedang berbunyi. Hanya hidup selagi `SignedIn`; sesi berakhir, Keluar, dan belum pernah masuk
  semuanya mematikannya. `HabitFlowApplication` tidak lagi memulai apa pun sendiri, `MainActivity` hanya mengamati notifikasi selagi masuk dan baru meminta izin notifikasi **setelah** masuk (dulu muncul di atas layar Masuk). Komponen yang bisa terpicu sistem **memeriksa `SessionGate` sendiri-sendiri** karena
  pembatalan bisa kalah cepat dari alarm yang sudah terjadwal: `ScheduleReceiver` (termasuk boot), `ScheduleNotifier.refresh` (tanpa sesi ia membersihkan sisa alarm lalu berhenti), dan semua pekerja (`StepsWorker`, `SyncWorker`, `HabitPushWorker`, `HabitPeriodicSyncWorker`, `TeamPollWorker`).
  Penjaga: `BackgroundGuardTest` memindai sumber, jadi receiver atau pekerja baru yang tidak memeriksa sesi (atau pemasang pekerjaan terjadwal baru) menggagalkan tes.
- [x] **Registri berkas pengaturan** `PrefFile` (tujuh berkas: `settings`, `calendar`, `sync`, `team`, `account`, `pending_invite`, `schedule_notifier_state`). Semua `SharedPreferences` dibuka lewat enum itu; `PrefWiper` menghapus semua isinya **kecuali** kunci tema (`theme_mode`, satu-satunya pengecualian,
  dijaga tes). `settings.xml` dipakai bersama banyak pengaturan, jadi yang dikecualikan adalah kuncinya, bukan berkasnya. `PrefFileRegistryTest` memindai sumber: pemanggilan `getSharedPreferences` langsung di luar registri menggagalkan tes.
- [x] **Keluar** (`SignOutCoordinator`, dialog di `AccountSection`). Dialog membuka dengan "Memeriksa perubahan...": dengan internet, habit/to-do dikirim dulu (dan cadangan snapshot **hanya kalau Sinkron otomatis menyala**), lalu perubahan yang tersisa dihitung: antrean centang habit, penghapusan tertunda, baris `dirty`
  atau belum pernah terkirim (habit, to-do, follow-up), dan keadaan cadangan snapshot (`BackupState`: `Off`, `Saved`, `Stale`, lewat `SnapshotFingerprint` yang mengabaikan waktu ekspor, id perangkat, dan versi). Tanpa internet, dihitung langsung dan dialog memperingatkan keras (tombol merah "Keluar dan hapus").
  **Keputusan saya (mohon dikoreksi):** Keluar **tidak** mengunggah snapshot kalau Sinkron otomatis mati, karena itu opt-in dan snapshot memuat data kesehatan. Gantinya dialog jujur bilang "Sinkron otomatis mati, jadi jadwal, data kesehatan, dan pengaturan hanya ada di HP ini dan ikut terhapus", dan peringatan ini muncul **setiap kali** Keluar dengan cadangan mati.
- [x] **Urutan Keluar dan mengapa data dihapus di awal proses berikutnya.** Keluar menulis penanda `no_backup/pending_wipe.json` (`PendingWipe`), mencabut token di server dan menutup sesi (sesi di HP selalu ditutup, juga saat server tak terjangkau), lalu memulai ulang proses. **Penghapusan sungguhan terjadi di awal proses baru, sebelum database dibuka**
  (`HabitFlowApplication.onCreate`): database Room beserta WAL, semua berkas pengaturan, dan cache. Alasan memilih ini daripada menghapus langsung: pengamat Room, `ViewModel`, dan pekerja masih memegang database saat berjalan, dan menghapusnya di tengah itu bisa membuat app crash tepat saat Keluar; dan penanda baru dihapus setelah semuanya selesai,
  jadi proses yang mati di tengah jalan mengulang penghapusan (idempoten). `AccountSettings` kini menulis dengan `commit` (sinkron), karena `apply` yang masih antre hilang saat proses dimatikan.
  **Akibat yang disengaja:** data yang dihapus membuat database baru (dengan habit awal) pada login berikutnya, sama seperti instalasi baru.
- [x] **Ambil alih data lama.** Sesi lama dari sebelum 31 (juga yang sudah berakhir) mengisi `ownerAccountId` dari id akun yang tercatat; kalau tidak ada akun sama sekali tetapi ada data, akun pertama yang masuk menjadi pemilik (`AccountSettings.save` mengklaim pemilik kalau kosong). Datanya sampai ke server lewat jalur gabung tahap 28 dan 27 yang sudah ada
  (baris lama sudah `dirty = 1` dari migrasi).
- [x] **Ganti akun di HP yang sama.** Login akun lain di HP berisi data pemilik lain **ditahan** di `AccountManager` (token hanya di memori, belum disimpan) dan layar auth menampilkan dialog "Data akun lain masih di HP ini". "Hapus dan masuk" menulis penanda hapus **beserta akun baru** lalu memulai ulang proses; di awal proses berikutnya data dihapus dan sesi akun baru dipulihkan dari penanda (penanda dihapus bersama itu).
  "Batal" mencabut token yang tertahan di server dan membiarkan data. **Akun yang sama** masuk lagi setelah sesi berakhir mempertahankan data tanpa restart dan tanpa pertanyaan.
- [x] **Hapus akun dari app** (`AccountSection`, dialog): memeriksa `GET me` untuk memilih cara konfirmasi (kata sandi, atau pilih akun Google untuk akun tanpa sandi), memanggil `DELETE account`, dan **hanya kalau server berhasil** menandai hapus dan memulai ulang proses. Kata sandi salah, tanpa internet, atau sesi berakhir tidak menghapus apa pun di HP.
  Tata letaknya sementara (di bagian Akun di Tentang); 31I memindahkannya ke Profil dan memakai ulang dialognya.
- [x] **Tes** (seluruh suite Android: 524 lulus): `PrefFileRegistryTest`, `SessionManagerTest`, `BackgroundGuardTest`, `PendingWipeTest` (termasuk penghapusan yang gagal diulang dan penanda rusak), `SignOutCoordinatorTest` (urutan Keluar, hitungan perubahan, teks peringatan, hapus akun gagal tidak menghapus apa pun), `AccountOwnerPolicyTest` (ganti akun, batal, akun sama),
  `SnapshotFingerprintTest`, dan 6 tes baru di `AuthStateMachineTest` untuk dialog ganti akun. Nama tes di bagian 3 `rilis-play-store.md` dipenuhi oleh yang bersangkutan, kecuali **dua yang tidak bisa dibuat sebagai tes unit JVM**: penghapusan **database Room sungguhan** (butuh instrumented test) dan penghitungan dari DAO sungguhan. Keduanya hanya diuji di emulator di bawah.
- [x] **Diuji di emulator** (Pixel 6 API 34, server tiruan PHP lokal dengan dua akun, bukan server produksi): instalasi baru tidak punya alarm (0) dan tidak menampilkan izin notifikasi; setelah masuk ada alarm jadwal dan **alarm Subuh 03.36**; Keluar menampilkan dialog dengan **10 perubahan** (benar: 9 habit awal + penanda), mencabut token di server (log mock), memulai ulang proses (PID berubah),
  membatalkan kedua alarm (`dumpsys alarm` mencatat `alarm_cancelled` untuk `ALARM_RING` dan `SCHEDULE_ALARM`), mengosongkan database dan ketujuh berkas pengaturan; **Budi masuk dan tidak melihat data Ani** (penanda `RAHASIA-ANI` = 0 di database Budi); sesi berakhir mematikan alarm (0) dan Budi masuk lagi **mempertahankan datanya** (penanda ada, PID sama);
  Ani masuk di HP milik Budi memunculkan dialog; **Batal** mempertahankan data dan mencabut token Ani di server; **Hapus dan masuk** memulai ulang, database baru tanpa penanda Budi, sesi Ani dipulihkan, `owner=7`, penanda hapus hilang; **Hapus akun**: kata sandi salah menampilkan galat dan database tetap utuh, kata sandi benar menghapus akun di server dan semua data di HP.

**Belum diuji:** HP fisik, Google (jalur masuk dan konfirmasi hapus akun Google), jalur `Stale` (cadangan menyala tapi berbeda), Keluar saat sesi sudah `Expired` (hanya teks dialognya yang diuji, sinkron akan menjawab `Unauthorized`), proses yang dimatikan tepat di tengah penghapusan (diuji di tingkat `PendingWipe` dengan tes, bukan dengan mematikan proses sungguhan),
dan alarm Subuh yang benar-benar tidak berbunyi (yang terbukti adalah **pembatalannya** dan penjaga di receiver, bukan menunggu jam 03.36).
**Catatan bagi 31I:** dialog Keluar dan Hapus akun (`SignOutDialog`, `DeleteAccountDialog`) dan bagian Akun di Tentang adalah tata letak sementara; teks dialog panjang karena menyebut tiap kemungkinan, dan layak dipadatkan saat dipindah ke Profil.
**Perilaku yang berubah dan perlu diketahui pemilik:** (1) Keluar sekarang menghapus data, tidak lagi sekadar keluar; (2) HP pemilik dan istri yang sudah masuk lewat Google tetap `SignedIn` setelah pembaruan dan otomatis dicatat sebagai pemilik data; (3) peringatan "Sinkron otomatis mati" akan muncul di setiap Keluar kalau cadangan snapshot tidak dinyalakan.

**Selesai jika:** diuji di HP fisik dengan dua akun bergantian, tidak ada data akun A yang terlihat di akun B (✅ di emulator, HP fisik belum), dan alarm Subuh yang sudah terjadwal tidak berbunyi setelah Keluar (✅ pembatalan terbukti, berbunyi atau tidaknya di jam 03.36 belum ditunggu).

#### 31I. Android: Pengaturan, Profil, Data tubuh

Status: **selesai di kode, tes, dan emulator (13 Oktober 2026).** Bergantung pada 31G dan 31D. Belum diuji di HP fisik dan dengan Google.

- [x] **Tab Tentang menjadi Setelan** (ikon roda gigi, `AppIcons.Settings`). `AboutScreen` menjadi `SettingsScreen`, judul layar "Pengaturan", bagian berkartu: Tampilan, Waktu dan sholat, Pengingat, Kalender, Data dan sinkron, Tentang. **Label tab "Setelan", bukan "Pengaturan"**: di emulator Pixel 6 (lebar item tab ±55dp) "Pengaturan" terpotong menjadi "Pengatur/an" di 11sp maupun 10sp.
  Baris chevron (lokasi, alarm, adzan, berat dan tensi, kalender) membuka **dialog**, bukan layar baru: `SettingsDialog` berisi bagian lama (`LocationSection`, `AlarmSection`, `AdzanSection`, `HealthReminderSection`, `CalendarSettingsSection`) tanpa mengubah perilakunya. Keterangan tiap baris dirangkum dari pengaturan (`alarmSummary`, `adzanSummary`, `healthReminderSummary`).
- [x] **Data tubuh pindah ke Profil**, bukan ke Pengaturan: diputuskan pemilik 11 Oktober 2026 mengikuti mockup (rencana lama di sini yang menyebut Pengaturan sudah usang). `BodyDataCard`: tinggi badan dan target berat, dialog angkanya dipakai ulang. Teks "Isi tinggi badan di Tentang" di Progres dan sheet kesehatan diganti "di Profil".
- [x] **Profil** (`ProfileScreen`, layar penuh di atas tab, tombol kembali dan tombol sistem menutupnya, lingkaran inisial di Hari ini membukanya): avatar inisial, nama, email, Data tubuh, **Cara masuk** (dari `GET me`: "Email dan kata sandi, Google terhubung" dan sejenisnya; gagal dimuat berarti baris bisa diketuk untuk mencoba lagi), **Ubah kata sandi** (atau **Buat kata sandi** untuk akun Google tanpa sandi),
  Sinkron, **Keluar**, **Keluar dari semua perangkat**, **Hapus akun**. Dialog Keluar dan Hapus akun dari 31H dipakai ulang; dialog Keluar kini punya varian semua perangkat (kalimat pembuka "Semua perangkat yang masuk dengan akun ini akan keluar, termasuk HP ini."). Dialog ubah kata sandi: kolom saat ini (hanya untuk akun bersandi), baru, dan ulangi; validasi di HP (`PasswordChangeRules`) lalu server; isian bertahan kalau server menolak.
- [x] **Keluar dari semua perangkat**: `SignOutCoordinator.signOutAllDevices` meminta server mencabut semua sesi **lebih dulu**; hanya kalau berhasil HP menandai hapus data, menutup sesi (`SessionEnding.closeSession`, tanpa memanggil `logout` lagi karena tokennya sudah mati), dan memulai ulang proses. Tanpa internet, sesi berakhir, atau ditolak server: tidak ada yang berubah di HP. `AccountManager.signOutAllDevices` yang lama (menutup sesi sendiri) diganti `revokeAllSessions`.
- [x] **UI server dan token dihapus**: Atur server, token, "Uji koneksi" (`SyncManager.testConnection` ikut hilang), `SyncSection`. Bagian "Data dan sinkron" (`DataSyncSection`): sakelar "Sinkron otomatis" (cadangan snapshot, opt-in sejak 31H), "Sinkron sekarang", dan **"Cadangan dan pulihkan"** yang mengunduh cadangan akun dan menampilkan dialog dengan waktu cadangan sebelum mengganti data. Tombol "Sinkron sekarang" di Profil menyinkronkan **habit dan to-do**, yang di Pengaturan mencadangkan **snapshot**; keduanya berbeda dan keterangannya menyebut apa yang dikirim.
- [x] **`TeamScreen`, `TeamViewModel`, `JoinTeamDialog`**: `SignInPrompt` dan cabang belum masuk dihapus (`TeamUiState.account` menjadi `accountId`). Undangan yang datang sebelum masuk tetap disimpan `PendingInviteStore` dan dipakai setelah masuk.
- [x] **`backup_rules.xml` dan `data_extraction_rules.xml`** kini hanya `<exclude>`: database (termasuk `-wal`, `-shm`, `-journal`) dan **semua** berkas di `PrefFile`, bukan hanya `settings.xml`, karena `account.xml` memuat token. `BackupRulesTest` menggagalkan build kalau ada `<include>`, atau berkas baru di `PrefFile` yang tidak dikecualikan.
- [x] **Cabang belum login di snapshot**: `SyncManager.followUpsOnAccount`, `Snapshot.followUpsOmitted`, dan bidang `followUps` dihapus. Snapshot tidak lagi membawa follow-up; snapshot lama yang masih memuatnya tetap terbaca dan bagian itu diabaikan, dan memulihkan snapshot tidak menyentuh follow-up di HP. Pencarian `account == null`, `Masuk dengan Google dulu`, `SignInPrompt`, `Atur server`, dan `Uji koneksi` di `app/src` tidak menemukan apa pun.
- [x] **Tes** (seluruh suite Android: **542 lulus**, dari 524): `BackupRulesTest`, `PasswordChangeRulesTest`, lima tes `signOutAllDevices` di `SignOutCoordinatorTest`, tes `AccountManager` untuk `revokeAllSessions` dan `closeSession`, dan tes snapshot lama yang masih memuat follow-up. Build rilis (`assembleRelease`) lolos.
- [x] **Diuji di emulator** (Pixel 6 API 34, server tiruan PHP lokal, bukan server produksi): tab Setelan dan semua bagiannya tampil; Profil terbuka dari lingkaran inisial dan menampilkan identitas, Data tubuh, "Email dan kata sandi, Google terhubung"; **ubah kata sandi** dengan sandi saat ini salah menampilkan galat server dan isian bertahan, dengan sandi benar mengirim `PUT account/password`, menutup dialog, dan sesi tetap; **Keluar dari semua perangkat** dengan server gagal (500) menampilkan pesan dan **tidak mengubah apa pun** (sesi dan database utuh), dengan server berhasil memanggil `logout-all` sekali (tanpa `logout`), memulai ulang proses (PID berubah), dan mengosongkan database dan berkas akun.
- [x] `docs/design/README.md` (bagian Pengaturan dan Profil) dan [rilis-play-store.md](rilis-play-store.md) diperbarui di commit yang sama dengan catatan ini.

**Belum diuji:** HP fisik; Google (membuat kata sandi untuk akun Google tanpa sandi, dan konfirmasi Hapus akun Google); "Cadangan dan pulihkan" dengan cadangan sungguhan (server tiruan hanya menjawab 404); dialog kalender dengan izin diberikan; gulir dan tampilan di layar kecil dan font besar; tombol "Kebijakan privasi" membuka halaman sungguhan (hanya `PRIVACY_POLICY_URL` yang diperiksa dari kodenya).
**Catatan bagi pemilik:** (1) tombol Hapus akun dan Keluar sekarang di Profil, jadi halaman publik `/hapus-akun` boleh didaftarkan di Play Console setelah 31I diterapkan di rilis; (2) dialog sub-pengaturan memakai dialog Material bawaan, bukan layar penuh, dan belum ada mockup untuknya; (3) dialog Keluar tetap panjang (teksnya menyebut tiap kemungkinan), belum dipadatkan.

**Selesai jika:** tidak ada lagi UI atau kode yang meminta alamat server atau token (✅), dan semua tindakan akun bisa dilakukan dari Profil di HP fisik (✅ di emulator, HP fisik belum).

#### 31J. Skrip cek rilis dan tes pelengkap

Status: **selesai (14 Oktober 2026).** Bergantung pada 31I. Skrip berjalan dan terbukti gagal pada setiap pelanggaran; pada build sekarang ia **gagal di `target-sdk`** (app masih targetSdk 34, syarat berikutnya 35 atau lebih), dan itu memang pekerjaan 31L.

- [x] **`scripts/cek-rilis.ps1`** (kode keluar 1 kalau ada yang gagal, 2 kalau tidak bisa dijalankan) menyiapkan manifest dan BuildConfig rilis lewat Gradle, lalu menjalankan 11 pemeriksaan: `debuggable` (manifest dan `BuildConfig.DEBUG`), `komponen-debug` (tidak ada komponen di paket `.debug.` atau bernama kelas dari `src/debug`, termasuk `DebugSessionReceiver`), `cleartext` (dan tidak ada `networkSecurityConfig`, yang khusus debug), `https` (`API_BASE_URL` rilis), `izin` (**sama persis** dengan `scripts/izin-disetujui.txt`, kelebihan dan kekurangan sama-sama gagal), `target-sdk` (dibanding `scripts/target-sdk-minimum.txt`), `minify` (blok release harus menulis `isMinifyEnabled` sesuai keputusan, mati), `backup` (manifest menunjuk kedua aturan; keduanya tanpa `<include>`, dengan semua bagian yang wajib ada, dan mengecualikan database serta **setiap** berkas di `PrefFile`, dibaca dari `PrefFile.kt`), `client-id` (tidak kosong, berbentuk client ID Google, tidak ada di `scripts/client-id-uji.txt`), `rahasia` (berkas kunci dan keystore, `.env`, `local.properties`, serta isi: kunci privat, rahasia klien Google, kunci API Google dan Resend, token GitHub, `APP_KEY`, sandi keystore; dibaca dari berkas tercatat **dan** yang belum tercatat tapi tidak diabaikan git; isinya tidak pernah dicetak), dan `version-code` (lebih besar dari `scripts/versi-terakhir.txt`). Berkas isian: `izin-disetujui.txt` (diisi dari manifest rilis hari ini, dengan catatan), `target-sdk-minimum.txt` (**diisi pemilik**; sementara 35, angka 2025, mungkin sudah kedaluwarsa), `versi-terakhir.txt` (0), `client-id-uji.txt` (kosong).
- [x] **Perintah tunggal `scripts/cek-semua.ps1`**: `cleanTestDebugUnitTest testDebugUnitTest` (542 tes di 41 kelas, lulus, dijalankan ulang dari nol), lalu `cek-rilis.ps1`. Dicatat di `CLAUDE.md` sebagai langkah wajib sebelum mengunggah AAB. `scripts/_jdk.ps1` memilih JDK 17 kalau `JAVA_HOME` menunjuk versi lain (di mesin ini awalnya JDK 11, dan Gradle gagal). **Jangan mem-pipe keluaran Gradle ke `tail`:** daemon-nya menahan pipa sampai perintah kena batas waktu.
- [x] **Tiap pemeriksaan dilanggar dengan sengaja:** `scripts/uji-cek-rilis.ps1` membuat salinan sementara, memastikan salinan utuh lulus, lalu untuk 19 pelanggaran (debuggable, receiver debug, cleartext, http, izin kelebihan dan kekurangan, targetSdk, minify, `<include>`, `account.xml` tidak dikecualikan, bagian `device-transfer` hilang, manifest tanpa aturan cadangan, client ID kosong dan tercantum uji, `client_secret_*.json`, `.jks`, rahasia klien Google, sandi keystore, `versionCode` sama) menuntut **persis** pemeriksaan yang dimaksud gagal dan tidak ada yang lain. 20 dari 20 kasus sesuai. Satu kali kasus pertama meleset: `client_secret_*.json` tidak terdeteksi karena `.gitignore` sudah mengabaikannya (itu perlindungan yang benar); kasusnya diubah memaksa berkas masuk repo (`git add -f`).
- [x] `.gitignore` kini juga mengabaikan `*.jks`, `*.keystore`, `keystore.properties` (kunci unggah dibuat di 31L, di luar repo).

**Catatan bagi pemilik:** (1) isi `scripts/target-sdk-minimum.txt` dengan syarat Play yang berlaku pada hari rilis; (2) isi `scripts/versi-terakhir.txt` setiap kali unggahan berhasil; (3) pemeriksaan `client-id` hanya tahu "nilai uji" dari `scripts/client-id-uji.txt`, jadi kalau nanti dibuat client ID khusus uji, daftarkan di sana; (4) skrip belum memeriksa AAB itu sendiri (ditandatangani atau tidak) dan membaca manifest hasil gabungan, bukan isi AAB; penandatanganan masuk 31L.

**Selesai jika:** skrip lulus pada build rilis yang benar (✅ di semua pemeriksaan kecuali `target-sdk`, yang menunggu 31L) dan gagal untuk setiap pelanggaran uji (✅).

#### 31K. Operasional server: backup, pemantauan, prosedur insiden

Status: **skrip, perintah, dan dokumen selesai di repo dan teruji dengan tiruan (15 Oktober 2026); BELUM dipasang di VM, jadi belum ada satu pun bukti operasional.** Bisa paralel setelah 31B. **Wajib selesai sebelum penguji diundang di 31M** (penguji adalah pengguna sungguhan dengan data sungguhan). Panduan pemasangan, pemulihan, dan keputusan: [operasional-server.md](operasional-server.md); prosedur kebocoran: [insiden-data.md](insiden-data.md). Sisa yang butuh pemilik: tujuan backup di luar VM, akun ntfy dan Healthchecks, kata sandi backup beserta salinannya, dan persetujuan dua keputusan tertulis.

- [~] **Backup MySQL harian** (tujuan **Cloudflare R2**, diputuskan 11 Oktober 2026; bucket dan token R2 belum dibuat) (kode selesai: `server/ops/backup.sh`; belum dipasang di VM, belum pernah jalan dengan MySQL dan rclone sungguhan) (`mysqldump --single-transaction`), dikompres, **terenkripsi**, dikirim ke lokasi **di luar VM**, ditambah berkas snapshot di storage. Rotasi 30 hari. Sinyal "backup berhasil" ke layanan
  pemantau supaya kegagalan diam-diam ketahuan.
- [~] **Latihan pemulihan** (kode selesai: `server/ops/restore-drill.sh`, memakai container MySQL sementara berbatas memori 450 MB karena VM hanya 2 GB; **belum pernah dijalankan sungguhan, dan itulah buktinya yang dibutuhkan**): skrip yang memulihkan backup terbaru ke database kosong lalu memeriksa jumlah baris. Dijalankan sekali sebelum 31M, dan seterusnya sebulan sekali.
- [~] **Pemantauan** (kode selesai: `server/ops/monitor.sh`; peringatan 80% email sudah ada di aplikasi sejak 31B; belum dipasang, peringatan uji belum sampai ke ponsel): pengecekan situs dari luar (uptime), peringatan disk hampir penuh, backup gagal, dan email terkirim mencapai 80% batas harian. Pemberitahuan sampai ke ponsel pemilik.
- [~] **Rahasia** (daftar, tempat, dan cara memutar kunci ada di operasional-server.md bagian 4 dan 5; perintah `habitflow:reencrypt-snapshots` selesai dan teruji; **salinan belum dibuat**): `.env.production` dan kunci app yang mengenkripsi snapshot disalin ke tempat aman terpisah (kunci hilang berarti semua snapshot tidak terbaca). Catat cara memutar kunci tanpa kehilangan
  snapshot (perintah enkripsi ulang).
- [~] Putusan tertulis soal **enkripsi disk VM** (ditulis sebagai usulan di operasional-server.md bagian 6, **menunggu persetujuan pemilik**; ditinjau ulang saat tahap 30 membuat tabel kesehatan) (data kesehatan di tabel MySQL berupa teks biasa) dan pembatasan akses admin database (tidak terbuka ke internet).
- [~] Jadwal pembaruan keamanan PHP, Laravel, dan image Docker (usulan di operasional-server.md bagian 7, menunggu persetujuan pemilik).
- [~] **`docs/insiden-data.md`** (ditulis, **perintah `habitflow:revoke-all-sessions` kini ada dan teruji**; menunggu verifikasi hukum Pasal 46 dan lembaga penerima laporan, dan latihan di atas kertas): prosedur kebocoran data. Siapa menilai, cara menahan (`habitflow:revoke-all-sessions`, memutar kunci), cara memastikan cakupan, dan templat pemberitahuan ke pengguna dan lembaga
  pengawas. UU PDP mewajibkan pemberitahuan paling lambat 3x24 jam, jadi prosedurnya ditulis **sebelum** dibutuhkan.

**Selesai jika:** backup dipulihkan dengan sukses di latihan, peringatan uji sampai ke ponsel pemilik, dan prosedur insiden bisa dijalankan tanpa membaca kode.

#### 31L. Rilis teknis: targetSdk, izin, penandatanganan, Google Sign-In produksi

Status: **sedang dikerjakan (15 Oktober 2026).** Bergantung pada 31I. Sudah di kode dan tes: `targetSdk`/`compileSdk` 36, jalan cadangan izin alarm, Health Connect stabil, akun peninjau, hapus `api_tokens`. Belum: penandatanganan, Google Sign-In produksi, jalan cadangan `USE_FULL_SCREEN_INTENT`, dan semua uji di HP fisik.

- [~] **Naikkan `targetSdk` dan `compileSdk`** ke angka syarat Play saat itu. **Sebagian selesai (15 Oktober 2026):** `compileSdk` 36 (commit `5a5e314`), `targetSdk` 36 dan `scripts/target-sdk-minimum.txt` 36 (syarat Play sejak 31 Agustus 2026, dicek pemilik 14 Oktober). 542 tes unit lulus dan `cek-rilis.ps1` lulus 11 dari 11. **Belum:** penelusuran perubahan perilaku API 35 dan 36 (edge-to-edge, dan seterusnya) dan uji di HP fisik. Perubahan perilaku yang perlu ditelusuri: (edge-to-edge, tipe layanan foreground, izin alarm, notifikasi,
  dukungan ukuran halaman memori 16 KB kalau berlaku untuk pustaka yang dipakai) dan uji ulang di HP fisik: alarm Subuh, adzan, notifikasi tetap, pembacaan langkah, kalender, Health Connect.
- [~] **Izin yang dibatasi:** siapkan jalan tanpa izin untuk `USE_EXACT_ALARM` (misalnya izin yang diberikan pengguna) dan `USE_FULL_SCREEN_INTENT` (notifikasi heads-up) sebelum rilis, supaya penolakan Google tidak
  mematikan fitur. Isi alasan deklarasi dengan jujur.
  - [x] **`USE_EXACT_ALARM` (kode, 15 Oktober 2026):** `SCHEDULE_EXACT_ALARM` kini ada di manifest tanpa `maxSdkVersion` sebagai cadangan. `ExactAlarmAccess` memeriksa `canScheduleExactAlarms()`; `ScheduleNotifier.setClockAlarm` memakai `setAlarmClock` kalau diizinkan dan `setAndAllowWhileIdle` kalau tidak. Layar Pengaturan menampilkan peringatan dan tombol "Izinkan alarm tepat waktu" (dicek ulang saat kembali dari layar sistem), dan receiver jadwal mendengarkan `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` untuk memasang ulang alarm. **Belum diuji di perangkat/emulator** (belum ada tes unit untuk jalur ini; sulit karena bergantung pada `AlarmManager`). Kalau Google menolak `USE_EXACT_ALARM`, izin itu dicabut dari manifest **dan** `scripts/izin-disetujui.txt` diubah bersamanya.
  - [x] **Keputusan pemilik 15 Oktober 2026: build uji dan rilis pertama hanya mendeklarasikan `SCHEDULE_EXACT_ALARM`; `USE_EXACT_ALARM` dan `USE_FULL_SCREEN_INTENT` dihapus dari manifest, `scripts/izin-disetujui.txt`, dan inventaris** (`cek-rilis` lulus 11 dari 11). Akibat yang harus diketahui: alarm presisi kini harus diberikan pengguna (Android 14 ke atas menolaknya bawaan), jadi **alarm Subuh pada instalasi baru terlambat sampai satu jam sampai izin diberikan**; Pengaturan menawarkannya, dan sejak 15 Oktober 2026 ada **kartu ajakan di layar Hari ini** (`ExactAlarmPrompt`): tampil kalau ada alarm yang akan berbunyi dan izinnya belum ada, dengan "Izinkan" (membuka layar izin sistem) dan "Nanti" (disembunyikan 3 hari, tersimpan di `AlarmSettings` sehingga ikut terhapus saat Keluar). Alarm Subuh sudah aktif sejak instalasi pertama (blok "Bangun" di-seed sebagai ALARM), jadi ajakannya muncul begitu pengguna masuk. Memakai `AppCard` yang ada, tanpa nilai desain baru; 5 tes unit untuk aturan tampilnya (551 tes, `cek-rilis` 11 dari 11 lulus), **belum dilihat di perangkat**. Peringatan di Pengaturan juga tidak lagi berwarna `error` (warna itu hanya untuk galat sistem menurut `docs/design/README.md`). Tombol "Izinkan layar alarm penuh" disembunyikan karena izinnya tidak lagi dideklarasikan.
  - [x] **`USE_FULL_SCREEN_INTENT` (kode, 15 Oktober 2026):** `FullScreenAlarmAccess` menentukan apakah layar alarm penuh boleh tampil (Android 14 ke atas: `NotificationManager.canUseFullScreenIntent()`; di bawahnya: izin ada di manifest). `AlarmService` hanya memasang `setFullScreenIntent` kalau diizinkan; tanpanya alarm tetap berbunyi penuh (suara, getar, layanan foreground tidak bergantung pada izin ini) dan muncul sebagai notifikasi heads-up berkategori alarm, tampil publik di layar kunci, dengan tombol Matikan dan Tunda, dan mengetuknya membuka layar alarm. Pengaturan menawarkan "Izinkan layar alarm penuh" (hanya di Android 14 ke atas, nada informatif, bukan peringatan). 3 tes unit untuk logika penentunya; 545 tes dan `cek-rilis` 11 dari 11 lulus. **Belum diuji di perangkat** (layar kunci, heads-up, dan tombol di notifikasi). Izinnya sudah dihapus dari manifest (lihat keputusan di atas), jadi jalan cadangan ini kini jalur satu-satunya.
- [ ] **Penandatanganan:** buat kunci unggah, **dua salinan di tempat berbeda** beserta kata sandinya, aktifkan Play App Signing, `signingConfigs` **sudah ada** di `app/build.gradle.kts` dan membaca `~/.habitflow/keystore.properties` (di luar repo, berkas itu belum ada di mesin ini), jadi pekerjaannya membuat kuncinya dan menyimpan salinannya.
- [x] **R8 (`isMinifyEnabled`) tetap mati untuk rilis pertama** (diputuskan pemilik 11 Oktober 2026). Tidak ada yang diubah di build. Kunci: nilai ini tidak diubah sebelum atau selama uji tertutup, supaya biner yang diuji 14 hari sama dengan yang dirilis. Menyalakannya menjadi tahap tersendiri sesudah app tayang dan stabil, lewat rilis bertahap, dengan aturan keep, `shrinkResources`, penyimpanan berkas mapping, dan uji penuh pada build rilis. Skrip cek rilis (31J) memastikan nilainya sesuai keputusan.
- [~] **Periksa pustaka versi alpha:** sudah pindah ke `androidx.health.connect:connect-client:1.1.0` stabil (commit `5a5e314`, 14 Oktober 2026). **Belum:** uji ulang pembacaan langkah di HP fisik setelah pindah versi.
- [ ] **Google Sign-In produksi:** layar persetujuan OAuth dipindah dari uji ke **produksi** (nama app, logo, tautan kebijakan privasi, domain resmi); client ID Android memakai **SHA-1 dari kunci Play App Signing**
  selain kunci unggah dan debug; `GOOGLE_ANDROID_CLIENT_ID` di server diperbarui. Diuji dari build yang **dipasang lewat jalur uji Play Store**, bukan adb.
- [~] **Akun peninjau:** perintah artisan `habitflow:make-reviewer-account` membuat akun permanen yang sudah terverifikasi dengan data contoh, untuk kolom App access di Play Console. **Perintah dan 4 tesnya selesai (15 Oktober 2026).** Belum dijalankan di produksi: akunnya dibuat saat mengisi App access di 31M, dengan email yang sungguh dikelola pemilik.
- [x] **Hapus tabel `api_tokens` (kode dan tes, 15 Oktober 2026; belum di-deploy):** migrasi `2026_10_14_100000_drop_api_tokens` menghapus tabel dan `snapshots.api_token_id`, dan **menolak jalan kalau masih ada snapshot tanpa akun** (3 tes). `habitflow:claim-legacy`, model `ApiToken`, dan tes klaimnya dihapus. **Urutan deploy:** klaim dulu dari rilis `b96bc51` atau sebelumnya, baru deploy rilis ini (lihat `server/DEPLOY.md`). Inventaris data (`DataInventory`, `rilis-play-store.md`) sudah diperbarui.
- [x] **Temuan saat memeriksa 31L: pembatas laju per token tidak pernah jalan sejak 31E.** `AppServiceProvider` membaca atribut `api_token` yang sudah tidak diisi siapa pun, dan sesudahnya (diperbaiki ke `user_token`) tetap tidak jalan karena Laravel menjalankan `throttle` sebelum middleware `user-token`. Akibatnya semua permintaan dibatasi per alamat IP, jadi dua akun di jaringan yang sama (pemilik dan istri) berbagi satu jatah 60 per menit. Kini kuncinya hash token dari header `Authorization`; tes baru membuktikan akun lain dari IP yang sama tidak ikut terblokir (dan gagal tanpa perbaikan).

**Selesai jika:** AAB rilis ditandatangani dan lulus 31J, login Google dan email berhasil dari build jalur uji Play Store, dan semua fitur latar belakang diuji di HP fisik pada `targetSdk` baru.

#### 31M. Uji tertutup 14 hari, Play Console, dan produksi

Status: **belum bisa dimulai; bahan persiapannya siap (15 Oktober 2026).** Bergantung pada 31C, 31D, 31J, 31K, 31L. Penahan jalur kritis (hitungan 14 hari tidak bisa jalan sebelum ini): kunci unggah dan AAB ditandatangani (31L), server 31B-31E belum di-deploy, email sungguhan belum (DNS dan Resend), Google Sign-In produksi belum, backup 31K belum dipasang, kontak privasi kosong, dan belum ada 15-25 penguji. Detail, siapa yang mengerjakan, draf formulir Play Console, pesan undangan, skrip uji akhir, dan tabel pantau: [uji-tertutup-dan-play-console.md](uji-tertutup-dan-play-console.md). Tiga keputusan pemilik yang harus diambil **sebelum build uji pertama** (karena biner uji harus sama dengan biner rilis): `USE_EXACT_ALARM` atau `SCHEDULE_EXACT_ALARM` saja, `USE_FULL_SCREEN_INTENT`, dan target usia (bagian 3 dokumen itu).

- [ ] **Undang penguji:** 15 sampai 25 orang nyata (minimal 12 harus tetap bergabung setiap hari selama 14 hari **berturut-turut**; kalau turun di bawah 12 hitungan mengulang dari nol, dan Google memeriksa bahwa
  penguji benar-benar memakai app). Beri petunjuk pemasangan dan cara melapor.
- [ ] **Formulir Play Console** (sebagian besar bisa diisi selama 14 hari menunggu): Data safety dari inventaris, Health apps declaration, deklarasi Health Connect, deklarasi izin sensitif, URL kebijakan privasi, URL hapus akun,
  App access (akun peninjau), peringkat konten, target audiens (bukan anak-anak), kategori, ikon, tangkapan layar, dan deskripsi tanpa klaim medis.
- [ ] Pantau selama uji: jumlah penguji aktif harian, laporan crash (Android vitals), pendaftaran dan verifikasi yang gagal, email terkirim terhadap batas harian, dan keberhasilan backup. Perbaiki bug lewat build uji baru tanpa
  mengganti penguji.
- [ ] **Gerbang H** di [rilis-play-store.md](rilis-play-store.md): pasang dari jalur uji di HP yang bukan HP pengembangan lalu daftar, verifikasi email, pakai tanpa internet, sinkron, Keluar, login akun lain, hapus akun.
- [ ] Ajukan produksi **hanya setelah semua gerbang A sampai H berstatus `[x]` dengan bukti.** Rilis bertahap (usulan 10%, 50%, 100%) dengan pemantauan di antara setiap tahap.
- [ ] Pasca rilis: tanggapi permintaan hapus akun (otomatis lewat halaman), dan jadwalkan latihan pemulihan bulanan.

**Selesai jika:** app tayang di Play Store dengan semua gerbang lulus dan terbukti, dan tidak ada item yang bergantung pada ingatan pemilik.

#### Selesai jika (seluruh tahap 31)

- Pada pemasangan baru, tidak ada layar app yang bisa dibuka sebelum login, dan tidak ada alarm, notifikasi, atau worker yang jalan.
- Login email, daftar email dengan verifikasi, dan Google semuanya berhasil, dan akun yang sama lewat Google dan email tidak menjadi dua akun.
- Setelah login, app tetap bisa dipakai tanpa internet, dan perubahan terkirim saat online lagi.
- Keluar menghapus semua data lokal, dan login akun lain di HP yang sama tidak menampilkan data akun sebelumnya.
- Data HP pemilik yang sudah ada sebelum tahap ini terambil alih ke akun pemilik tanpa kehilangan, dan snapshot lama bisa dipulihkan.
- Tidak ada lagi UI atau kode yang meminta alamat server atau token di app.
- Akun bisa dihapus dari app dan dari web, dan semua data di server (baris dan berkas) ikut terhapus, dibuktikan tes.
- Semua penjaga otomatis di [rilis-play-store.md](rilis-play-store.md) bagian 3 ada dan lulus, dan skrip cek rilis lulus pada build yang diunggah.
- Backup server terbukti bisa dipulihkan.
- Semua gerbang rilis A sampai H berstatus selesai dengan bukti.

#### Edge case baru yang harus diuji

- Login di HP yang masih memegang data akun lain (misalnya dari cadangan Android atau pembaruan dari versi lama).
- Token dicabut dari web atau perangkat lain saat app sedang dipakai. Perubahan lokal yang belum terkirim tidak boleh hilang diam-diam, dan akun yang sama yang login lagi melanjutkan tanpa kehilangan.
- Hari pertama tanpa internet: layar Masuk menjelaskan bahwa login butuh internet.
- Email verifikasi terlambat sampai atau kode kedaluwarsa sebelum diketik: kirim ulang jalan, kode lama tidak berlaku.
- Pengguna menutup app di tengah verifikasi lalu membukanya lagi: kembali ke layar Verifikasi, bukan ke layar Daftar kosong.
- Jam HP salah: kedaluwarsa kode dihitung server, bukan HP.
- Link undangan tim dibuka saat belum login, lalu login dengan akun yang berbeda dari yang diundang.
- Keluar saat ada perubahan belum terkirim, dengan dan tanpa internet.
- Pengguna yang daftar lewat Google lalu mencoba masuk dengan email dan kata sandi (akun belum punya kata sandi): diarahkan ke "Buat kata sandi" lewat lupa kata sandi.
- Seseorang mendaftar dengan email korban lalu korban login Google: akun diambil alih Google, kata sandi penyerang hilang.
- Memulihkan snapshot lama pada akun yang sudah punya data di server (tahap 27 sudah punya batasan "dobel").
- Alarm Subuh yang sudah terjadwal saat sesi berakhir: harus dibatalkan, tidak berbunyi sendiri.
- HP restart (boot) saat belum ada sesi: tidak ada alarm yang dijadwalkan.
- Pemilik tim menghapus akun saat ada anggota lain, dan saat tim berisi hanya dia.
- Dua perangkat memakai akun yang sama, satu menekan "Keluar dari semua perangkat".
- Batas harian email layanan terlampaui: pendaftaran gagal dengan pesan yang jelas, dan pemilik sudah diberi tahu sejak 80%.
- Layanan email mati: pendaftaran tidak macet (antrean) dan pengguna melihat pesan tenang, bukan galat teknis.

#### Keputusan enam butir terbuka (diputuskan pemilik 11 Oktober 2026)

Butir-butir ini sebelumnya ditandai untuk dibahas 12 Oktober 2026, tapi pemilik memutuskan lebih cepat, 11 Oktober 2026:

| Butir | Tertahan | Keputusan |
|---|---|---|
| Lokasi backup di luar VM dan pemegang kunci enkripsinya | 31K (dan karena itu 31M) | **Cloudflare R2** untuk lokasi, kunci dipegang **Roziq sendiri**. Lihat rincian di bawah |
| Kunci app (sidik jari atau PIN) | Tidak menahan tahap 31 | **Disetujui** — dikerjakan sebagai tahap sendiri sesudah tahap 31 |
| Web memakai kode atau tautan | 31C | **Disetujui: kode**, sama dengan app |
| Tema tampilan setelah Keluar | 31H | **Disetujui: dipertahankan** (ikut preferensi perangkat) |
| Kapan tabel `api_tokens` dihapus | 31L | **Disetujui**: di 31L, setelah semua berjalan |
| Urutan terhadap tahap 29, 30 (langkah 3-6), 32, dan 33 | Sebelum 31B dimulai | **Disetujui: kerjakan tahap 31 dulu** sampai selesai, baru lanjut ke 29 langkah 4, 30 langkah 3-6, 32, dan 33 |

Rincian tiap butir:

- **Kunci app (sidik jari atau PIN)** sebagai perlindungan data lokal yang sebenarnya, seperti yang diusulkan Claude di riwayat nomor 6. **Disetujui pemilik 11 Oktober 2026**: dikerjakan sebagai tahap sendiri sesudah tahap 31 selesai.
- **Lokasi backup di luar VM dan pemegang kunci enkripsinya.** Konteks: Gerbang D di `rilis-play-store.md` mewajibkan "backup MySQL otomatis harian ke lokasi di luar VM, terenkripsi, dengan rotasi 30 hari" **sebelum penguji sungguhan diundang** (31M) — karena semua pengguna sekarang bergantung pada satu VM Hyper-V di rumah; kalau VM itu rusak/kena ransomware/salah hapus, semua data pengguna hilang permanen tanpa cara pulih.
  **Diputuskan pemilik 11 Oktober 2026:**
  - Lokasi: **Cloudflare R2** (satu ekosistem dengan Cloudflare Tunnel yang sudah dipakai, S3-compatible, tier gratis 10GB tanpa biaya egress — penting untuk latihan pemulihan berkala).
  - Enkripsi: dump MySQL dienkripsi **di sisi klien sebelum diunggah** (`age` atau GPG simetris), supaya kredensial R2 yang bocor tidak otomatis membuka isi backup.
  - Pemegang kunci: **Roziq sendiri**, disimpan di password manager, **terpisah dari VM dan dari repo git**, dengan satu salinan cadangan offline/fisik. Kunci ini **beda** dari kunci app yang sudah mengenkripsi snapshot sejak tahap 19B (lihat inventaris data di `rilis-play-store.md` bagian 1) — kunci ini khusus untuk backup MySQL.
  - Rotasi: tetap 30 hari seperti usulan awal di `rilis-play-store.md`.
  - Catatan: Gerbang D juga mewajibkan **latihan pemulihan** (restore ke database kosong dan diperiksa) — backup yang belum pernah dicoba dipulihkan dianggap belum ada. Ini dikerjakan sebagai bagian dari 31K, sesudah backup otomatis jalan.
- **Web memakai kode atau tautan** untuk verifikasi dan reset kata sandi. **Disetujui pemilik 11 Oktober 2026: kode**, sama dengan app. Subdomain pengirim sudah diputuskan: `mail.roziqrizal.com`.
- **Tema tampilan setelah Keluar.** **Disetujui pemilik 11 Oktober 2026**: dipertahankan karena preferensi perangkat.
- **Kapan tabel `api_tokens` dihapus.** **Disetujui pemilik 11 Oktober 2026**: di 31L, setelah semua berjalan.
- **Urutan terhadap tahap 29, 30 (langkah 3-6), 32, dan 33.** **Diputuskan pemilik 11 Oktober 2026: kerjakan tahap 31 sampai selesai dulu**, baru lanjut ke tahap-tahap lain yang sudah antre (termasuk tahap 33 yang dicatat hari yang sama).

### Tahap 32: Rekap kerja otomatis dari Claude (diputuskan garis besar 11 Oktober 2026; langkah 1 selesai 15 Oktober 2026)

**Status (15 Oktober 2026):** langkah 1 (server) selesai: migrasi `2026_10_15_100000_create_daily_briefs_table`
(`user_id`, `date`, `email_digest`, `meeting_note`, unik per `user_id`+`date`), model `DailyBrief`, `DailyBriefController`,
rute `GET`/`POST /api/v1/daily-briefs` (`user-token`), 7 tes baru (`DailyBriefApiTest`), 229 tes server lulus. Kontrak:
`POST` adalah upsert per tanggal (201 saat baris baru, 200 saat menimpa); kolom yang **tidak dikirim tidak disentuh**
dan yang dikirim `null` dikosongkan, jadi skill yang hanya mengirim `email_digest` tidak menghapus `meeting_note`.
Batas 20.000 karakter per kolom. `GET` terbaru dulu, maksimal 31 baris, filter `date` dan `from`. Tanpa 409 (tidak ada
penyunting manual). Tabel sudah masuk `DataInventory`, `AccountDeletionService` (+ tesnya), dan inventaris
`rilis-play-store.md`. **Belum di-deploy.** Berikutnya: langkah 2 (tes manual lewat curl setelah deploy), langkah 4
(kartu Android + web).

**Langkah 3 selesai (15 Oktober 2026):** `skills/habitflow-morning-routine/SKILL.md` ditulis. **Belum pernah dijalankan
terhadap server sungguhan** (server belum di-deploy, jadi langkah 2 belum ada); yang sudah cocok dengan kode adalah
kontrak `POST /api/v1/daily-briefs` dan `GET /api/v1/ping`. Keputusan di dalamnya:
- **Lokasi:** `skills/habitflow-morning-routine/` di repo Habitflow (bukan `.claude/skills/`, supaya tidak otomatis aktif
  di setiap sesi kerja repo ini); dipasang dengan menyalin foldernya ke `~/.claude/skills/`. Nama tetap `habitflow-morning-routine`.
- **Token:** dari `HABITFLOW_TOKEN` atau `~/.habitflow/claude-token`, dibuat sekali oleh pemilik lewat `POST /api/v1/auth/login`
  dengan `device_name` `claude-morning-routine`. Skill tidak pernah memegang kata sandi. Token itu setara akses penuh ke data
  akun, dan mati kalau "Keluar dari semua perangkat" atau ganti kata sandi, jadi skill berhenti dengan pesan jelas saat 401.
  Alternatif yang lebih sempit (token khusus tulis ringkasan saja) belum ada; layak dipertimbangkan sebelum dijadwalkan di awan.
- **Hanya `email_digest` yang dikirim**, `meeting_note` tidak (butir "Meeting & briefing" di bawah tetap belum diputuskan).
- **Gmail tidak terhubung atau token tidak ada: tidak menulis apa pun**, supaya ringkasan benar sebelumnya tidak tertimpa "kosong".
- Kode verifikasi, tautan reset, dan data kartu/rekening dari email tidak disalin ke ringkasan, karena disimpan di server dan terbaca di web.
- Tanpa email belum dibaca tetap menulis "0 email belum dibaca" supaya kartu membedakan "dicek, kosong" dari "belum dicek".

**Langkah 4, bagian web selesai (15 Oktober 2026; Android belum):** komponen Livewire `DailyBriefCard`
(`resources/views/livewire/daily-brief-card.blade.php`) dipasang di atas halaman `/follow-up`. Read-only, berlabel
"Ditulis otomatis, hanya dibaca", dan tidak punya form, jadi jelas beda dari Catatan EOD yang ditulis manual. Perilaku:
- Menampilkan `email_digest` hari ini (dengan "Hari ini · diperbarui HH.MM"). "Hari ini" dihitung di zona waktu terakhir
  yang dikirim HP (`prayer_days`, sama dengan kartu jadwal), cadangan Asia/Jakarta, bukan zona server.
- Belum ada ringkasan hari ini: menampilkan yang terakhir beserta tanggalnya dan tulisan "Belum ada ringkasan hari ini",
  supaya kartu tidak kosong tapi juga tidak berpura-pura itu ringkasan hari ini. Ringkasan bertanggal di depan diabaikan.
- Belum pernah ada: pesan kosong yang menyebut skill `habitflow-morning-routine`.
- Teks di-escape dan ditampilkan polos (`white-space: pre-line`), jadi isi email tidak bisa menyisipkan HTML. Segar tiap 5 menit.
- Hanya memakai token warna dan gaya kartu yang sudah ada di halaman Kerja; tidak ada nilai desain baru.
- 8 tes (`DailyBriefCardTest`); 237 tes server lulus. **Belum dilihat di browser** dan **belum di-deploy.**

**Penyimpangan dari mockup yang perlu diketahui:** artboard `KerjaWeb.dc.html` ternyata **tidak punya** kartu "Ringkasan
dari Claude". Yang ada hanya hero "Ringkasan hari ini" (statistik follow-up), panel Meeting & briefing, Rencana besok, dan
Catatan EOD. Kartu ini dirancang mengikuti keputusan garis besar di atas (kartu terpisah di tab Kerja) dan gaya halaman
yang ada, bukan menyalin artboard. Kalau mau ditaruh di tempat lain atau dibuatkan artboard dulu, tinggal dipindah.
**Langkah 4, bagian Android selesai di kode (15 Oktober 2026; belum dilihat di HP):** kartu "Ringkasan dari Claude" (`DailyBriefCardView`)
di paling atas tab Kerja, di bawah judul. Read-only, berlabel "Ditulis otomatis, hanya dibaca", tanpa tombol ubah.
- **Alur data:** `HabitSyncClient.listDailyBriefs()` (`GET /api/v1/daily-briefs`) → `DailyBriefRepository.refresh()` → salinan satu
  ringkasan di `DailyBriefStore`. Ditarik tiap tab Kerja dibuka; gagal (tanpa internet, 5xx, 401) **diam** dan kartu memakai salinan
  terakhir, jadi tidak berkedip kosong saat sinyal jelek. Server tanpa ringkasan berisi mengosongkan salinan.
- **Tanpa tabel Room baru** (database tetap versi 10, 18 entitas): server sumber kebenaran, HP hanya cache. Disimpan di berkas pengaturan
  baru `daily_brief.xml` (`PrefFile.DAILY_BRIEF`), jadi **otomatis terhapus saat Keluar** (`PrefWiper`) dan dikecualikan dari Auto
  Backup dan pemindahan perangkat (`backup_rules.xml`, `data_extraction_rules.xml`, dijaga `BackupRulesTest`). Inventaris Android di
  `rilis-play-store.md` sudah memuatnya.
- **Aturan pilihan sama dengan web** (`pickDailyBrief`): hari ini kalau ada; kalau belum, yang terakhir dengan tulisan "Belum ada ringkasan
  hari ini. Terakhir: <tanggal>"; tanggal di depan dan isi kosong diabaikan. "Hari ini" mengikuti `DayClock` (tanggal perangkat), jadi
  ikut berganti saat tengah malam tanpa menunggu tarikan baru. Jam "diperbarui" memakai zona perangkat.
- Teks polos; lebih dari 6 baris dilipat dengan tombol "Selengkapnya". Hanya memakai `AppCard` dan gaya teks yang sudah ada.
- 22 tes baru (551 menjadi 573 tes unit lulus): pemilihan ringkasan, cache (termasuk tanggal rusak), repository (gagal tidak menimpa
  salinan, 401 tidak menghapus), klien (baris rusak atau tanpa isi dilewati), keterangan kartu.
- **Belum:** dilihat di emulator/HP (tata letak, lipatan "Selengkapnya", tema gelap) dan diuji terhadap server sungguhan (belum di-deploy).
  Yang berubah di `WorkViewModel`: konstruktornya kini menerima `DailyBriefRepository` (satu-satunya pemanggil: `MainActivity`).

**Langkah 2 sebagian selesai (15 Oktober 2026): dites dengan curl terhadap server LOKAL, bukan produksi.** `php artisan serve` dengan
database SQLite sementara (seluruh migrasi jalan, termasuk `daily_briefs`) dan satu akun uji. Hasil: `POST` pertama 201; kirim ulang
200 dengan `id` yang sama (tidak dobel); `POST` hanya `meeting_note` 200 dan `email_digest` tetap; tanggal lain 201 (id baru); `GET` terbaru
dulu dan filter `date` benar; `date` salah format 422; tanpa token dan token salah 401. Isi dengan baris baru, tanda kutip, tanda
pisah "—", dan `<script>` tersimpan dan kembali utuh (di-escape saat tampil, lihat tes web). Tanggal di JSON berupa `2026-10-15`
polos, cocok dengan pembaca Android. Pesan validasi 422 berbahasa Inggris (bawaan Laravel); skill hanya meneruskannya. Server dan
berkas uji sudah dihapus. **Belum:** dites terhadap produksi, dan skill `habitflow-morning-routine` belum pernah dijalankan end-to-end.

**Deploy ke produksi belum dilakukan (15 Oktober 2026).** Alasannya bukan teknis fitur ini, tapi isi deploy: produksi masih menjalankan
kode sebelum 31B, jadi satu deploy membawa seluruh 31B-31L + tahap 32, dengan akibat yang sudah dicatat di 31E: app Android terpasang
(HP pemilik dan istri) berhenti sinkron sampai app baru terpasang, migrasi `drop_api_tokens` mengharuskan `habitflow:claim-legacy`
dijalankan dulu dari rilis lama (`b96bc51` atau sebelumnya, jadi dua kali deploy), dan email verifikasi belum bisa terkirim (DNS Resend
belum). Sesi ini juga tidak punya jalan ke VM (IP tercatat 172.23.192.247 tidak menjawab; perlu IP terbaru dari Hyper-V).

Dengan ini seluruh langkah 4 selesai di kode (web dan Android). Sisa tahap 32: langkah 2 (tes manual dengan curl, menunggu deploy) dan
langkah 5 (opsional, `/schedule`), serta dua butir "Belum diputuskan" di bawah.

Dibahas 11 Oktober 2026, lanjutan tahap 19 dan 27. Sumbernya: skill `morning-routine` yang dipakai pemilik tiap pagi
di `C:\Project\MasuyaLab\masuya-context` untuk bikin jurnal harian di Notion ("Ruang Kerja : I am a Leader") —
ringkasan email unread, migrasi task belum selesai, dan rencana besok. Pertanyaannya: bisa dibuat versi yang sama
tapi nulis ke Habitflow, bukan Notion.

Mockup web dan mobile (artboard "Mobile · Kerja (rekap harian)" dan "Web · Kerja (rekap harian)"):
https://claude.ai/artifact/TWJ8Uus2hKtQQa81wKujG5

**Perbandingan dengan jurnal Notion** (enam section di halaman "Ruang Kerja : I am a Leader" Category = Daily):

| Section Notion | Status di Habitflow |
|---|---|
| Task Hari Ini | Tertutup — follow-up (tahap 19) |
| Rencana Besok | Tersirat — daily scrum besok menarik follow-up yang belum selesai |
| Catatan / Log | Tersirat — field `eodNote` bebas teks |
| Meeting / Briefing | Bolong |
| Email Belum Dibaca | Bolong — hanya bisa dibuat Claude dari Gmail, bukan dari dalam app |
| Ringkasan Percakapan Claude | Bolong — sama, kerjaan Claude di sesi desktop |

**Beda mendasar dengan model Notion:** Notion membuat halaman baru tiap hari dan memigrasi task yang belum selesai
secara manual (bagian paling rumit di `morning-routine`, bagian C). Follow-up Habitflow **tidak butuh migrasi** —
status (Inbox/Aktif/Menunggu) menempel di item yang sama sampai ditandai Selesai, otomatis terbawa ke hari
berikutnya tanpa disalin ulang. Jadi skill versi Habitflow seharusnya lebih ramping dari `morning-routine`.

**Keputusan garis besar (11 Oktober 2026):**

| Pertanyaan | Keputusan |
|---|---|
| Tempat simpan | Tabel baru `daily_briefs` (`date`, `email_digest`, `meeting_note`), **bukan** field di `work_days`/snapshot 19B — alasan sama seperti follow-up dipisah di tahap 27: field yang ikut snapshot bisa ditimpa Android dengan data lama saat sinkron |
| Endpoint | `POST`/`GET /api/v1/daily-briefs`, upsert per tanggal, pola sama seperti `FollowUpController` tahap 27 |
| Autentikasi | `user-token` yang sama dengan `/api/v1/follow-ups`, tidak perlu mekanisme baru |
| Skill baru | `habitflow-morning-routine`, ditaruh di repo Habitflow sendiri (bukan `masuya-context`). Isinya cuma: cek email unread → ringkas → kirim ke endpoint baru. Tanpa langkah migrasi task (tidak perlu, lihat di atas) dan tanpa dependensi Notion |
| Tampilan | Kartu baru "Ringkasan dari Claude" di tab Kerja (Android) dan halaman web Kerja — read-only, dipisah jelas dari Catatan EOD (ditulis manual) supaya sumbernya tidak campur |

**Urutan kerja yang disarankan (belum dikerjakan):**

1. Server: migrasi `daily_briefs`, model, controller, route, tes Laravel.
2. Tes manual endpoint lewat curl/Postman dengan token yang sudah ada.
3. Tulis `SKILL.md` untuk `habitflow-morning-routine`.
4. Android + web: kartu "Ringkasan dari Claude" di tab/halaman Kerja, mengikuti mockup di atas.
5. Opsional: pasangkan ke `/schedule` supaya jalan otomatis tiap pagi, seperti `morning-routine` yang lama.

**Selesai jika:**
- Endpoint `daily-briefs` menerima dan menyimpan ringkasan per tanggal, aman dipanggil ulang di hari yang sama
  (upsert, tidak membuat baris dobel).
- Skill `habitflow-morning-routine` berhasil menulis ringkasan email hari itu ke Habitflow tanpa menyentuh Notion.
- Kartu "Ringkasan dari Claude" tampil di Android dan web, jelas beda dari Catatan EOD yang ditulis manual.
- Tidak ada langkah migrasi task yang ditiru dari `morning-routine` — follow-up tetap mengandalkan status, bukan
  disalin ulang tiap hari.

**Belum diputuskan:**
- Apakah "Meeting & briefing" (section terpisah di mockup) juga ditulis lewat skill ini, atau tetap manual dari
  app/web — mockup sudah menyediakan tempatnya, tapi isinya saat ini masih contoh.
- ~~Nama dan lokasi pasti skill~~ **Diputuskan Claude saat langkah 3 (15 Oktober 2026), boleh diubah pemilik:** `habitflow-morning-routine`, di `skills/` repo ini.
- Hubungan dengan tahap 31 (Login Wajib): `user-token` sudah ada sejak tahap 27 jadi kemungkinan tidak
  terpengaruh, tapi perlu dicek ulang setelah tahap 31 selesai.

### Tahap 33: Web disamakan dengan mockup Design canvas (dicatat 11 Oktober 2026, belum dikoding)

**Kenapa tahap ini ada.** Di sesi 11 Oktober 2026, setelah deploy tahap 29 langkah 1-3, pemilik menunjukkan
bahwa beranda web (`https://habitflow.roziqrizal.com/`) tidak terlihat seperti mockup di
https://claude.ai/artifact/TWJ8Uus2hKtQQa81wKujG5. Klaim sebelumnya ("sudah sesuai mockup tahap 29") **keliru** —
dicek ulang, link artifact itu cuma disebut di tahap 30, 31, dan 32, **tidak pernah di tahap 29**. Tahap 29 memang
sengaja sempit (cuma kartu jadwal), dikerjakan lepas dari mockup. Tahap ini adalah audit menyeluruh: baca ulang tiga
artboard web di canvas (`Main.dc.html` "Web · Dashboard", `Habit.dc.html` "Web · Habit", `KerjaWeb.dc.html`
"Web · Kerja (rekap harian)"), bandingkan baris demi baris dengan kode Livewire yang live, dan catat persisnya apa
yang kurang serta tahap mana yang menanggungnya.

**Canvas tidak punya artboard web untuk Tugas Rumah** (cuma ada Mobile · Kerja, bukan versi web Tugas Rumah) —
jadi halaman `/tugas-rumah` (`Teams.php`) di luar cakupan audit ini, tetap polos seperti sekarang sampai ada mockup.

**Perbedaan label nav:** mockup pakai "Kerja" untuk tab follow-up (`<a href="KerjaWeb.dc.html">Kerja</a>`), kode
live pakai "Follow-up" (`routes/web.php` → nama rute `follow-ups`, label di `layout.blade.php` baris 52). Kedua label
menunjuk hal yang sama; cuma perlu disamakan kalau pemilik mau ikut mockup persis.

#### A. Beranda (`/`, `Dashboard.php` + `dashboard.blade.php` + `schedule-card.blade.php`) vs `Main.dc.html`

| Elemen di mockup | Status live (11 Okt 2026) | Tahap yang menanggung |
|---|---|---|
| Header tanggal ("Minggu, 11 Oktober 2026") di atas judul sapaan | Tidak ada — `dashboard.blade.php` langsung `<h1>Selamat datang, {nama}</h1>` tanpa baris tanggal | Baru, bagian tahap ini |
| Kartu hero "Fokus kesehatan hari ini" (judul, narasi, chip, panel "Dasar rekomendasi") | Tidak ada sama sekali di web | Tahap 30 langkah 5 (belum dikerjakan) |
| Kartu "Jadwal hari ini" (Sekarang/Berikutnya) | **Sudah ada** lewat `<livewire:schedule-card />`, strukturnya (kartu terisi warna untuk Sekarang, border untuk Berikutnya, teks "sisa N menit") sudah dekat dengan mockup. Bedanya cuma judul ("Jadwal sekarang" vs "Jadwal hari ini") dan belum dibungkus gaya `hf-card` yang konsisten dengan kartu lain | Tahap 29 (sudah jalan), tinggal langkah 4 (Android kirim data) supaya tidak kosong |
| Strip 4 kartu ringkasan: Habit hari ini (X/9 + bar), To-do hari ini (X/5 + bar), Follow-up kerja (Lewat tanggal/Jatuh tempo/Inbox), Tugas Rumah (jumlah belum selesai) | **Tidak ada sama sekali.** `dashboard.blade.php` cuma punya sapaan, kartu jadwal, dan kotak status akun Google | Baru — tidak tercakup tahap manapun yang ada. Datanya sendiri sudah tersedia dari tahap 28 (habit/to-do web), 27 (follow-up web), 26 (tugas rumah web), tinggal dirangkum jadi kartu di beranda |
| Section "Kesehatan" penuh: grafik berat SVG 6 minggu + target, grafik batang langkah 7 hari, kartu tensi | Tidak ada di web | Tahap 30 langkah 5 (belum dikerjakan) |
| Gaya kartu (`hf-card`: border radius 16px, padding konsisten) dan tema terang/gelap dengan token warna sage yang sama | Tema terang/gelap **sudah ada** (`partials/theme-toggle.blade.php`, token warna di `partials/theme-styles.blade.php`) dan tampaknya sudah pakai palet sage yang sama; kartu individual belum konsisten pakai gaya `hf-card` | Baru, bagian tahap ini (perapian visual) |

#### B. Habit (`/habit`, `HabitToday.php` + `habit-today.blade.php`) vs `Habit.dc.html`

| Elemen di mockup | Status live (11 Okt 2026) | Tahap yang menanggung |
|---|---|---|
| Baris judul + tanggal, lalu 3 kartu stat (Habit hari ini X/9 + bar, To-do hari ini X/5 + bar, Streak N hari + terpanjang) | Tidak ada. `habit-today.blade.php` langsung `<h1>Habit</h1>` lalu daftar | Baru — streak backend ada di model Android/`domain/` tapi belum tentu diekspos API web; perlu dicek saat dikerjakan |
| Kartu "Hari ini": checklist habit (wajib di atas) + to-do, dengan caption "Habit wajib selalu tampil di atas..." | **Sudah ada secara fungsional** (checklist habit dan to-do jalan, toggle wajib, tambah to-do), tinggal gaya kartu dan caption yang beda | Tahap 28 (sudah selesai secara fungsi), tahap ini tinggal menyamakan visual |
| Section "Kontribusi": heatmap gabungan 66 minggu + heatmap per habit (mini, 14 minggu, biner) | **Tidak ada sama sekali di web.** Heatmap cuma ada di Android (`ContributionViewModel`, tahap 4 dan 8) | Baru — perlu endpoint API heatmap web (belum ada di `routes/api.php` setahu audit ini) sebelum bisa digambar |
| Kartu "Kelola habit": ubah nama, toggle wajib, hapus, tambah habit | **Sudah ada secara fungsional** (`habit-today.blade.php` baris 41-73: ubah nama, toggle wajib dengan konfirmasi, hapus, tambah), tinggal gaya kartu | Tahap 28 (sudah selesai secara fungsi), tahap ini tinggal visual |

#### C. Kerja / Follow-up (`/follow-up`, `FollowUps.php` + `follow-ups.blade.php`) vs `KerjaWeb.dc.html` ("Web · Kerja (rekap harian)")

Ini beda paling besar — **bukan cuma soal gaya, tapi konsep halaman yang beda total**:

- **Live sekarang** (`follow-ups.blade.php`): daftar follow-up datar dikelompokkan per status (Inbox/Aktif/Menunggu/
  Selesai), form tambah cepat di atas, edit inline per item termasuk deteksi konflik 409. Ini persis cakupan tahap 27
  ("Web untuk Follow-up Kerja") dan sudah selesai untuk cakupan itu.
- **Mockup** (`KerjaWeb.dc.html`): halaman **"Rekap hari ini"** — kartu hero ringkasan (judul "7 dari 9 follow-up
  selesai", narasi, chip status, tingkat selesai %), daftar follow-up dengan filter chip (Semua/Inbox/Aktif/
  Menunggu/Selesai), lalu panel samping: **Meeting & briefing** (catatan rapat per meeting), **Rencana besok**
  (checklist, bisa tambah dari Inbox), **Catatan EOD**, dan tombol **Bagikan ringkasan**.
- Potongan mockup yang **sudah** punya rencana di tahap lain:
  - Catatan EOD dan rencana besok: data modelnya sudah ada di Android sejak tahap 19 (`daily_scrums` table:
    `yesterday`/`today`/`blockers`, field `eodNote`), tapi **belum pernah dimigrasi ke web** — tidak ada endpoint
    atau tabel server untuk daily scrum/EOD di `routes/api.php` setahu audit ini. Ini gap yang belum tercatat di
    tahap manapun sebelumnya.
  - "Ringkasan dari Claude" / email digest, Meeting & briefing: ini isi tahap 32 ("Rekap kerja otomatis dari
    Claude"), yang masih **"belum dikoding"**.
- **Bagian yang murni baru** (tidak tercakup tahap manapun): filter chip per status di atas daftar, kartu hero rekap
  harian dengan statistik ringkas, dan tombol Bagikan ringkasan.

#### Rencana langkah (belum dikerjakan, urutan diusulkan)

1. **Beranda**: tambah strip 4 kartu ringkasan (Habit/To-do/Follow-up/Tugas Rumah) — ini yang paling murah karena
   datanya sudah ada semua lewat model yang sudah dipakai `HabitToday`, `FollowUps`, `Teams`.
2. **Habit**: bungkus ulang jadi kartu stat + gaya `hf-card`, lalu endpoint heatmap web (baru) dan komponen heatmap.
3. **Kerja/Follow-up**: migrasi daily scrum/EOD ke web (tabel + endpoint baru, pola sama seperti tahap 27/29), baru
   susun ulang jadi halaman "Rekap hari ini" dengan filter chip. Bagian Meeting & briefing dan Ringkasan Claude
   menunggu tahap 32.
4. **Kesehatan di beranda**: ini tahap 30 langkah 5, dikerjakan di jalurnya sendiri (langkah 3-4 server dan Android
   kirim data dulu sebelum web bisa menampilkan apa pun).

**Urutan terhadap tahap 31 (Login Wajib):** sama seperti tahap 30 langkah 3-6 dan tahap 32, tahap ini menyentuh
halaman web secara luas — sebaiknya **menunggu enam butir terbuka tahap 31 diputuskan (12 Oktober 2026)** sebelum
mulai, supaya tidak mengerjakan ulang kalau ada perubahan nav/struktur halaman dari Login Wajib (tab Pengaturan,
Profil, dll.).

**Selesai jika:**
- Beranda menampilkan strip 4 kartu ringkasan sesuai mockup, datanya benar dibanding halaman masing-masing (Habit,
  Follow-up, Tugas Rumah).
- Halaman Habit web punya 3 kartu stat dan section Kontribusi (heatmap gabungan + per habit) yang datanya sama
  dengan Android.
- Halaman Kerja/Follow-up punya kartu rekap harian, filter chip, dan panel Rencana besok/Catatan EOD terisi dari
  data yang sama dengan Android (setelah daily scrum/EOD dimigrasi ke web).
- Tema terang/gelap dan gaya kartu (`hf-card`) konsisten di ketiga halaman.
- Bagian yang memang menunggu tahap lain (Fokus kesehatan web, Meeting & briefing, Ringkasan dari Claude) ditandai
  jelas "menyusul" di UI, bukan dibangun setengah jadi.

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
