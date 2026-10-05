# Visi: HabitFlow sebagai asisten harian pribadi

Ditulis 5 Oktober 2026 dari diskusi dengan Roziq. Dokumen ini arah besar. Aturan fitur
yang sudah diputuskan tetap ditulis di `docs/rancangan.md`, dan tahapan pembangunan di
`docs/concept.md`. Kalau ada konflik, `rancangan.md` yang menang.

## Tujuan

Satu app pribadi, seperti Jarvis bagi Iron Man, yang tahu **sedang di blok apa sekarang
dan apa yang berikutnya**, dari bangun tidur sampai tidur lagi. App ini mengingatkan di
waktu yang tepat, mencatat target kerja supaya tidak ada follow-up yang terlupa, dan
memantau target hidup sehat.

HabitFlow yang sudah ada **dikembangkan**, bukan diganti app baru. Habit, to-do, heatmap,
desain, dan datanya tetap dipakai. Nama app bisa ditinjau nanti.

## Prinsip

1. **Catat dalam 2 detik.** Topik baru harus bisa dicatat seketika, tanpa memilih kategori,
   tanggal, atau layar dulu. Merapikan dilakukan belakangan.
2. **Sedikit notifikasi yang berarti**, bukan banyak notifikasi yang diabaikan. Lihat
   bagian Notifikasi.
3. **Offline dulu.** Semua fitur inti jalan tanpa internet. HP adalah sumber data. Data
   disinkron ke server sendiri (tahap 19B) untuk backup dan pindah HP, saat ada internet.
   Fitur yang butuh internet (AI) bersifat opsional.
4. **Tenang, bukan memarahi.** Prinsip desain yang sudah ada tetap berlaku.
5. **Habit tetap pusat.** Jadwal, kesehatan, dan kerja terhubung ke habit yang sudah ada,
   misalnya langkah 8.000 mencentang "Jalan kaki" dan 8 gelas mencentang "Air putih 2 liter".

## Jadwal harian (template hari kerja)

| Waktu | Blok | Patokan |
|---|---|---|
| Subuh − 15 mnt | Bangun, **alarm** | Waktu Subuh |
| Subuh | Jamaah Subuh dan ngaji, 1 jam | Waktu Subuh |
| s.d. 06.00 | Aktivitas fisik | Jam tetap |
| 06.15 | Mandi dan prepare | Jam tetap |
| 06.35 | Berangkat ke kantor | Jam tetap |
| 08.00 | Mulai kerja, buka daily scrum | Jam tetap |
| 08.00–12.00 | Kerja blok pagi, pengingat air dan break | Jam tetap |
| Dzuhur | Sholat Dzuhur | Waktu sholat |
| 12.00 | Makan siang dan istirahat, 1 jam | Jam tetap |
| 13.00–16.00 | Kerja blok sore, pengingat air dan break | Jam tetap |
| Ashar | Sholat Ashar | Waktu sholat |
| 16.00 | EOD: update follow-up, siapkan topik besok | Jam tetap |
| 17.00 | Pulang | Jam tetap |
| Maghrib | Sholat Maghrib | Waktu sholat |
| 18.00–19.00 | Sampai rumah | Jam tetap |
| Isya | Sholat Isya | Waktu sholat |
| s.d. 21.00 | Project personal | Jam tetap |
| 22.00 | Batas akhir tidur | Jam tetap |

Blok bisa berpatokan ke **jam tetap** atau ke **waktu sholat** (dengan selisih menit). Semua
waktu sholat masuk jadwal, bukan hanya Subuh. Template akhir pekan belum dibahas.

## Waktu sholat

- Mesin hitung: **Ephemeris (Al Hasib – Alkaukaba Team)**, sama dengan metode default app
  Al-Kaukaba. Subuh −20°, Isya −18°, Maghrib −1°, Dhuha 4,5°, Imsak −22°, Ashar dari
  `cotan h = tan|φ−δ| + 1`, ikhtiyat 2 menit (ditambah, dikurangi untuk Terbit, nol untuk
  Imsak). Rujukan: `alkaukabaandroid/docs/features/rumus-hisab-ephemeris.md` dan
  `EphemerisPrayerCalculator.kt`.
- Dihitung **offline** di HP. Posisi matahari dari Astronomy Engine (MIT), dipasang
  sebagai dependency, bukan disalin.
- Catatan: app Al-Kaukaba sendiri saat ini menampilkan jam dari Aladhan API metode 20
  (Kemenag) sebagai pengganti sementara. Hasil HabitFlow divalidasi terhadap jadwal itu
  untuk beberapa tanggal, dan selisihnya dicatat.
- **Lokasi**: GPS secara default (izin lokasi kasar), bisa diganti kota atau koordinat
  manual. Lokasi terakhir disimpan supaya tetap jalan tanpa GPS.

## Notifikasi

Tiga tingkat, ditambah satu notifikasi tetap.

