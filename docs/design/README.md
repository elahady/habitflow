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

Tombol hapus dan aksi sekunder memakai `TextButton`. Dialog memakai `AlertDialog`, dan
detail hari memakai `ModalBottomSheet`.

## Pola layar

### Hari ini
1. Kartu hero: tanggal (labelMedium), dua `StatBlock` (Habit, To-do), lalu baris level dan streak.
2. Judul "Habit", lalu daftar habit (habit wajib di atas, bertanda "Wajib").
3. Judul "To-do hari ini (x/5)", daftar to-do, lalu tombol `TonalButton` "+ To-do".
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
3. Satu kartu per habit: nama (tap untuk ubah), label "Wajib" untuk habit wajib, dan tombol
   "Hapus" hanya untuk habit tidak wajib.

### Tentang
Nama app (headlineMedium), "Versi x.y" (bodyMedium, `onSurfaceVariant`), dan "Dibuat oleh Roziq Rizal".
Teks dipusatkan.

Di bawahnya, berjarak 32dp: label "Tampilan" (labelMedium, `onSurfaceVariant`), lalu
`SingleChoiceSegmentedButtonRow` dengan tiga segmen "Sistem", "Terang", "Gelap". Segmen aktif
memakai latar `primaryFixed` dan teks `onPrimaryContainer`, sama dengan `TonalButton`.

### Navigasi bawah
- Empat tab: Hari ini, Kontribusi, Habit, Tentang.
- Bar memakai `surfaceContainer`. Tab aktif memakai indikator pill `primaryFixed` dan teks `onSurface`.
  Tab tidak aktif memakai `onSurfaceVariant`.
- Tombol kembali membuka tab sebelumnya. Dari tab pertama, tombol kembali menutup app.

**Belum diterapkan:** tab belum punya ikon, jadi indikator pill mengecil menjadi garis tipis di atas
label. Kalau ikon ditambahkan, indikator akan tampil seperti pill penuh.

### System bar (tahap 13, belum diterapkan)
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
- Tap pada kotak memanggil detail hari. Celah antar kotak tidak merespons tap.
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

## Keputusan terbuka

1. **Ikon tab navigasi.** Belum ada. Pilihan: `material-icons-core` (terbatas) atau ikon vektor sendiri.
2. **Warna status** untuk peringatan. Belum ada peringatan di layar, jadi belum ada keputusan.
3. **Font di mode gelap.** Sama dengan mode terang. Belum diuji kontrasnya di perangkat.
4. **Layar Kontribusi dan detail hari** belum diperiksa di tangkapan layar setelah perubahan desain.
