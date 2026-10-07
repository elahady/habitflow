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
- Tidak ada input angka di habit. Berat badan dan tensi dicatat di fitur Kesehatan (tahap 21).
- Habit boleh punya sumber otomatis. Saat ini hanya satu: habit "8.000 langkah" (dulu
  "Jalan kaki 20 menit") tercentang otomatis dari Health Connect (tahap 21).

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
- Pengingat adzan bisa dimatikan per waktu sholat. Default semua nyala. Mematikannya menghentikan
  notifikasi blok yang mulai tepat di waktu sholat itu (misalnya "Sholat Dzuhur").
- Alarm adalah tingkat notifikasi sebuah blok, bukan hanya Bangun. Alarm tidak ikut "Hari ini libur".

## Follow-up kerja (tahap 19)

- Follow-up adalah catatan target kerja supaya tidak lupa ditindaklanjuti. Terpisah dari
  to-do pribadi: **tidak ada batas jumlah** dan **tidak memengaruhi level hari maupun streak**.
- Setiap follow-up punya judul (wajib), status, dan opsional: tanggal tindak lanjut, jam
  khusus, orang terkait, catatan.
- Status: **Inbox** (baru dicatat, belum dirapikan), **Aktif** (akan dikerjakan, punya
  tanggal), **Menunggu** (menunggu orang lain, punya tanggal cek ulang), **Selesai**.
- **Catat cepat**: tombol "Catat" di semua layar membuka satu kolom teks. Enter menyimpan ke
  Inbox tanpa memilih apa pun.
- **Daily scrum (08.00)**: menampilkan follow-up yang lewat tanggal, jatuh tempo hari ini,
  dipilih saat EOD kemarin, dan Menunggu yang perlu dicek ulang hari ini. Pilih yang
  dikerjakan hari ini.
- **EOD (16.00)**: beri status setiap follow-up hari ini (Selesai, Lanjut besok, Pindah
  tanggal, Menunggu), rapikan Inbox, dan tulis catatan EOD. Ringkasan bisa dibagikan lewat
  share sheet.
- Tidak ada notifikasi per follow-up, kecuali item yang diberi jam khusus.
- Orang terkait diketik bebas dengan saran nama yang pernah dipakai, dan bisa difilter.
- HabitFlow menggantikan task harian di Notion. Task lama tidak dimigrasi.

## Kesehatan (tahap 21)

- **Langkah** dibaca dari Health Connect (HP atau smartwatch). Target 8.000 langkah per hari.
  Saat tercapai, habit "8.000 langkah" tercentang otomatis. Habit itu tetap bisa dicentang
  manual.
- **Berat badan** dicatat kapan saja. Tinggi badan diisi sekali. BMI dan kategorinya mengikuti
  Kemenkes RI (kurus < 18,5, normal 18,5–25,0, gemuk > 25,0–27,0, obesitas > 27,0). Ada target
  berat (default batas atas BMI normal), sisa ke target, dan tren 4 minggu.
- **Tensi** dicatat sebagai sistolik/diastolik, dengan nadi dan catatan opsional. Kategori
  mengikuti PERHI/ESH. Kalau ≥ 180/110, app menampilkan saran tenang untuk istirahat, ukur
  ulang, dan menghubungi dokter bila tetap tinggi atau ada keluhan.
- Kategori adalah informasi, bukan diagnosis.
- Pengingat: berat setiap Senin pagi setelah bangun, tensi pagi setelah bangun dengan
  frekuensi yang bisa diatur (default mingguan). Keduanya bisa dimatikan.
- Kesehatan tidak mengubah aturan level, kecuali lewat centang otomatis habit langkah.

## Kalender (tahap 22)

- Acara berbeda dari blok jadwal: blok adalah rutinitas harian, acara adalah kejadian di
  tanggal tertentu atau berulang (misalnya meeting reguler setiap 2 minggu).
- Acara dibuat di HabitFlow dengan label **Kerja** atau **Pribadi**, dan bisa berulang: harian,
  setiap N minggu di hari tertentu, bulanan per tanggal atau per urutan hari, tahunan, dengan
  tanggal berakhir opsional. Satu kejadian bisa dilewati atau diubah sendiri.
- Acara dari kalender HP ikut tampil (hanya baca), dari kalender yang dipilih di pengaturan.
- Pengingat −15 menit (bisa diubah per acara) hanya untuk acara HabitFlow. Acara kalender HP
  tidak diberi notifikasi oleh HabitFlow.
- Acara hari ini masuk timeline dan Sekarang/Berikutnya. Acara Kerja tampil di Agenda tab
  Kerja, acara Pribadi di dashboard Hari ini.
- **Libur nasional** dan cuti bersama Indonesia otomatis mengaktifkan "Hari ini libur": blok
  dan acara Kerja mati, sholat, alarm Subuh, dan acara Pribadi tetap. Bisa dibatalkan per
  tanggal.
- Catatan cepat yang dibuat selama acara Kerja berlangsung tertaut ke acara itu.

## Asupan makan (tahap 23)

