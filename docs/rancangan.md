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

1. **Hari ini**
   - Header: tanggal, skor hari ini (contoh: "Habit 5/7 · To-do 2/5"), dan streak.
   - Bagian Habit ditampilkan dulu, lalu bagian To-do di bawahnya.
   - Tombol tambah to-do, dinonaktifkan jika sudah 5.
2. **Kontribusi**
   - Heatmap gabungan (gaya GitHub), warna dari level hari. Minimal 26 minggu, dan
     ditambah minggu sampai lebar kartu terisi, paling banyak 53 minggu (setahun).
     Sisa ruang dibagi rata di kiri dan kanan grid.
   - Heatmap per habit di bawahnya, dengan jumlah minggu yang sama.
   - Tap kotak mana pun untuk melihat detail hari itu.
3. **Kelola habit**
   - Tambah, ubah nama, dan hapus habit.
4. **Tentang**
   - Nama app dan teks "Dibuat oleh Roziq Rizal".

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
