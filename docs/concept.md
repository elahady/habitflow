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

## Tahap 17-24: HabitFlow sebagai asisten harian (diskusi dulu, belum dikoding)

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

Menggantikan tahap 15. **HabitFlow menggantikan jurnal task harian di Notion** ("Ruang Kerja :
I am a Leader"). Task lama di Notion dihabiskan di sana sampai cut off. Task baru dicatat di
HabitFlow, tanpa migrasi data dari Notion.

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
| Cakupan | Semua data HabitFlow: habit, riwayat, to-do, jadwal, follow-up, EOD, pengaturan. Nanti kesehatan |
| Sifat app | Tetap offline-first. HP adalah sumber data. Sinkron berjalan saat ada internet |
| Login | Token pribadi. Dibuat sekali di server lewat perintah artisan, ditempel di Tentang. Tanpa akun |
| Cara sinkron | Snapshot lengkap (satu berkas JSON bernomor versi), bukan per baris |
| Kapan | Otomatis ±5 menit setelah ada perubahan (hanya saat ada internet) dan sekali sehari. Tombol "Sinkron sekarang" |
| Pulihkan | Manual dari Tentang, dengan konfirmasi yang menyebut waktu snapshot. Mengganti seluruh data di HP |
| Perlindungan | HTTPS wajib (kecuali emulator saat debug). Isi disimpan terenkripsi di server dengan kunci app Laravel |
| Beban VPS | Tanpa layanan baru: Laravel stateless di PHP-FPM yang sudah ada, SQLite untuk metadata, berkas snapshot di storage. Satu permintaan per sinkron |

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

### Tahap 22: Kalender dan acara rutin (diputuskan, belum dikoding)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Kalender.

| Pertanyaan | Keputusan |
|---|---|
| Sumber acara | Acara dibuat di HabitFlow, ditambah membaca kalender HP (hanya baca) |
| Kalender HP yang dibaca | Dipilih sendiri per kalender di pengaturan. Default tidak ada |
| Pengulangan | Sekali, harian, setiap N minggu di hari tertentu, bulanan per tanggal atau per urutan hari (Senin kedua), tahunan. Bisa diberi tanggal berakhir |
| Tampilan | Acara hari ini masuk timeline dan Sekarang/Berikutnya. Tab Kerja punya Agenda 7 hari dan tampilan bulan untuk melompat ke tanggal |
| Libur nasional | Otomatis dari daftar libur nasional dan cuti bersama. "Hari ini libur" aktif sendiri, bisa dibatalkan per tanggal |
| Pengingat | −15 menit hanya untuk acara HabitFlow. Acara kalender HP memakai pengingat Google Calendar, supaya tidak dobel |
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
  pengaturan. Tanpa izin, fitur ini mati dan acara HabitFlow tetap jalan.
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
- Kalender HP yang dicentang tampil di dashboard dan Agenda tanpa notifikasi dari HabitFlow.
  Yang tidak dicentang tidak tampil. Tanpa izin kalender, app tetap jalan.
- Di tanggal libur nasional, blok dan acara Kerja mati otomatis, sholat dan alarm Subuh tetap.
  Membatalkan libur untuk satu tanggal mengembalikan blok Kerja hari itu.
- Catatan cepat saat acara Kerja berlangsung tertaut ke acara itu, dan saat EOD terkumpul per
  acara.

### Tahap 23: Asupan makan (diputuskan, belum dikoding)

**Keputusan (5 Oktober 2026).** Aturan fitur di `docs/rancangan.md` bagian Asupan makan.

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
  disimpan di environment server. Endpoint HabitFlow di server dilindungi token yang sama
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