- Makan dicatat per waktu makan (sarapan, siang, malam, camilan) dengan pola **Isi Piringku**:
  komponen karbo, lauk, sayur, buah yang ada di piring, plus tanda gorengan dan manis. Teks
  catatan opsional. Tidak ada hitung kalori.
- Kopi dan minuman manis dihitung dengan tombol cepat, seperti air.
- Habit makan yang sudah ada dicentang otomatis saat batas tidur:
  - "Makan malam selesai 2-3 jam sebelum tidur" kalau makan malam dicatat paling lambat
    2 jam sebelum batas tidur.
  - "Tanpa gorengan atau camilan manis" kalau tidak ada catatan bertanda gorengan atau manis.
  - "Tanpa minuman manis" kalau minuman manis 0.
  - "Ngopi maksimal 2 gelas" kalau kopi paling banyak 2.
  Centang manual selalu menang. Kopi ke-3 memunculkan pesan tenang.
- Satu pengingat Info sebelum batas tidur kalau ada waktu makan yang belum dicatat.

## Asisten AI (tahap 24)

- Empat fitur: **ringkasan mingguan** (Minggu malam), **tanya jawab** atas data sendiri,
  **bantu EOD dan daily scrum**, dan **saran pola** yang tampil sesekali di dashboard.
- Model Claude Sonnet 5.5, dipanggil lewat server sendiri (tahap 19B), bukan dari HP.
- AI hanya membaca data. Usulan AI (misalnya status follow-up saat EOD) harus disetujui
  dulu sebelum disimpan.
- Semua data boleh dikirim ke AI, tapi hanya saat fitur AI dipakai.
- Batas biaya $5 per bulan. Saat hampir habis, tanya jawab dan saran pola berhenti sampai
  bulan berikutnya. Ringkasan mingguan dan bantu EOD tetap jalan dari anggaran cadangan.
- Fitur AI butuh internet. Tanpa internet, app tetap jalan seperti biasa.

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

## Sinkron ke server (tahap 19B)

- Fungsinya **backup dan pindah HP**, bukan akses dari web atau laptop. HP tetap sumber data dan app
  tetap bisa dipakai penuh tanpa internet.
- Arah satu jalur: HP mengirim **snapshot lengkap** (satu berkas JSON) ke server Laravel sendiri.
  Otomatis sekitar 5 menit setelah ada perubahan (hanya saat ada internet) dan sekali sehari, atau
  lewat tombol "Sinkron sekarang" di Tentang.
- Login memakai **token pribadi** yang dibuat sekali di server dan ditempel di Tentang, bersama alamat
  server (HTTPS). Tanpa akun.
- **Pulihkan dari server** hanya manual, di Tentang, dengan konfirmasi yang menyebut waktu snapshot.
  Pemulihan **mengganti seluruh data** di HP (habit, riwayat, to-do, jadwal, follow-up, EOD,
  pengaturan) dalam satu transaksi.
- Nada alarm dan tanggal alarm yang dimatikan sekali tidak ikut disinkron.
- Isi snapshot disimpan terenkripsi di server. Server menyimpan 14 snapshot terakhir.
- Kontrak API dan format snapshot ada di `docs/concept.md` tahap 19B.

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
   - Jumlah Inbox yang belum dirapikan dan follow-up lewat tanggal (tahap 19).
   - Kartu Langkah, Berat, dan Tensi dengan tombol catat (tahap 21).
   - Kartu Makan dan tombol cepat kopi dan minuman manis di kartu air (tahap 23).
   - Tombol tambah to-do, dinonaktifkan jika sudah 5.
2. **Kerja** (tahap 19)
   - Inbox, Lewat tanggal, Hari ini, Menunggu, Nanti. Mode daily scrum dan EOD.
   - Agenda 7 hari dan tampilan bulan (tahap 22).
3. **Progres** (dulu Kontribusi, diganti nama di tahap 21)
   - Heatmap gabungan (gaya GitHub), warna dari level hari. Minimal 26 minggu, dan
     ditambah minggu sampai lebar kartu terisi, paling banyak 53 minggu (setahun).
     Sisa ruang dibagi rata di kiri dan kanan grid.
   - Heatmap per habit di bawahnya, dengan jumlah minggu yang sama.
   - Tap kotak mana pun untuk melihat detail hari itu. Tap di celah antar kotak dihitung
     ke kotak terdekat.
   - Tahan sebentar lalu geser di heatmap untuk menelusuri hari: tooltip di atas jari
     menunjukkan tanggal dan level, dan detail hari terbuka saat jari diangkat (tahap 16).
4. **Kelola habit**
   - Tambah, ubah nama, dan hapus habit.
5. **Tentang**
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

Semua disimpan lokal di perangkat lewat Room. Tidak ada akun. Cadangan opsional ke server sendiri lewat
token pribadi (lihat Sinkron ke server).

## Di luar versi ini

- Input angka untuk tekanan darah, gula darah, berat badan, dan lingkar perut.
- Notifikasi pengingat.
- Ekspor CSV.
- Sinkronisasi dua arah antar perangkat (yang ada hanya cadangan satu arah ke server, tahap 19B).
