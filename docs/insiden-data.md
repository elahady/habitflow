# Prosedur insiden kebocoran data

Tahap 31K, Gerbang C dan D di [rilis-play-store.md](rilis-play-store.md). Ditulis **sebelum** dibutuhkan, supaya orang yang paling panik (Anda, di malam hari)
tidak harus membaca kode. Pasangan teknisnya: [operasional-server.md](operasional-server.md).

**Jam 3x24 dimulai sejak pemilik mengetahui adanya kegagalan pelindungan data, bukan sejak kebocoran terjadi.** Catat jam itu (T0) di baris pertama catatan insiden (bagian 7).

## 0. Yang perlu diketahui sebelum membaca

- **Penilai dan pengambil keputusan: pemilik (Roziq Rizal).** Tidak ada orang lain; kalau tidak terjangkau dalam 12 jam, tidak ada yang bisa menilai, jadi ☐ tunjuk satu orang
  tepercaya (pasangan) yang tahu di mana dokumen ini, kunci SSH, dan pengelola kata sandi.
- Kontak yang tercantum di [halaman kebijakan privasi](https://habitflow.roziqrizal.com/kebijakan-privasi) adalah alamat pengguna menghubungi Anda. Pantau kotak masuknya selama insiden.
- Data yang disimpan (rinci di `rilis-play-store.md` bagian 1): identitas (email, nama, hash kata sandi, `google_sub`), **data kesehatan** (berat, tensi, langkah, makan, di snapshot dan tabel),
  **lokasi** (di snapshot), aktivitas (habit, to-do, follow-up), dan nama orang lain di follow-up dan tim.
- Snapshot **terenkripsi** dengan `APP_KEY`: kebocoran berkas snapshot saja (tanpa kunci) tidak membuka isinya. Kebocoran `.env.production` **bersama** snapshot membukanya. Per 15 Oktober 2026
  data kesehatan, lokasi, dan makan **hanya** ada di snapshot (belum ada tabelnya), jadi kebocoran database saja tidak membuka data itu. Tabel MySQL (akun, habit, to-do, follow-up, jadwal, tim) berupa
  teks biasa: kebocoran database = kebocoran data itu. **Perbarui bagian ini saat tahap 29-30 membuat tabel kesehatan.**
- Kata sandi disimpan sebagai hash (hasher bawaan Laravel). Token sesi: yang tersimpan hanya hash.

## 1. Apakah ini insiden? (5 menit)

Anggap **ya** sampai terbukti bukan kalau salah satu terjadi: akses tidak sah ke VM, database, `.env.production`, backup, atau tujuan backup; kehilangan atau pencurian PC host, VM, ponsel
yang masuk ke server, atau media salinan rahasia; kunci SSH, token Cloudflare, atau kredensial Resend/Google terlihat pihak lain; akun pengguna diambil alih secara massal; pengguna melapor
melihat data orang lain; atau data dikirim ke pihak yang salah (email ke alamat salah, log berisi data pribadi tersebar).

Bukan insiden (cukup catat): satu pengguna lupa kata sandi, satu akun dibobol karena sandi lemah pengguna sendiri tanpa tanda masalah di sistem kami. Ragu berarti perlakukan sebagai insiden.

## 2. Menahan (dalam 1 jam; lakukan yang relevan, urut dari yang paling cepat)

Semua perintah dijalankan di VM lewat `ssh elahady@<ip-vm>`. Catat waktu setiap tindakan.

| Tindakan | Perintah / cara | Kapan |
|---|---|---|
| Putus semua sesi dan token | `docker exec habitflow php artisan habitflow:revoke-all-sessions --force` | Hampir selalu: akun diambil alih, token bocor, atau belum jelas cakupannya. Pengguna harus masuk ulang. |
| Putar `APP_KEY` | [operasional-server.md](operasional-server.md) bagian 5 | `.env.production`, backup, atau berkas snapshot diduga bocor |
| Ganti kata sandi database | Di `shared-mysql`: `ALTER USER 'habitflow'@'%' IDENTIFIED BY '<baru>';`, ubah `DB_PASSWORD` di `.env.production`, recreate container app dan queue | Kredensial database diduga bocor |
| Cabut kunci layanan email | Buat kunci baru di dashboard Resend, ganti `MAIL_PASSWORD`, hapus yang lama | Kunci Resend diduga bocor, atau email dipakai menyebar spam |
| Ganti rahasia Google | Console Google Cloud: buat client secret baru, ganti di `.env.production`, hapus yang lama | Secret OAuth bocor |
| Putus dari internet | `sudo systemctl stop cloudflared` (situs offline, data aman di VM) | Pelaku masih aktif atau cakupan tidak jelas; harga: layanan mati |
| Mode perawatan | `docker exec habitflow php artisan down --secret=<kata-rahasia>` | Perlu menutup aplikasi tetapi tetap bisa menguji sendiri |
| Ganti akses SSH | Hapus kunci yang dicurigai dari `~/.ssh/authorized_keys`, buat kunci baru | Kunci SSH atau PC pemilik diduga disusupi |
| Amankan backup | Cabut akses/ganti kunci layanan tujuan backup (rclone), ganti `~/.habitflow-backup-pass` untuk backup berikutnya | Tujuan backup diduga bocor |

Setelah menahan, **jangan menghapus bukti**: jangan `docker system prune`, jangan menimpa log, jangan menghapus akun mencurigakan sebelum dicatat. Salin log sebelum recreate container
(`docker logs habitflow > ~/insiden-<tanggal>-app.log`, `docker logs habitflow-queue`, dan `docker cp habitflow:/var/www/html/storage/logs ~/insiden-<tanggal>-laravel`).

## 3. Memastikan cakupan (hari 1)

Tujuannya menjawab tiga pertanyaan yang diminta pemberitahuan: **data apa**, **milik siapa**, **kapan dan bagaimana**. Yang ada untuk ditelusuri (jangan berasumsi ada yang lain):

- Log container (`docker logs habitflow`) dan `storage/logs/laravel.log`. Aplikasi sengaja **tidak** mencatat isi permintaan (ada tes penjaga), jadi log menunjukkan *apa yang diakses*, bukan isinya.
- `user_tokens.last_used_at` dan `created_at`: token mana yang aktif pada rentang waktu mencurigakan, milik akun mana. `users.updated_at`, `users.email_verified_at`, `account_deletions`.
- Log SSH VM: `last -a`, `sudo journalctl -u ssh --since "<tanggal>"`; log tunnel: `sudo journalctl -u cloudflared`; dashboard Cloudflare (analitik dan log keamanan domain).
- Dashboard Resend (email apa yang dikirim), Google Cloud (penggunaan client OAuth).
- Daftar berkas dan waktu ubah di `storage/app/private/snapshots`, dan riwayat backup di tujuan (siapa mengunduh, kalau layanan tujuan mencatatnya).

Putuskan dan tulis: (a) kategori data yang terdampak (identitas / kesehatan / lokasi / aktivitas / orang lain), (b) jumlah dan identitas pengguna terdampak (atau "semua" kalau tidak bisa dibatasi),
(c) apakah data bisa dibaca pihak yang mengakses (snapshot terenkripsi tanpa kunci: tidak; tabel: ya), (d) kapan mulai dan berakhir, (e) apakah masih berlangsung.

## 4. Wajib memberitahu?

UU Pelindungan Data Pribadi (UU No. 27 Tahun 2022) Pasal 46 mewajibkan pengendali data memberi tahu **secara tertulis paling lambat 3 x 24 jam** kepada **subjek data (pengguna)** dan **lembaga
pelindungan data pribadi** bila ada kegagalan pelindungan data pribadi (data terungkap, hilang, berubah, rusak, atau diakses tanpa izin); pada kasus tertentu juga kepada masyarakat. Isi minimalnya:
data yang terungkap, kapan dan bagaimana terungkap, dan upaya penanganan serta pemulihan.

☐ **Verifikasi sebelum 31M** (penyusun dokumen tidak bisa memastikannya dari sini): (1) teks resmi Pasal 46 dan peraturan pelaksananya yang berlaku saat ini; (2) lembaga mana yang menerima laporan
dan bagaimana caranya. Pada pengetahuan penyusun, UU ini mengamanatkan sebuah lembaga PDP yang pembentukannya mengikuti peraturan turunan; kalau lembaga itu belum aktif, tanyakan ke Kementerian
Komunikasi dan Digital kanal pelaporan yang berlaku. Tulis hasil verifikasi dan tanggalnya di sini: ______. **Konsultasikan juga ke ahli hukum** sebelum 31M; dokumen ini bukan nasihat hukum.

Aturan praktis sampai verifikasi selesai: **kalau ada data pribadi pengguna yang mungkin terbaca pihak tak berwenang, anggap wajib memberitahu.** Kalau pasti hanya data terenkripsi tanpa kunci
yang bocor dan kunci aman, catat pertimbangan itu tertulis; keputusan tidak memberitahu harus bisa dipertanggungjawabkan.

## 5. Memberitahu (paling lambat T0 + 72 jam; target T0 + 48 jam)

Kirim lewat email ke alamat terdaftar tiap pengguna terdampak (dari `users.email`; kirim lewat Resend dalam batch kecil sesuai batas harian, atau lewat Gmail pribadi kalau jumlahnya kecil),
dan taruh pengumuman di halaman kebijakan privasi/beranda. Tulis jujur dan singkat. Jangan menyalahkan pengguna dan jangan menyebut hal yang belum pasti sebagai pasti.

### Templat untuk pengguna

> **Judul:** Pemberitahuan penting tentang keamanan data akun Habitflow Anda
>
> Halo <nama>,
>
> Pada <tanggal dan jam> kami mengetahui bahwa <apa yang terjadi, satu kalimat jelas>. Kami memberi tahu Anda karena data Anda termasuk yang terdampak.
>
> **Data yang terdampak:** <contoh: alamat email dan nama; data kesehatan yang Anda simpan (berat, tekanan darah, langkah) / hanya salinan data terenkripsi yang tidak dapat dibaca tanpa kunci>.
> **Kapan dan bagaimana:** <rentang waktu dan penyebab, sejauh yang sudah kami pastikan>.
> **Yang sudah kami lakukan:** <contoh: seluruh sesi masuk dicabut; kunci enkripsi dan kredensial diganti; akses penyusup ditutup pada <waktu>>.
> **Yang perlu Anda lakukan:** masuk kembali ke aplikasi (Anda akan diminta masuk ulang). Ganti kata sandi Habitflow Anda, dan jika Anda memakai kata sandi yang sama di layanan lain, ganti juga di sana.
> Waspadai email atau pesan yang mengaku dari Habitflow dan meminta kata sandi atau kode; kami tidak pernah memintanya.
> **Hak Anda:** Anda dapat meminta informasi lebih lanjut, mengoreksi, atau menghapus akun dan data Anda kapan saja (Pengaturan > Profil > Hapus akun, atau <tautan hapus akun>).
>
> Kami menyesali kejadian ini. Pertanyaan: <kontak pada kebijakan privasi>. Kami akan memberi kabar lanjutan di <tempat> paling lambat <tanggal>.
>
> Roziq Rizal, pengendali data Habitflow

### Templat untuk lembaga pengawas

> **Kepada:** <lembaga PDP / kanal yang diverifikasi di bagian 4>
> **Perihal:** Pemberitahuan kegagalan pelindungan data pribadi, layanan Habitflow
>
> 1. **Pengendali data:** Roziq Rizal, <alamat/kontak>, layanan Habitflow (aplikasi Android dan web habitflow.roziqrizal.com).
> 2. **Waktu diketahui (T0):** <tanggal, jam, zona waktu>. **Waktu perkiraan terjadi:** <rentang>. **Pemberitahuan ini dikirim:** <jam>, <N> jam sejak T0.
> 3. **Apa yang terjadi dan bagaimana terungkap:** <uraian teknis singkat>.
> 4. **Data pribadi yang terungkap:** kategori <identitas / kesehatan / lokasi / aktivitas / nama pihak lain>; perkiraan jumlah subjek data <N>; apakah terbaca atau terenkripsi: <...>.
> 5. **Dampak yang mungkin:** <contoh: penyalahgunaan identitas, pengungkapan data kesehatan>.
> 6. **Upaya penanganan dan pemulihan:** <tindakan menahan dan waktunya; perbaikan akar masalah; rencana pencegahan>.
> 7. **Pemberitahuan kepada subjek data:** dikirim pada <waktu> lewat <email>.
> 8. **Kontak untuk tindak lanjut:** <nama, email, telepon>.

## 6. Setelah itu

1. **Pulihkan:** perbaiki akar masalah, tambal, buka kembali layanan (`php artisan up`, `sudo systemctl start cloudflared`), dan pantau 72 jam (`monitor.sh` tetap berjalan).
2. **Kalau data hilang atau rusak:** pemulihan dari backup di [operasional-server.md](operasional-server.md) bagian 3.
3. **Tinjauan tertulis dalam 7 hari:** apa yang terjadi, mengapa tidak tertangkap lebih awal, apa yang diubah. Masukkan perbaikan sebagai tugas di `concept.md`.
4. Perbarui inventaris data dan dokumen ini kalau ada celah yang ditemukan.

## 7. Catatan insiden (salin satu blok per kejadian)

```
Insiden:            <judul>
T0 (diketahui):     <tanggal jam>           Dilaporkan oleh: <siapa/apa>
Penilai:            <nama>                  Dinilai sebagai insiden: ya / tidak, alasannya: ...
Tindakan menahan:   <jam> <tindakan>, <jam> <tindakan>
Cakupan:            data: ...  subjek terdampak: ...  terbaca atau terenkripsi: ...  masih berlangsung: ...
Wajib memberitahu:  ya / tidak, dasar pertimbangan: ...
Pemberitahuan:      pengguna <jam> ; lembaga <jam> ; (T0 + <jam>)
Pemulihan:          ...
Tinjauan (<=7 hari): ...
```

## 8. Latihan

☐ Sebelum 31M, jalankan **latihan di atas kertas** dengan skenario "kunci SSH PC dicuri" dan "`.env.production` terkirim ke repo publik": ikuti dokumen ini langkah demi langkah tanpa membuka kode, catat apa yang
membingungkan, dan perbaiki dokumen. Jalankan juga `habitflow:revoke-all-sessions` di salinan lokal/uji untuk memastikan Anda tahu efeknya (semua orang harus masuk ulang). Tanggal latihan: ______