| Tingkat | Contoh | Perilaku |
|---|---|---|
| **Alarm** | Subuh − 15 | Bunyi penuh, layar penuh, menembus mode senyap. Hanya untuk ini |
| **Pengingat** | Adzan, berangkat, mulai kerja, EOD, meeting −15 mnt, batas tidur | Heads-up dengan getar. Tombol Sudah, Tunda 10 mnt, Lewati |
| **Info** | Minum air, break | Tanpa bunyi dan tanpa pop-up. Hanya memperbarui notifikasi tetap |

- **Notifikasi tetap** di status bar: "Sekarang: … · Berikutnya: …". Ini juga menjadi
  ringkasan dashboard di luar app.
- Pengingat yang jatuh berdekatan (selisih 15 menit atau kurang) digabung jadi satu.
- Selama blok sholat atau meeting, pengingat tingkat Info ditahan.
- Pengingat air yang diabaikan 3 kali berturut-turut berhenti sampai blok berikutnya.
- **Minum air setiap 60 menit** di jam kerja (±7 kali), dengan tombol "Sudah minum" yang
  menambah hitungan gelas. 8 gelas mencentang habit "Air putih 2 liter".
- **Break setiap 90 menit** di jam kerja. Kalau dekat jadwal minum, digabung jadi
  "Break + minum".

## Kerja: catat cepat, follow-up, daily scrum

Kebutuhan inti: mencatat semua target kerja supaya tidak lupa follow-up, dan mencatat
topik baru seketika tanpa menunda. **HabitFlow menggantikan jurnal task harian di Notion.**
Rinciannya di tahap 19 dan 19B.

- **Inbox catat cepat.** Satu kolom teks, simpan dengan Enter. Versi pertama: **tombol +
  di semua layar** yang membuka bottom sheet. Jalur lain (balas dari notifikasi, share dari
  app lain, widget, Quick Settings) dipertimbangkan nanti.
- **Follow-up kerja dipisah dari to-do pribadi.**
  - Follow-up: tanpa batas jumlah, bisa diberi tanggal tindak lanjut (dengan pengingat)
    dan orang terkait. Tidak memengaruhi level hari.
  - To-do pribadi: tetap seperti sekarang (maksimal 5 per hari, ikut menentukan level 4).
  - Follow-up yang lewat tanggal tampil mencolok di dashboard.
- **Daily scrum** dibangun dari follow-up:
  - 08.00: pilih follow-up yang dikerjakan hari ini.
  - 16.00 EOD: tandai yang selesai, pindahkan yang belum, rapikan Inbox jadi topik besok.
  - Besok pagi, "Hari ini" sudah terisi dari topik EOD.
  - Tetap pribadi, bisa dibagikan sebagai teks lewat share sheet.

## Kesehatan

- **Langkah**: dari Health Connect (data HP atau smartwatch). Target 8.000 langkah per hari
  ditonjolkan di dashboard, dan mencentang habit "Jalan kaki" secara otomatis.
- **Berat badan dan BMI**: catat berkala, tinggi badan diisi sekali. Grafik tren dan
  rentang BMI sehat.
- **Tensi**: catat sistolik dan diastolik, dengan kategori dan grafik tren.
- **Asupan makan**: mulai sederhana (catat makan dan porsi ala "Isi Piringku"), bukan
  hitung kalori.

## Kalender

- Acara berulang, misalnya meeting reguler setiap 2 minggu, dengan pengingat −15 menit.
- Kalender HP (misalnya Google Calendar kantor) dipertimbangkan untuk ikut dibaca.

## Dashboard

Layar utama yang menjawab "sekarang apa, berikutnya apa":

```
Sekarang   Kerja · blok pagi       sisa 35 mnt
Berikutnya Break 10.30 · Minum air 10.00
────────────────────────────────────────
Langkah  3.240 / 8.000      Air  3 / 8 gelas
Habit    4 / 9 · Level 1    Tensi terakhir 128/84
Follow-up lewat tanggal: 2
Hari ini: Meeting reguler 14.00 (2 mingguan)
Subuh besok 04.12 · alarm 03.57
```

## Asisten AI ("Jarvis")

Paling akhir dan opsional karena butuh internet: ringkasan mingguan, saran pola, dan
tanya jawab atas data sendiri.

## Urutan pengerjaan

Rinciannya di `docs/concept.md` tahap 17 dan seterusnya. Setiap tahap didiskusikan dulu
sebelum dikoding.

1. Jadwal harian, dashboard "sekarang dan berikutnya", dan notifikasi tetap.
2. Waktu sholat Ephemeris, lokasi, dan alarm Subuh − 15.
3. Inbox catat cepat, follow-up kerja, daily scrum dan EOD (menggantikan tahap 15 lama).
   Lalu sinkron ke server sendiri (tahap 19B), sebelum cut off Notion.
4. Pengingat kerja: air dan break.
5. Kesehatan: langkah, berat badan dan BMI, tensi.
6. Kalender dan acara rutin.
7. Asupan makan.
8. Asisten AI.

## Pertanyaan terbuka

- Jadwal akhir pekan dan hari libur.
- Nama app setelah berkembang.
- Apakah blok jadwal bisa ditandai selesai, dan apakah memengaruhi level hari.
- Kategori tensi mengikuti pedoman mana.
- Perangkat langkah: HP saja atau ada smartwatch.
