# Rancangan HabitFlow

Written for: Roziq (pemilik project), sebagai acuan sebelum dan selama coding.

## Tujuan

Satu layar untuk memantau kebiasaan harian dan menyelesaikan to-do kecil.
Habit adalah prioritas utama. To-do menjadi pendorong tambahan. Progres
divisualisasikan seperti kontribusi GitHub.

## Habit

- Daftar habit awal:
  1. **Sholat 5 waktu** (wajib)
  2. **Baca Al-Quran** (wajib)
  3. Jalan kaki 20 menit
  4. Air putih 2 liter
  5. Tanpa minuman manis
  6. Ngopi maksimal 2 gelas (sepulang kerja)
  7. Tidur sebelum 22.00
  8. Makan malam selesai 2-3 jam sebelum tidur
  9. Tanpa gorengan atau camilan manis
- **Habit wajib** (ditandai `isMandatory`) tidak bisa dihapus dan selalu tampil
  paling atas di layar Hari ini dan Habit. Habit wajib tetap dihitung di level
  dan streak seperti habit lain.
- Status wajib bisa diatur sendiri untuk habit mana pun (tahap 14). Habit baru dibuat
  tidak wajib. Menyalakan wajib langsung berlaku. Mematikan wajib meminta konfirmasi,
  setelah itu habit bisa dihapus. Dua habit seed tetap wajib sebagai nilai awal.
- Habit lain bisa ditambah, diubah, dan dihapus.
- Setiap habit hanya punya status centang: sudah atau belum, per hari.
- Tidak ada input angka di versi ini (tekanan darah, gula, berat badan belum masuk).

## To-do

- To-do dibuat per hari dan tidak berulang.
- Maksimal 5 to-do per hari.
- To-do yang belum selesai di akhir hari **otomatis dipindah ke besok**.
  Saat app dibuka dan hari berganti, to-do lama dengan status belum selesai
  diubah tanggalnya menjadi hari ini.
- Pemindahan hanya mengubah tanggal, sehingga hari lama tidak lagi
  menghitung to-do itu sebagai selesai (perilaku yang diinginkan).

## Jadwal harian (tahap 17)

- Jadwal adalah satu daftar **blok**. Setiap blok punya: nama, waktu mulai, durasi, hari
  aktif (Senin sampai Minggu), tingkat notifikasi, dan habit yang ditautkan (boleh kosong).
- Waktu mulai bisa **jam tetap** (06.35) atau **berpatokan waktu sholat** dengan selisih
  menit (Subuh − 15). Waktu sholat dihitung offline dengan metode Ephemeris Al Hasib, sama
  dengan Al-Kaukaba (lihat `docs/visi-super-app.md`).
- Blok diatur sendiri di layar **Atur jadwal**. Jadwal awal diisi dari tabel di
  `docs/concept.md` tahap 17.
- Blok **tidak** dicentang. Kalau blok ditautkan ke habit, tombol "Sudah" di notifikasinya
  mencentang habit itu. Aturan level dan streak tidak berubah.
- **Sekarang** adalah blok aktif yang paling akhir dimulai. Kalau blok sholat jatuh di
  tengah blok kerja, Sekarang menampilkan blok sholat, lalu kembali ke blok kerja.
- **Hari ini libur**: tombol di dashboard yang mematikan blok yang hanya aktif di hari kerja
  (Senin sampai Jumat) untuk tanggal itu. Blok yang aktif setiap hari tetap jalan.
- **Notifikasi tetap** "Sekarang · Berikutnya" tampil dari blok pertama hari itu sampai
  batas tidur, lalu hilang. Bisa dimatikan dari Tentang.

## Alarm Subuh dan adzan (tahap 18)

- Alarm berbunyi di waktu blok Bangun (awal: Subuh − 15 menit), **setiap hari** termasuk
  akhir pekan dan hari libur. Bisa dimatikan untuk satu tanggal saja.
- Suara memakai nada alarm HP yang bisa dipilih. Volume naik perlahan.
- Tunda 5 menit, paling banyak 2 kali. Alarm berhenti sendiri setelah 10 menit.
- Pengingat adzan bisa dimatikan per waktu sholat. Default semua nyala.

## Daily scrum (tahap 15, dibatalkan)

> Aturan di bawah tidak jadi dipakai. Daily scrum akan dibangun dari follow-up kerja di
> tahap 19, lihat `docs/visi-super-app.md`. Bagian ini disimpan sampai tahap 19 diputuskan.

- Catatan pribadi per hari, disimpan lokal seperti data lain. Tidak ada akun dan tidak
  ada sinkron. Bisa dibagikan sebagai teks lewat share sheet Android.
