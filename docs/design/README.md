# Desain HabitFlow

Written for: Roziq dan Claude, sebagai acuan setiap kali menambah atau mengubah tampilan.
Dokumen ini berdiri sendiri: semua nilai warna, font, jarak, dan bentuk ada di sini, dan
kode di `app/src/main/java/com/roziqrizal/habitflow/ui/` mengikuti dokumen ini.

Kalau nilai di kode dan di dokumen berbeda, ubah dokumen dulu, lalu kode. Kalau perlu
keputusan baru, tulis di bagian [Keputusan terbuka](#keputusan-terbuka).

## Prinsip

1. **Tenang, bukan memarahi.** Habit yang belum dicentang tampil abu-abu netral, bukan
   merah. Tidak ada peringatan yang memblokir.
2. **Habit dulu.** Habit selalu tampil di atas to-do, dan lebih besar.
3. **Angka dan status selalu disertai teks.** Warna hanya penguat, tidak pernah satu-satunya
   pembawa arti.
4. **Satu keluarga visual dengan homepage roziqrizal.com.** Palet hijau sage, judul serif,
   isi sans-serif, dan kartu putih di atas latar hangat.

## Warna

Semua warna dinyatakan sebagai peran Material 3, lalu dipetakan ke `ColorScheme`.

### Terang

| Peran | Hex | Dipakai untuk |
|---|---|---|
| primary | `#4D6359` | Label kecil di kartu habit ("Wajib"), judul bagian lain |
| onPrimary | `#FFFFFF` | Teks di atas primary |
| primaryContainer | `#8FA79B` | Level heatmap 3 |
| onPrimaryContainer | `#273C33` | Teks dan ikon di atas tombol tonal |
| inversePrimary | `#B3CCBF` | Level heatmap 2 |
| primaryFixed (token) | `#CFE8DB` | Latar tombol tonal, indikator tab aktif, level heatmap 1 |
| secondary | `#48626E` | Cadangan, belum dipakai |
| background, surface | `#FAF9F7` | Latar dasar |
| onBackground, onSurface | `#1B1C1B` | Teks utama |
| onSurfaceVariant | `#424845` | Teks sekunder, label, keterangan |
| surfaceContainerLowest | `#FFFFFF` | Kartu |
| surfaceContainerLow | `#F5F3F1` | Latar layar (di belakang kartu) |
| surfaceContainer | `#EFEEEC` | Bar navigasi bawah |
| surfaceContainerHighest | `#E3E2E0` | Level heatmap 0 (belum selesai) |
| outline | `#727874` | Cadangan |
| outlineVariant | `#C2C8C3` | Garis kartu (1dp) |
| error | `#BA1A1A` | Hanya untuk galat sistem, belum dipakai |

### Gelap

| Peran | Hex |
|---|---|
| primary | `#B3CCBF` |
| onPrimary | `#1F352B` |
| primaryContainer | `#354B42` |
| onPrimaryContainer | `#CFE8DB` |
| inversePrimary | `#4D6359` |
| primaryFixed (token) | `#354B42` |
| secondary | `#AFCBD9` |
| background, surface | `#121412` |
| onBackground, onSurface | `#E3E3E0` |
| onSurfaceVariant | `#C2C8C3` |
| surfaceContainerLowest | `#0D0F0E` |
| surfaceContainerLow | `#1A1C1A` |
| surfaceContainer | `#1E201E` |
| surfaceContainerHigh | `#282A28` |
| surfaceContainerHighest | `#333533` |
| outline | `#8C928E` |
| outlineVariant | `#424845` |
| error | `#FFB4AB` |

Catatan: di mode gelap, kartu (`surfaceContainerLowest`) justru lebih gelap dari latar.
Itu disengaja, supaya kartu tetap terbaca tanpa bayangan.

### Warna heatmap

| Level | Terang | Gelap | Arti |
|---|---|---|---|
| 0 | `#E3E2E0` | `#333533` | Tidak ada habit yang dicentang |
| 1 | `#CFE8DB` | `#354B42` | Kurang dari 50% |
| 2 | `#B3CCBF` | `#4D6359` | 50% atau lebih, belum semua |
| 3 | `#8FA79B` | `#8FA79B` | Semua habit selesai |
| 4 | `#4D6359` | `#CFE8DB` | Semua habit dan minimal 2 to-do |

Sel habit per habit memakai level 4 untuk "selesai" dan level 0 untuk "belum". Sel sebelum
habit dibuat tidak digambar sama sekali.

### Warna yang belum dipakai

Tidak ada warna status (hijau, kuning, merah) di layar. Kalau nanti dibutuhkan, aturannya:
selalu dengan ikon dan teks, dan jangan memakai merah untuk habit yang terlewat.

## Tipografi

Dua keluarga font, keduanya variabel dan berlisensi OFL (salinan di `app/src/main/assets/licenses/`):

- **Manrope** (400, 500, 600, 700): semua teks UI dan **semua angka**.
- **Libre Caslon Text** (400, 700): hanya judul layar dan judul bagian. Jangan dipakai untuk angka.

| Peran | Font | Ukuran | Berat | Kapan |
|---|---|---|---|---|
| displayMedium | Manrope | 40sp | SemiBold, angka tabular | Skor besar: "0/9", angka streak |
| headlineMedium | Caslon | 28sp | Normal | Judul layar: "Kontribusi", "Kelola habit" |
| titleLarge | Caslon | 18sp | Normal | Judul bagian: "Habit", "To-do", "Gabungan" |
| titleMedium | Manrope | 14sp | SemiBold | Nama habit di kartu heatmap, label kartu |
| bodyLarge | Manrope | 16sp | Normal | Nama habit di daftar |
| bodyMedium | Manrope | 14sp | Normal | Teks isi, nama to-do |
| bodySmall | Manrope | 12sp | Normal | Keterangan kecil |
| labelMedium | Manrope | 12sp | SemiBold | Label di bawah angka, tanggal di kartu ringkasan |
| labelSmall | Manrope | 11sp | Normal (default M3) | Label "Wajib" |

Ukuran selain tabel mengikuti default Material 3.

Aturan ukuran: semua teks memakai `sp`, jadi ikut ukuran font yang dipilih pengguna.
Ukuran elemen (kotak heatmap, ikon, jarak) memakai `dp` dan tidak ikut.

## Bentuk dan jarak

### Bentuk

| Nama | Radius | Dipakai untuk |
|---|---|---|
| small | 8dp | Cadangan |
| medium | 16dp | Kartu biasa (habit, to-do, heatmap, kelola habit) |
| large | 20dp | Kartu ringkasan (header Hari ini, streak) |
| kotak kecil | 2dp | Sel heatmap dan kotak legenda |
| kotak level | 4dp | Kotak level di header Hari ini |

Tombol memakai bentuk bawaan Material 3 (kapsul). Kartu selalu punya garis 1dp `outlineVariant`
dan tidak memakai bayangan.

### Jarak

Skala 4dp: 4, 8, 12, 16, 24, 32.

| Tempat | Nilai |
|---|---|
| Gutter layar (kiri dan kanan) | 16dp, kecuali Kontribusi 12dp (lihat di bawah) |
| Jarak antar kartu dalam daftar | 12dp |
| Padding dalam kartu biasa | 16dp (habit dan to-do: 8dp horizontal, 4-8dp vertikal) |
| Padding kartu ringkasan | 20dp kiri/kanan/atas, 16dp bawah |
| Jarak judul bagian | 12dp di atas, 4dp di kiri |
| Jarak antar blok angka | 24dp |
| Tinggi minimum sentuh | Mengikuti komponen Material 3 (checkbox, tombol). Belum diukur, lihat Aksesibilitas |

**Pengecualian Kontribusi:** gutter 12dp dan padding kartu heatmap 8dp. Alasannya, grid
minimal 26 minggu lebarnya 309dp (26 kolom × 12dp − 3dp), dan layar 360dp hanya menyisakan
320dp dengan pengaturan ini. Jangan menaikkan gutter di Kontribusi tanpa mengecilkan sel.

## Komponen

Komponen bersama ada di `ui/Components.kt`. Pakai ulang, jangan membuat versi baru di layar.

| Komponen | Isi | Aturan pemakaian |
|---|---|---|
| `AppCard` | Kartu putih, garis 1dp, sudut medium atau large, padding bisa diatur, `onClick` opsional | Semua kartu. Pakai `hero = true` hanya untuk ringkasan |
| `SectionTitle` | Judul bagian Caslon 18sp | Setiap bagian daftar. Jangan membuat judul bagian sendiri |
| `StatBlock` | Angka displayMedium di atas label labelMedium | Skor dan streak. Maksimal dua blok berdampingan |
| `TonalButton` | Tombol tonal dengan latar primaryFixed | Aksi utama layar, misalnya "+ To-do", "+ Habit" |
| `HeatmapGrid` | Grid 7 baris × 26–53 minggu (mengisi lebar), dengan legenda opsional | Kontribusi. Gabungan dan per habit |

Pilihan memakai `FilterChip` dan tombol segmen. Warna terpilihnya selalu dari `appFilterChipColors()` dan
`appSegmentedColors()` di `ui/Components.kt` (latar `primaryFixed`, teks `onPrimaryContainer`), bukan biru
bawaan Material.

Tombol hapus dan aksi sekunder memakai `TextButton`. Dialog memakai `AlertDialog`, dan
detail hari memakai `ModalBottomSheet`.

## Pola layar

### Hari ini
1. Kartu hero **Sekarang** dengan timeline (lihat [Dashboard di Hari ini](#dashboard-di-hari-ini-tahap-17)).
2. Kartu hero: tanggal (labelMedium), dua `StatBlock` (Habit, To-do), lalu baris level dan streak.
3. Kartu Langkah (tahap 21), kartu air (tahap 20, lihat [Pengingat kerja](#pengingat-kerja-tahap-20)), lalu kartu Tensi
   dan Berat berdampingan (lihat [Kesehatan](#kesehatan-tahap-21)).
4. Judul "Habit", lalu daftar habit (habit wajib di atas, bertanda "Wajib").
5. Judul "To-do hari ini (x/5)", daftar to-do, lalu tombol `TonalButton` "+ To-do".
   Tombol nonaktif saat sudah 5, dan pesan penuh muncul di bawahnya.

### Kontribusi
1. Judul layar "Kontribusi" (headlineMedium).
2. Kartu hero streak: "Sekarang" dan "Terpanjang" sebagai dua `StatBlock`.
3. Judul "Gabungan", kartu berisi heatmap gabungan dengan legenda.
4. Judul "Per habit", satu kartu per habit berisi nama dan heatmap tanpa legenda.
5. Tap kotak mana pun membuka `ModalBottomSheet`: tanggal, habit aktif dengan status,
   dan to-do hari itu dengan status.

### Kelola habit
1. Judul "Kelola habit".
2. Tombol `TonalButton` "+ Habit".
3. Satu kartu per habit: nama (tap untuk ubah), lalu baris "Wajib" (bodySmall,
   `onSurfaceVariant`) dengan `Switch` Material 3 yang dikecilkan ke 55% (sekitar 29×18dp, setinggi teks,
   jarak 8dp dari label). Ukuran bawaan 52×32dp terlalu besar untuk baris keterangan. Warna tetap bawaan
   (track `primary`, thumb `onPrimary` saat aktif). Seluruh baris bisa ditekan, jadi area
   sentuh tidak ikut mengecil. Tombol "Hapus" hanya ada untuk habit tidak wajib.
4. Mematikan switch membuka `AlertDialog` "Lepas status wajib?" dengan tombol "Batal" dan
   "Lepas". Menyalakan switch langsung berlaku.

### Tentang
Nama app (headlineMedium), "Versi x.y" (bodyMedium, `onSurfaceVariant`), dan "Dibuat oleh Roziq Rizal".
Teks dipusatkan. Di bawahnya enam bagian pengaturan, masing-masing dengan label labelMedium (`onSurfaceVariant`) dan jarak 32dp di atasnya:

- **Tampilan**: `SingleChoiceSegmentedButtonRow` dengan segmen Sistem, Terang, Gelap. Segmen aktif memakai latar `primaryFixed` dan teks `onPrimaryContainer`, sama dengan `TonalButton`.
- **Lokasi untuk waktu sholat**: nama (bodyLarge) dan koordinat (bodySmall), lalu `TonalButton` "Pakai lokasi saat ini" dan `TextButton` "Atur manual" (dialog nama kota, lintang, bujur).
- **Notifikasi**: satu baris dengan judul "Notifikasi tetap", keterangan bodySmall, dan `Switch` di kanan.
- **Alarm**: "Alarm berikutnya" (bodyLarge) dengan hari, tanggal, dan jam (bodySmall); tombol teks "Matikan untuk tanggal itu" (jadi "Nyalakan lagi" kalau sudah dimatikan); tombol teks "Pilih nada" dengan nama nada terpilih di bawahnya.
- **Pengingat adzan**: lima baris (Subuh, Dzuhur, Ashar, Maghrib, Isya), masing-masing nama (bodyLarge) dan `Switch` di kanan. Mematikan satu hanya menghentikan notifikasi waktu sholat itu.
- **Sinkron ke server**: baris status (bodyLarge) "Belum diatur" atau "Terakhir berhasil: Rab 7 Okt 12.44", dengan
  pesan hasil terakhir di bawahnya (bodySmall, `onSurfaceVariant`; gagal ditulis sebagai kalimat biasa, bukan merah).
  Lalu satu baris "Sinkron otomatis" dengan `Switch` (nonaktif sebelum server diisi), `TonalButton` "Sinkron sekarang",
  dan tiga `TextButton`: "Atur server", "Uji koneksi", "Pulihkan dari server". Tombol yang sedang berjalan menampilkan
  "Memproses..." dan semuanya nonaktif selama itu.
  - **Dialog Atur server** (`AlertDialog`): kolom alamat (placeholder `https://...`) dan kolom token (disamarkan), dengan
    keterangan bodySmall "Token dibuat di server dengan `php artisan habitflow:token`". Simpan menolak alamat tanpa
    `https://` dengan teks galat di bawah kolom.
  - **Dialog pulihkan** (`AlertDialog`): judul "Pulihkan dari server?", isi menyebut waktu snapshot dan ringkasan
    jumlahnya (habit, follow-up), lalu "Semua data di HP ini akan diganti. Ini tidak bisa dibatalkan." Tombol utama
    `Button` "Ganti semua data", dan `TextButton` "Batal".

Layar bisa digulir karena isinya lebih panjang dari layar kecil. Urutan bagian: Tampilan, Lokasi, Notifikasi, Pengingat kerja (tahap 20), Alarm, Pengingat adzan, Sinkron ke server.

### Dashboard di Hari ini (tahap 17)
1. Kartu hero "Sekarang" paling atas, di atas kartu skor: label "Sekarang" (labelMedium,
   `onSurfaceVariant`) dengan tombol teks "Atur" di kanan, nama blok (titleLarge Caslon), lalu
   rentang waktu dan sisa waktu (bodyMedium). Di bawahnya garis pemisah `outlineVariant`, lalu
   "Berikutnya" dengan nama dan jam mulai. Tanpa blok aktif: "Tidak ada blok sekarang". Setelah
   blok terakhir: "Selesai untuk hari ini".
2. Baris "Lihat jadwal hari ini" dengan chevron (▾ tertutup, ▴ terbuka) membuka timeline: satu
   baris per blok (jam di kiri dengan angka tabular, lebar tetap 96dp, nama di kanan). Blok yang
   sedang berjalan memakai latar `primaryFixed` (sudut 8dp) dan nama SemiBold. Blok yang sudah
   lewat memakai `onSurfaceVariant`. Titik waktu (batas tidur) hanya menampilkan jam mulai.
3. Tombol teks "Hari ini libur" di bawah timeline. Saat aktif, kartu menampilkan
   "Hari libur, blok kantor dimatikan" dengan tombol "Batalkan".
4. Layar **Atur jadwal** (layar penuh di atas tab, tab tetap empat; tombol kembali menutupnya):
   judul (headlineMedium), keterangan lokasi sholat, `TonalButton` "+ Blok", lalu satu kartu per
   blok: nama, jam atau patokan sholat dan durasi, hari aktif sebagai tujuh huruf S S R K J S M
   (hari aktif tebal, tidak aktif redup `outlineVariant`), dan tingkat notifikasi. Tap membuka editor
   (`AlertDialog` gulir): nama, patokan waktu (jam tetap atau waktu sholat dengan selisih menit),
   durasi, jam selesai tetap (opsional), hari aktif (chip), tingkat notifikasi, habit yang ditautkan.
   Pintu masuk dari tombol "Atur" di kartu Sekarang.

### Layar alarm (tahap 18)
Layar penuh yang muncul di atas lock screen saat alarm berbunyi (layar menyala), memakai tema app dan
latar `surfaceContainerLow`. Isi dipusatkan dan diletakkan di tengah vertikal:
1. Label "Alarm" (labelMedium, `onSurfaceVariant`).
2. Jam sekarang (displayMedium, angka tabular), misalnya "03.55".
3. Nama blok (titleLarge Caslon), misalnya "Bangun".
4. `TonalButton` "Matikan" selebar layar (tinggi 56dp), lalu `TextButton` "Tunda 5 menit" di bawahnya dengan
   keterangan "Tunda ke-1 dari 2" (bodySmall). Setelah dua kali tunda, tombol Tunda dan keterangannya hilang.
Tanpa warna merah atau animasi. Layar menutup sendiri begitu alarm dimatikan, ditunda, atau berhenti
sendiri setelah 10 menit.

### Kerja (tahap 19)
1. Tab kedua "Kerja" (urutan tab: Hari ini, Kerja, Kontribusi, Habit, Tentang). Judul layar "Kerja"
   (headlineMedium), lalu dua `TonalButton` berdampingan: "Daily scrum" dan "EOD".
2. Kalau ada nama orang yang pernah dipakai, baris `FilterChip` di bawah tombol: "Semua" lalu
   satu chip per nama. Chip terpilih memfilter semua bagian.
3. Bagian dengan `SectionTitle` dan jumlah: "Inbox (n)", "Hari ini (n)", "Lewat tanggal (n)",
   "Menunggu (n)", "Nanti (n)". Bagian kosong disembunyikan, kecuali Hari ini yang menampilkan
   "Belum ada yang dikerjakan hari ini." (bodyMedium, `onSurfaceVariant`).
4. Satu `AppCard` per follow-up: `Checkbox` di kiri (centang = selesai), judul (bodyLarge) dan baris
   keterangan (bodySmall, `onSurfaceVariant`) berisi tanggal, jam, dan orang. Lewat tanggal ditandai
   teks "Lewat n hari", bukan warna merah. Yang dipilih untuk hari ini diberi teks "Dipilih hari ini".
   Tap kartu membuka editor.
5. **Editor follow-up** (`AlertDialog` gulir): judul, status (baris `FilterChip` Inbox, Aktif, Menunggu),
   tanggal dan jam (tombol teks yang membuka `DatePickerDialog` dan `TimePicker`, dengan tombol "Hapus"
   untuk mengosongkan), orang (`OutlinedTextField` dengan chip saran di bawahnya), catatan, lalu
   tombol Simpan, Hapus, Batal. Jam hanya muncul setelah tanggal diisi.
6. **Kartu ringkasan di Hari ini**: kalau ada Inbox atau lewat tanggal, satu `AppCard` kecil berisi
   "Kerja · Inbox n · Lewat tanggal n · Hari ini n" (bodyMedium). Tap membuka tab Kerja. Disembunyikan
   kalau semuanya nol.
7. **Tombol Catat**: `ExtendedFloatingActionButton` "Catat" (latar `primaryFixed`, tanpa ikon) di kanan
   bawah semua tab, di atas bar navigasi. Membuka `ModalBottomSheet` berisi satu `OutlinedTextField` satu
   baris yang langsung fokus, dengan aksi keyboard Selesai. Enter menyimpan ke Inbox, mengosongkan
   kolom, dan sheet tetap terbuka dengan teks "Tersimpan di Inbox" (bodySmall) yang hilang sendiri.

### Daily scrum dan EOD (tahap 19)
Dua layar penuh di atas tab (tab tetap lima, tombol kembali menutupnya), dibuka dari tombol di tab
Kerja atau dari notifikasi blok Kerja pagi dan EOD.
1. **Daily scrum**: judul "Daily scrum" (headlineMedium) dan tanggal (bodySmall). Daftar kartu
   kandidat (lewat tanggal, jatuh tempo hari ini, dipilih kemarin, menunggu yang dicek ulang hari
   ini), masing-masing dengan `Checkbox` "Kerjakan hari ini" yang langsung tersimpan. Di bawahnya
   judul "Ambil dari Nanti (n)" dengan kartu yang sama untuk Nanti dan Menunggu. Tombol
   `TonalButton` "Selesai daily scrum" menandai selesai dan menutup layar.
2. **EOD**: judul "EOD" dan tanggal. Bagian "Hari ini (n)": satu kartu per follow-up dengan baris
   `FilterChip` Selesai, Lanjut besok, Pindah tanggal, Menunggu (awalnya Lanjut besok). Pindah tanggal
   dan Menunggu membuka `DatePickerDialog`; tanggalnya tampil di chip. Bagian "Inbox (n)": satu kartu
   per item dengan `FilterChip` Biarkan, Nanti, Pilih tanggal, Menunggu, Hapus (awalnya Biarkan).
   Lalu `OutlinedTextField` "Catatan EOD" beberapa baris, dan dua tombol: `TonalButton` "Simpan EOD"
   serta `TextButton` "Bagikan" yang membuka share sheet berisi ringkasan.
3. Layar ini tidak memakai warna status. Yang belum diputuskan tidak ditandai merah.

### Pengingat kerja (tahap 20)
1. **Kartu air** di Hari ini, tepat di bawah kartu skor dan di atas judul "Habit": `AppCard` dengan label "Air putih"
   (labelMedium, `onSurfaceVariant`), lalu angka "3 / 8" (displayMedium kecil, angka tabular) dengan keterangan "gelas
   hari ini" (bodySmall), dan bar kemajuan setinggi 8dp (`primary` di atas `surfaceContainerHighest`, penuh di 8 atau
   lebih). Di kanan angka, `TonalButton` "+ Segelas" dan `TextButton` "−" di sebelahnya (nonaktif di 0). Area sentuh
   tombol minimal 48dp. Tanpa warna status dan tanpa animasi saat target tercapai, hanya teks "Target tercapai".
2. **Notifikasi** (channel Info jadwal, senyap): judul "Waktunya minum", "Break sebentar", atau "Break + minum"
   (bodyLarge), isi "3 dari 8 gelas hari ini" atau "Berdiri dan regangkan badan." Tombol "Sudah minum" untuk air dan
   gabungan, tanpa tombol untuk break. Tap membuka app.
3. **Tentang**: bagian baru "Pengingat kerja" di antara Notifikasi dan Alarm, berisi dua baris dengan `Switch`:
   "Minum air" (keterangan "Setiap 60 menit di jam kerja") dan "Break" (keterangan "Setiap 90 menit di jam kerja").
4. **Editor blok** (Atur jadwal): satu baris `Switch` "Pengingat air dan break" di bawah pilihan hari aktif, dengan
   keterangan bodySmall "Dihitung dari jam mulai blok ini." Kartu blok di daftar menambah teks "Air dan break"
   (bodySmall) bila menyala.

### Asupan makan (tahap 23, belum diterapkan)
1. Kartu "Makan" di dashboard: empat chip kecil Sarapan, Siang, Malam, Camilan. Chip yang
   sudah dicatat memakai latar `primaryFixed` dan tanda centang, yang belum memakai garis
   `outlineVariant`. Tap chip membuka bottom sheet catat.
2. Bottom sheet catat: judul waktu makan dan jam (bisa diubah), baris `FilterChip` Karbo,
   Lauk, Sayur, Buah, baris kedua Gorengan dan Manis, kolom catatan opsional, tombol Simpan.
3. Kartu air (tahap 20) mendapat dua tombol teks kecil "+ Kopi" dan "+ Manis" dengan
   jumlahnya. Kopi ke-3 menampilkan teks tenang di bawah kartu, bukan dialog.

### Kalender (tahap 22)
1. Tab Kerja mendapat tombol ketiga "Agenda" di samping "Daily scrum" dan "EOD" (tiga `TonalButton` berdampingan, setiap
   tombol mengisi sepertiga lebar, padding horizontal dalam tombol 8dp dan teks satu baris supaya "Daily scrum" tidak membungkus).
2. **Agenda** (layar penuh di atas tab, seperti Daily scrum dan EOD): tombol "‹ Kembali", judul "Agenda" (headlineMedium),
   `SingleChoiceSegmentedButtonRow` "7 hari" dan "Bulan", lalu `TonalButton` "+ Acara". **7 hari**: hari ini sampai enam hari
   lagi, tiap hari satu judul (labelMedium, misalnya "Jumat, 9 Oktober"; "Hari ini" untuk hari ini) lalu satu baris per acara:
   jam dengan angka tabular (bodyLarge, sejajar dengan judul) selebar 96dp ("14.00–15.00" atau "Sepanjang hari"), judul (bodyLarge), dan keterangan (bodySmall,
   `onSurfaceVariant`) berisi label, pengulangan ("Setiap 2 minggu") atau nama kalender HP. Hari tanpa acara menampilkan
   "Tidak ada acara." (bodySmall). Libur nasional tampil sebagai baris "Libur: <nama>" (bodyMedium) di awal hari, dan acara
   Kerja pada hari itu tidak tampil. Baris acara HabitFlow bisa ditekan, baris kalender HP tidak.
3. **Menu kejadian** (`AlertDialog` berisi `TextButton` bertumpuk) saat baris acara HabitFlow ditekan: "Ubah acara", "Ubah
   kejadian ini", "Kembalikan kejadian ini" (hanya kalau kejadian itu sudah diubah) dan "Lewati kejadian ini" (hanya untuk acara
   berulang), "Hapus acara", dan "Batal". Lewati langsung berlaku; Hapus meminta konfirmasi satu dialog.
4. **Tampilan bulan**: judul bulan dan tahun (titleLarge) dengan tombol teks "‹" dan "›" di kedua sisi, baris nama hari (Sen
   sampai Min, labelSmall), lalu grid 7 kolom. Tiap sel setinggi 48dp berisi angka tanggal (bodyMedium, angka tabular) dan titik
   `primary` 6dp di bawahnya kalau ada acara. Hari ini memakai latar `primaryFixed`, hari libur memakai teks `onSurfaceVariant`
   dengan garis bawah tipis. Tap tanggal menampilkan daftar acara tanggal itu di bawah grid (format baris sama dengan 7 hari).
5. **Editor acara** (`AlertDialog` gulir): judul, `SingleChoiceSegmentedButtonRow` label Kerja dan Pribadi, switch "Sepanjang
   hari", tombol teks tanggal mulai dan jam mulai (`AppDatePickerDialog`, `AppTimePickerDialog`; jam disembunyikan kalau
   sepanjang hari), kolom durasi menit, baris `FilterChip` pengulangan (Sekali, Harian, Mingguan, Bulanan, Tahunan). Mingguan
   menambah kolom "Setiap berapa minggu" dan tujuh `FilterChip` hari. Bulanan menambah dua `FilterChip` pilihan: "Tanggal 12" dan
   "Senin kedua" (menyesuaikan tanggal mulai). Pengulangan selain Sekali menambah tombol teks "Berakhir: ..." (dengan "Hapus").
   Lalu baris `FilterChip` pengingat (Tanpa, 5, 10, 15, 30, 60 menit), kolom catatan, dan tombol Simpan, Batal. Untuk "Ubah kejadian
   ini" editor hanya memuat judul, tanggal, jam, dan durasi.
6. **Timeline dashboard**: acara tampil seperti blok dengan penanda kecil "Acara · Kerja" atau "Acara · Pribadi" (labelSmall,
   `onSurfaceVariant`) di bawah nama. Acara sepanjang hari tampil di awal timeline dengan jam "Sepanjang hari".
7. **Kartu hari libur** (di kartu Sekarang, menggantikan baris "Hari libur"): libur nasional aktif menampilkan "Libur nasional:
   <nama>, blok kantor dimatikan" dengan tombol "Batalkan"; libur nasional yang dibatalkan menampilkan "Libur nasional
   dibatalkan, blok kantor jalan" dengan tombol "Libur lagi"; libur manual seperti sebelumnya.
8. **Tentang, Kalender HP**: bagian baru "Kalender HP" setelah Pengingat kesehatan. Tanpa izin: satu kalimat bodySmall dan
   `TonalButton` "Izinkan akses kalender". Dengan izin: satu baris per kalender (nama bodyLarge, nama akun bodySmall) dengan
   `Switch`, dan kalau menyala dua `FilterChip` Kerja dan Pribadi di bawahnya.
9. **Notifikasi pengingat acara** (tingkat Pengingat): judul = judul acara, isi "Mulai 14.00 · Kerja".
10. **Catat cepat tertaut**: sheet Catat menampilkan baris bodySmall "Tertaut ke acara: <judul>" saat ada acara Kerja berlangsung.
    Di EOD, Inbox diberi judul kelompok (labelMedium) "Dari acara: <judul>" di atas item-item dari acara itu, ditambah tanggal
    pendek kalau bukan hari ini (jam tidak disimpan di follow-up). Item di luar acara tampil lebih dulu tanpa judul kelompok.
    Kartu follow-up di tab Kerja dan EOD menambahkan keterangan yang sama ("Dari acara: <judul>") di akhir baris keterangan.

### Kesehatan (tahap 21)
1. **Kartu di Hari ini**, di bawah kartu skor: kartu Langkah selebar layar (angka "3.240 /
   8.000" dengan displayMedium kecil, bar kemajuan `primary` di atas `surfaceContainerHighest`),
   lalu kartu Air (tahap 20), lalu dua kartu berdampingan Tensi dan Berat (angka terakhir, keterangan waktu,
   tombol teks "+ Catat"). Tanpa catatan: "Belum ada catatan". Kartu Langkah tanpa akses menampilkan satu kalimat
   tenang dan satu `TextButton`: "Izinkan akses langkah" (belum diizinkan), "Pasang Health Connect" (belum ada, di
   Android 13 ke bawah), atau "Perbarui Health Connect". Tanpa data hari ini: "0 / 8.000". Habit yang tercentang
   otomatis tidak diberi tanda khusus.
2. **Bottom sheet catat**: berat satu kolom angka (kg, satu desimal). Tensi dua kolom
   sistolik/diastolik berdampingan, nadi dan catatan opsional. Setelah simpan, tampil
   kategori sebagai teks.
3. **Tab Progres** (dulu Kontribusi): dua segmen di atas, "Habit" (isi lama) dan
   "Kesehatan". Kesehatan berisi grafik garis berat dengan garis putus-putus target, dan
   grafik tensi dua garis (sistolik dan diastolik). Grafik digambar dengan Canvas.
4. Kategori selalu berupa teks ("Normal-tinggi"), warna hanya penguat. Tidak memakai merah,
   termasuk untuk tensi sangat tinggi.
5. **Sheet catat, rincian.** Mode Berat: judul "Catat berat", kolom "Berat (kg)" dengan keyboard angka, dan kolom
   "Tinggi badan (cm)" hanya kalau tinggi belum diisi. Mode Tensi: judul "Catat tensi", dua kolom berdampingan
   "Sistolik" dan "Diastolik", lalu "Nadi (opsional)" dan "Catatan (opsional)". Tombol `TonalButton` "Simpan"
   selebar sheet, nonaktif sampai angkanya sah (teks galat bodySmall di bawah kolom, bukan merah). Setelah simpan,
   isi sheet berganti hasil: baris kategori (titleMedium, misalnya "BMI 24,3 · Normal" atau "Normal-tinggi"), baris
   keterangan (bodySmall: sisa ke target, atau saran bila tensi ≥ 180/110), dan `TextButton` "Selesai".
6. **Progres, Kesehatan.** Judul "Progres" dengan `SingleChoiceSegmentedButtonRow` Habit dan Kesehatan di bawahnya.
   Kesehatan: kartu ringkasan berat (angka terakhir displayMedium kecil, baris BMI dan kategori, baris target dan sisa,
   baris tren 4 minggu), kartu grafik berat (garis `primary`, titik di tiap catatan, garis putus-putus `outline`
   untuk target), lalu kartu ringkasan tensi (angka terakhir dan kategori) dan kartu grafik tensi (sistolik garis
   `primary` setebal 3dp, diastolik garis `onSurfaceVariant` setebal 1,5dp, dibedakan juga lewat ketebalan karena
   kedua warna sama-sama gelap, dengan keterangan "Garis atas sistolik (tebal), garis bawah diastolik (tipis)."). Sumbu: tiga label tanggal dan dua label nilai (labelSmall).
   Rentang 90 hari terakhir. Tanpa catatan: teks "Belum ada catatan berat. Catat dari Hari ini." (bodyMedium).
7. **Tentang.** Bagian baru "Kesehatan" (dua baris: "Tinggi badan" dengan nilainya dan "Target berat" dengan nilainya
   atau "Otomatis (25,0 BMI)", tap membuka `AlertDialog` satu kolom angka) dan "Pengingat kesehatan" (switch "Timbang
   berat, setiap Senin" dan "Ukur tensi" dengan `SingleChoiceSegmentedButtonRow` Harian, Mingguan, Mati). Urutan
   bagian: Tampilan, Lokasi, Notifikasi, Pengingat kerja, Kesehatan, Pengingat kesehatan, Alarm, Pengingat adzan,
   Sinkron ke server.
8. **Notifikasi** (Info, senyap): judul "Waktunya timbang", "Waktunya ukur tensi", atau "Timbang dan ukur tensi",
   isi "Sebelum aktivitas pagi." Tap membuka app di Hari ini.

### Daily scrum (tahap 15, dibatalkan)
1. **Kartu di Hari ini**, di bawah tombol "+ To-do": `AppCard` berisi judul "Daily scrum"
   (titleMedium) dan satu baris status (bodySmall, `onSurfaceVariant`): "Belum diisi" atau
   potongan isi "Hari ini". Seluruh kartu bisa ditekan.
2. **Layar isi**: judul "Daily scrum" (headlineMedium) dan tanggal (labelMedium). Tiga
   bagian dengan `SectionTitle`: "Kemarin (<hari, tanggal>)", "Hari ini", "Hambatan",
   masing-masing `OutlinedTextField` multi-baris. Tombol `TonalButton` "Bagikan" di bawah.
   Tombol kembali menyimpan dan menutup layar.
3. **Detail hari Kontribusi**: bagian "Daily scrum" di bawah to-do, tiga bagian sebagai
   teks biasa (bodyMedium). Tidak tampil kalau hari itu tidak ada daily scrum.
4. **Tentang**: di bawah pilihan Tampilan, berjarak 24dp: label "Pengingat daily scrum"
   (labelMedium), lalu baris "Ingatkan" dengan switch kecil (sama seperti switch wajib)
   dan jam (bodyMedium). Tap jam membuka `TimePicker` Material 3. Jam nonaktif saat
   switch mati.

### Navigasi bawah
- Lima tab: Hari ini, Kerja, Kontribusi, Habit, Tentang.
- Bar memakai `surfaceContainer`. Tab aktif memakai indikator pill `primaryFixed` dan teks `onSurface`.
  Tab tidak aktif memakai `onSurfaceVariant`.
- Tombol kembali membuka tab sebelumnya. Dari tab pertama, tombol kembali menutup app.

**Belum diterapkan:** tab belum punya ikon, jadi indikator pill mengecil menjadi garis tipis di atas
label. Kalau ikon ditambahkan, indikator akan tampil seperti pill penuh.

### System bar
- Edge-to-edge. Status bar transparan di atas latar layar (`surfaceContainerLow`).
- Bar gesture bawah memakai `surfaceContainer`, menyambung dengan bar navigasi app.
- Ikon system bar gelap di tema terang dan terang di tema gelap, mengikuti pilihan
  tampilan di app.
- Splash memakai latar `background` sesuai pilihan tampilan (Android 12+).

### Latar layar
Latar di belakang kartu memakai `surfaceContainerLow`, bukan `background`, supaya kartu putih terlihat.

## Heatmap

- 7 baris (Senin di atas, Minggu di bawah). Kolom terakhir adalah minggu ini.
- Jumlah kolom (minggu) mengikuti lebar kartu: sebanyak yang muat, minimal 26 dan paling
  banyak 53. Grid diletakkan di tengah sehingga sisa ruang kiri dan kanan sama (selisihnya
  selalu di bawah satu kolom, 12dp).
- Kotak 9dp dengan jarak 3dp, sudut 2dp. Lebar grid = kolom × 12dp − 3dp (309dp untuk 26 kolom).
- Hari setelah hari ini tidak digambar. Sel sebelum habit dibuat tidak digambar.
- Tap pada kotak memanggil detail hari. Tap di celah dihitung ke kotak terdekat (celah dibagi
  dua), jadi seluruh area grid merespons. Tap di kotak yang tidak digambar diabaikan.
- **Tekan lalu geser.** Setelah ditahan 300 ms:
  - Kotak di bawah jari diberi bingkai 1,5dp `onSurface`, di luar kotak.
  - Tooltip di atas jari: latar `inverseSurface`, teks `inverseOnSurface` labelMedium, sudut
    small (8dp), padding 8dp × 4dp, jarak 32dp di atas kotak supaya tidak tertutup ujung jari. Isi "<hari, tgl bln> · Level n",
    atau "Selesai"/"Belum" di heatmap per habit. Posisi digeser supaya tidak keluar layar.
    Tooltip memakai `Popup`, jadi tidak terpotong oleh kartu.
  - Lepas jari membuka detail hari. Lepas di luar grid atau di kotak yang tidak digambar
    berarti batal.
  - Getar `HapticFeedbackType.LongPress` saat aktif, `TextHandleMove` setiap pindah kotak.
- Legenda "Less" dan "More" dengan lima kotak level di antaranya, di bawah grid gabungan,
  rata kanan dengan tepi kanan grid.
- Deskripsi aksesibilitas: "Heatmap gabungan <n> minggu terakhir" atau "Heatmap <nama habit> <n> minggu terakhir".
  Setiap kotak punya deskripsi "<tanggal>, level <n>".

## Ikon app

- Potongan heatmap 3×3 di atas latar `#FAF9F7`. Level sel memakai warna heatmap terang,
  makin pekat ke kanan atas: `[2,3,4] / [1,2,3] / [0,1,2]` (baris atas ke bawah).
- Kanvas adaptive icon 108dp, sel 15dp, jarak 2dp, sudut 3dp. Grid 49dp di tengah, masih
  di dalam safe zone 66dp.
- Versi monochrome (themed icon Android 13+) memakai bentuk yang sama, level dinyatakan
  dengan alpha.
- Semua file ikon dibuat oleh `docs/design/ikon/generate.py`. Ubah script lalu jalankan ulang,
  jangan mengedit file hasilnya. PNG 512 untuk Play Store dan README: `docs/design/ikon/ikon-512.png`.

## Aksesibilitas

- Angka dan status selalu disertai teks (misalnya "Level 3", bukan hanya kotak berwarna).
  Kotak level di header punya `contentDescription` "Level hari ini N".
- Layar harus bisa dipakai di lebar 360dp tanpa scroll horizontal.
- Teks boleh membesar sampai ukuran font terbesar sistem. Kartu tumbuh mengikuti teks.

**Belum diverifikasi:** kontras kotak level 1 dan 2 terhadap kartu putih, target sentuh baris habit
(checkbox Material 3 sering di bawah 48dp), dan deskripsi per kotak heatmap (Tahap 11).

## Pemetaan ke kode

| Aturan | File |
|---|---|
| Warna terang dan gelap | `ui/theme/Color.kt` (`HabitFlowLightScheme`, `HabitFlowDarkScheme`) |
| Token primaryFixed | `ui/theme/Color.kt` (`HabitFlowTokens`, akses `MaterialTheme.tokens`) |
| Warna heatmap | `ui/theme/Color.kt` (`LightHeat`, `DarkHeat`) |
| Font dan skala teks | `ui/theme/Type.kt` (`HabitFlowTypography`) |
| Bentuk | `ui/theme/Theme.kt` (`HabitFlowShapes`) |
| Komponen bersama | `ui/Components.kt` |
| Kotak dan grid heatmap | `ui/HeatmapGrid.kt` |
| Navigasi dan latar layar | `ui/HabitFlowApp.kt` |
| System bar (edge-to-edge) | `MainActivity.kt` |
| Splash dan latar jendela | `res/values/themes.xml`, `res/values-night/themes.xml`, `data/ThemeSettings.kt` |

## Keputusan terbuka

1. **Ikon tab navigasi.** Belum ada. Pilihan: `material-icons-core` (terbatas) atau ikon vektor sendiri.
2. **Warna status** untuk peringatan. Belum ada peringatan di layar, jadi belum ada keputusan.
3. **Font di mode gelap.** Sama dengan mode terang. Belum diuji kontrasnya di perangkat.
4. **Layar Kontribusi dan detail hari** belum diperiksa di tangkapan layar setelah perubahan desain.