- Satu daily scrum per tanggal, berisi tiga bagian: **Kemarin**, **Hari ini**,
  **Hambatan**. Ketiganya teks bebas, boleh kosong.
- Saat daily scrum hari ini dibuka pertama kali, isinya terisi otomatis lalu bisa diedit:
  - **Kemarin**: to-do yang selesai di hari terakhir sebelum hari ini yang punya to-do
    selesai atau daily scrum (misalnya Jumat untuk hari Senin). Label menyebut tanggalnya.
  - **Hari ini**: to-do hari ini, termasuk yang dipindah dari hari sebelumnya.
  - **Hambatan**: kosong.
  Isian otomatis hanya terjadi sekali. Setelah disimpan, perubahan to-do tidak lagi
  mengubah daily scrum.
- Daily scrum tidak mengubah to-do, dan tidak memengaruhi level hari maupun streak.
- Hanya daily scrum hari ini yang bisa diedit. Hari sebelumnya hanya bisa dibaca.
- **Pengingat**: opsional, diatur di layar Tentang (switch dan jam), awalnya mati.
  Notifikasi muncul sekali sehari di jam itu, hanya kalau daily scrum hari itu belum diisi.

## Level warna per hari

| Level | Syarat |
|---|---|
| 0 | Tidak ada habit yang dicentang |
| 1 | Kurang dari 50% habit selesai |
| 2 | 50% atau lebih habit selesai, tetapi belum semua |
| 3 | Semua habit selesai |
| 4 | Semua habit selesai **dan** minimal 2 to-do selesai |

- To-do tidak bisa menaikkan hari ke level 4 kalau habit belum lengkap.
- Hari tanpa to-do maksimal tetap level 3.

## Streak

- Streak adalah jumlah hari berturut-turut dengan **semua habit selesai** (level 3 atau 4).
- To-do tidak ikut dihitung di streak.
- Hari ini belum selesai tidak langsung memutus streak. Streak baru putus
  kalau hari kemarin tidak lengkap.

## Layar

1. **Hari ini** (sekaligus dashboard)
   - Kartu "Sekarang / Berikutnya" paling atas, dengan timeline jadwal hari ini yang bisa
     dibuka-tutup dan tombol "Hari ini libur" (tahap 17).
   - Header: tanggal, skor hari ini (contoh: "Habit 5/7 · To-do 2/5"), dan streak.
   - Bagian Habit ditampilkan dulu, lalu bagian To-do di bawahnya.
   - Tombol tambah to-do, dinonaktifkan jika sudah 5.
2. **Kontribusi**
   - Heatmap gabungan (gaya GitHub), warna dari level hari. Minimal 26 minggu, dan
     ditambah minggu sampai lebar kartu terisi, paling banyak 53 minggu (setahun).
     Sisa ruang dibagi rata di kiri dan kanan grid.
   - Heatmap per habit di bawahnya, dengan jumlah minggu yang sama.
   - Tap kotak mana pun untuk melihat detail hari itu. Tap di celah antar kotak dihitung
     ke kotak terdekat.
   - Tahan sebentar lalu geser di heatmap untuk menelusuri hari: tooltip di atas jari
     menunjukkan tanggal dan level, dan detail hari terbuka saat jari diangkat (tahap 16).
3. **Kelola habit**
   - Tambah, ubah nama, dan hapus habit.
4. **Tentang**
   - Nama app dan teks "Dibuat oleh Roziq Rizal".
   - Pengaturan pengingat daily scrum (tahap 15).
   - Pilihan tampilan: Sistem, Terang, atau Gelap. Default Sistem (mengikuti
     pengaturan perangkat). Pilihan disimpan di perangkat dan berlaku langsung.

## Palet warna

Warna, font, dan jarak ada di [docs/design/README.md](design/README.md). Level 0-4 memakai
gradasi sage dengan level 0 abu-abu netral. UI memakai Jetpack Compose (Material 3).

## Identitas

- Nama app: HabitFlow.
- Application ID: `com.roziqrizal.habitflow`.

## Data

- `habits`: id, nama, tanggal dibuat, urutan.
- `habit_entries`: id habit, tanggal (ISO `yyyy-MM-dd`). Satu baris = habit selesai di tanggal itu.
- `todos`: id, judul, tanggal, selesai (boolean), tanggal dibuat.

Semua disimpan lokal di perangkat lewat Room. Tidak ada akun dan server.

## Di luar versi ini

- Input angka untuk tekanan darah, gula darah, berat badan, dan lingkar perut.
- Notifikasi pengingat.
- Ekspor CSV.
- Sinkronisasi antar perangkat.
