# Uji tertutup, Play Console, dan produksi (31M)

Tahap 31M di [concept.md](concept.md), Gerbang F dan H di [rilis-play-store.md](rilis-play-store.md). Dokumen ini adalah **bahan siap pakai**: draf jawaban formulir,
pesan undangan penguji, daftar pantau, dan skrip uji akhir. Dibuat 15 Oktober 2026.

> **Status: 31M BELUM BISA DIMULAI.** Hitungan 14 hari baru berjalan setelah penguji bisa mendaftar dan memakai app dari jalur uji Play Store, dan itu butuh hal-hal di bagian 1
> yang belum ada. Draf di sini tidak menggantikan bukti.
>
> **Tentang draf formulir:** bentuk formulir Play Console berubah dari waktu ke waktu dan tidak bisa saya buka dari sini. Jawaban di bagian 4 disusun dari inventaris data dan kode
> repo, **bukan dari formulir yang sedang terbuka**. Cocokkan tiap pertanyaan saat mengisi, dan kalau formulir menanyakan hal yang tidak ada di sini, jangan menebak: tambahkan ke
> inventaris dulu.

## 1. Apa yang menahan 31M (jalur kritis)

Penguji harus bisa **mendaftar sendiri dan memakai app dari Play Store** selama 14 hari berturut-turut. Satu per satu, urut dari yang menentukan:

| # | Syarat sebelum mengundang penguji | Keadaan 15 Oktober 2026 | Siapa |
|---|---|---|---|
| 1 | Akun pengembang Play Console aktif dan terverifikasi | Tidak diketahui dari repo | Pemilik |
| 2 | Kunci unggah dibuat (dua salinan), Play App Signing aktif, AAB rilis ditandatangani lulus `cek-semua.ps1` | Belum: `keystore.properties` belum ada (31L) | Pemilik membuat kunci; AAB bisa dibuat setelahnya |
| 3 | Server menjalankan kode 31B-31E: migrasi, antrean email (`habitflow-queue`), snapshot per akun | Kode selesai, **belum di-deploy**, belum diuji `curl` ke produksi | Deploy: pemilik atau sesi dengan akses SSH |
| 4 | Email sungguhan sampai: domain pengirim, DNS (SPF, DKIM, DMARC), kunci Resend; uji ke Gmail dan satu penyedia lain, di kotak masuk | Belum (31B) | Pemilik (DNS dan akun Resend) |
| 5 | Google Sign-In produksi: layar persetujuan OAuth ke status produksi, client ID Android dengan SHA-1 kunci Play App Signing | Belum (31L, Gerbang E) | Pemilik (Google Cloud Console dan Play Console) |
| 6 | Kontak privasi terisi di kebijakan, teks hukum selesai, halaman hapus akun final | **`PRIVACY_CONTACT_EMAIL` kosong** (bagian 2) | Pemilik |
| 7 | Backup berjalan di VM dan satu latihan pemulihan lulus (31K, wajib sebelum penguji diundang) | Skrip siap, **belum dipasang** | Pemilik memberi tujuan backup dan akun ntfy/Healthchecks |
| 8 | Satu uji menyeluruh di HP fisik yang bukan HP pengembangan (bagian 6) | Belum | Pemilik |
| 9 | 15-25 orang yang mau jadi penguji, dengan akun Google dan HP Android | Belum | Pemilik |

Yang bisa dikerjakan tanpa menunggu: bagian 2 sampai 7 dokumen ini, dan tiga keputusan di bagian 3.

## 2. Temuan yang harus beres sebelum rilis

Ditemukan saat menyusun dokumen ini. Semuanya tentang kebijakan privasi, yang dibaca peninjau Play dan penguji.

1. **Kebijakan tayang tanpa kontak.** `privacy.blade.php` hanya menampilkan alamat kontak kalau `PRIVACY_CONTACT_EMAIL` terisi, dan di `.env.production` lokal kunci itu **kosong** (periksa juga di VM). Play
   dan UU PDP mensyaratkan kontak pengendali data. Isi dengan alamat yang benar-benar Anda baca (bukan email pribadi utama kalau tidak mau, tapi harus dibaca).
2. **`OWNER_EMAIL` juga kosong.** `CountSentMail` hanya mengirim peringatan "email hampir habis (80%)" kalau `OWNER_EMAIL` terisi. Sekarang peringatan itu tidak punya tujuan, jadi
   di `operasional-server.md` kalimat "peringatan 80% sudah ada" hanya benar setelah variabel ini diisi.
3. **Kebijakan menjanjikan hal yang belum benar.** Bagian 5: "Cadangan harian database disimpan terenkripsi di penyimpanan terpisah di luar server dan dihapus otomatis setelah
   paling lama 30 hari." Itu baru benar setelah 31K terpasang dan terbukti. Jangan tayangkan versi kebijakan itu ke penguji sebelum benar, atau ubah kalimatnya sementara.
4. **Lokasi tidak lengkap.** Bagian 2 menyebut lokasi sebagai "nama dan koordinat kota yang kamu pilih ... Bukan lokasi real-time". Kode (`LocationSettings.kt`) juga membaca **lokasi kasar perangkat**
   (`ACCESS_COARSE_LOCATION`) untuk mengisi kota. Tambahkan kalimat: *"Kalau kamu mengizinkan, aplikasi membaca lokasi perkiraan HP sekali untuk menentukan kotamu. Yang disimpan hanya nama kota dan koordinatnya."*
   (periksa dulu bahwa yang disimpan memang hanya itu).
5. **Usia.** Kebijakan: "tidak ditujukan untuk anak di bawah 13 tahun". Play Console target audiens "bukan anak-anak", dan UU PDP memperlakukan data anak secara khusus (di Indonesia anak umumnya di bawah 18 tahun).
   Usulan: tetapkan **18+** di Play Console dan samakan kalimat kebijakan. ☐ Konfirmasi ke ahli hukum bersama pertanyaan Pasal 46 di `insiden-data.md`.

Perubahan teks kebijakan menaikkan `PRIVACY_POLICY_VERSION` (pengguna yang sudah setuju mungkin perlu setuju ulang; cek perilaku di 31C sebelum menaikkannya setelah ada pengguna).

## 3. Tiga keputusan sebelum build uji pertama

**Biner yang diuji 14 hari harus sama dengan yang dirilis** (aturan di 31L untuk R8, berlaku untuk manifest juga). Mengubah izin sesudah uji dimulai berarti membangun ulang dan meragukan hasil uji.
Jadi putuskan ini **sebelum** AAB pertama diunggah.

**A. `USE_EXACT_ALARM` atau `SCHEDULE_EXACT_ALARM` saja?**
Play hanya mengizinkan `USE_EXACT_ALARM` untuk app yang fungsi intinya alarm, timer, atau kalender. Habitflow fungsi intinya pelacak habit; alarm Subuh dan adzan adalah fitur di dalamnya. Deklarasi bisa ditolak, dan
penolakan setelah uji 14 hari berarti manifest berubah dan uji diulang. Sekarang ada jalan cadangan (31L: `ExactAlarmAccess`, tombol "Izinkan alarm tepat waktu" di Pengaturan).
**Rekomendasi: kirim build uji tanpa `USE_EXACT_ALARM`, hanya `SCHEDULE_EXACT_ALARM`** (pengguna memberi izin sekali lewat Pengaturan). Harganya satu langkah tambahan bagi pengguna untuk alarm Subuh; keuntungannya tidak ada risiko
penolakan deklarasi di tengah jalan. Kalau tetap memilih `USE_EXACT_ALARM`, ubah `scripts/izin-disetujui.txt` dan isi alasan di bagian 4.5 dengan jujur. ☐ Pilihan pemilik: ______

**B. `USE_FULL_SCREEN_INTENT` (layar alarm di atas layar kunci).**
Di Android 14 ke atas hanya untuk app panggilan dan alarm, dan butuh deklarasi; jalan cadangan (notifikasi heads-up) **belum dikerjakan** (31L). Dua pilihan: (1) kerjakan jalan cadangannya lalu hapus izin ini dari build uji,
atau (2) pertahankan dan deklarasikan. **Rekomendasi: (1)**, alasan yang sama dengan A. ☐ Pilihan pemilik: ______

**C. Usia dan kebijakan** (bagian 2 poin 5). ☐ Pilihan pemilik: ______

## 4. Draf jawaban Play Console

Semuanya draf. Cocokkan dengan formulir yang terbuka (lihat catatan di atas).

### 4.1 Data safety

Dasar: [rilis-play-store.md](rilis-play-store.md) bagian 1. Semua data dikirim lewat HTTPS. Pengguna bisa menghapus semuanya (halaman web dan app). Tidak ada iklan, penjualan data, atau SDK analitik di dalam app.

| Kategori Play | Jenis | Dikumpulkan | Dibagikan ke pihak ketiga | Wajib atau opsional | Tujuan |
|---|---|---|---|---|---|
| Info pribadi | Nama | Ya | Tidak | Wajib | Fungsi app, manajemen akun |
| Info pribadi | Alamat email | Ya | Tidak (lihat catatan) | Wajib | Fungsi app, manajemen akun, kode verifikasi |
| Info pribadi | ID pengguna (ID akun Google, ID akun) | Ya | Tidak | Wajib | Manajemen akun |
| Kesehatan dan kebugaran | Info kesehatan (berat, tinggi, tekanan darah) | Ya | Tidak | Opsional | Fungsi app |
| Kesehatan dan kebugaran | Info kebugaran (langkah, catatan makan) | Ya | Tidak | Opsional | Fungsi app |
| Lokasi | Lokasi perkiraan | Ya | Tidak | Opsional | Fungsi app (waktu sholat) |
| Aktivitas aplikasi | Konten buatan pengguna lain (habit, to-do, follow-up, catatan) | Ya | Tidak | Wajib | Fungsi app |
| Kalender | Acara kalender | Ya | Tidak | Opsional | Fungsi app (jadwal) |
| Info dan kinerja app | Laporan crash | **Isi sesuai kenyataan**: ya hanya kalau ada pelaporan crash di build; Android vitals dari Play dikumpulkan Google | Tidak | n/a | Analitik |

Catatan:
- **Email dan Resend/Brevo**: layanan pengirim memproses email atas nama kita. Formulir Play membedakan "berbagi" dan "diproses atas nama pengembang"; **verifikasi pengecualian ini di formulir terbaru** sebelum menjawab "Tidak dibagikan".
- **Cloudflare** meneruskan lalu lintas HTTPS; sama, cek perlakuannya di formulir.
- Data **dienkripsi dalam perjalanan**: ya. **Pengguna bisa meminta penghapusan**: ya, URL hapus akun adalah `https://habitflow.roziqrizal.com/hapus-akun` (rute terverifikasi di `routes/web.php`).
- Kategori "Kalender" dan "Info kesehatan" menarik pertanyaan tambahan; jawab jujur dan konsisten dengan kebijakan privasi (bagian 2 dokumen ini).
- Data pihak lain: nama orang di follow-up dan nama anggota tim berasal dari pengguna; sebutkan di kebijakan (sudah ada) dan jangan mengklaim "tidak mengumpulkan data orang lain".

### 4.2 Deklarasi aplikasi kesehatan

- Fitur kesehatan: ya. Jenis: pencatatan kebugaran dan kesehatan mandiri (berat, tinggi, tekanan darah, langkah, makan), pengingat, dan ringkasan harian.
- **Bukan perangkat medis, tidak mendiagnosis, tidak memberi saran pengobatan.** Teks "bukan diagnosis" ada di app dan kebijakan (Gerbang C). Tidak ada klaim medis di deskripsi.
- Data tidak dipakai untuk iklan atau dijual.

### 4.3 Deklarasi Health Connect

- Izin: `READ_STEPS` dan `READ_HEALTH_DATA_IN_BACKGROUND`. Hanya membaca, tidak menulis.
- Alasan `READ_STEPS`: menampilkan langkah harian dan menghitung progres habit serta fokus kesehatan harian.
- Alasan latar belakang: agar progres langkah di notifikasi dan jadwal tetap terbarui tanpa pengguna membuka app. **☐ Cocokkan dengan kode sebelum mengirim**: cari di mana `READ_HEALTH_DATA_IN_BACKGROUND` dipakai (worker) dan tulis
  alasan persis fitur itu; peninjau Health Connect membandingkan alasan dengan perilaku app.
- Data dikirim ke server pengembang (snapshot terenkripsi) untuk sinkron dan cadangan; tertulis di kebijakan dan Data safety. Tidak dipakai untuk iklan, tidak dijual.
- Halaman kebijakan privasi harus memuat penjelasan Health Connect (bagian 2 baris Kesehatan sudah menyebutnya).

### 4.4 App access (untuk peninjau)

Pilih "semua atau sebagian fungsi dibatasi akses". Isi: email dan kata sandi akun peninjau (dibuat dengan `php artisan habitflow:make-reviewer-account <email>` di produksi), instruksi singkat:
*"Masuk dengan email dan kata sandi di layar Masuk (bukan Google). Akun sudah terverifikasi dan berisi data contoh. Fitur Health Connect, kalender, dan lokasi bersifat opsional dan meminta izin saat dinyalakan."*
Jangan menghapus akun itu selama app ditinjau, dan ingat bahwa akun ini ada di produksi (data contoh saja, tanpa data pribadi nyata).

### 4.5 Izin sensitif

Isi **hanya izin yang tersisa setelah keputusan 3.A dan 3.B**. Alasan jujur per izin:

| Izin | Alasan yang boleh ditulis |
|---|---|
| `SCHEDULE_EXACT_ALARM` (atau `USE_EXACT_ALARM` kalau dipilih) | "Pengguna menyalakan alarm Subuh dan pengingat adzan sendiri; alarm harus berbunyi tepat waktu, dan terlambat sampai satu jam membuatnya tidak berguna." Jangan menulis "fungsi inti app adalah alarm" kalau bukan. |
| Layanan foreground tipe `mediaPlayback` | "Memutar suara alarm Subuh atau adzan yang dinyalakan pengguna, sampai dimatikan." |
| `POST_NOTIFICATIONS` | Pengingat habit, jadwal, dan alarm. Minta saat pengguna menyalakan fitur terkait. |
| `ACCESS_COARSE_LOCATION` | Menentukan kota untuk waktu sholat; hanya perkiraan, sekali, tidak berkelanjutan. |
| `READ_CALENDAR` | Menampilkan acara kalender HP di jadwal; opsional, diminta saat fitur dinyalakan. |
| `RECEIVE_BOOT_COMPLETED` | Menjadwalkan ulang alarm setelah HP restart (hanya bekerja kalau ada sesi masuk). |
| Health Connect | Lihat 4.3. |

### 4.6 Peringkat konten, audiens, kategori

- **Target audiens: 18+ (usulan, bagian 2 poin 5), bukan anak-anak.** Tidak menarik bagi anak.
- Peringkat konten: kuesioner Play; jawaban kemungkinan "tidak ada" untuk kekerasan, seksual, judi, dan narkoba. Fitur sosial: tim rumah berbagi to-do dan nama (isi di bagian interaksi pengguna/berbagi data pribadi). Jawab sesuai kuesioner terbaru.
- Kategori: **Produktivitas** (usulan). Hindari "Kesehatan & kebugaran" kalau tidak perlu, karena menarik pemeriksaan tambahan; tetapi deklarasi kesehatan tetap wajib karena ada fitur kesehatan.
- Aset: ikon `docs/design/ikon/ikon-512.png` (512x512 sudah ada), tangkapan layar di `docs/screenshots/` (hari-ini, kontribusi, habit; ambil ulang dari build rilis dan tambah layar Masuk dan Profil).

### 4.7 Daftar toko (tanpa klaim medis)

- **Nama (maks. 30 karakter):** `Habitflow: Habit Harian`
- **Deskripsi singkat (maks. 80):** `Catat habit dan to-do harian, lihat konsistensimu seperti heatmap kontribusi GitHub.`
- **Deskripsi lengkap (draf; sesuaikan dengan fitur yang benar-benar ada di build):**

> Habitflow membantu kamu menjaga kebiasaan harian dan menyelesaikan to-do kecil, dengan tampilan kontribusi seperti heatmap GitHub.
>
> • **Hari ini**: centang habit dan sampai 5 to-do. Level hari dan streak langsung terlihat.
> • **Kontribusi**: heatmap gabungan dan per habit, streak sekarang dan terpanjang.
> • **Jadwal harian dan waktu sholat**: blok jadwal, pengingat, serta alarm Subuh dan pengingat adzan (opsional).
> • **Follow-up kerja**: catat hal yang perlu ditanyakan atau dikirim ke orang lain.
> • **Catatan kebugaran**: catat berat badan, tekanan darah, dan langkah (dari Health Connect, jika kamu izinkan) sebagai pencatatan pribadi.
> • **Tim rumah**: bagi to-do dengan pasangan atau keluarga.
> • Bisa dipakai tanpa internet; data disinkronkan ke akunmu saat ada koneksi.
>
> Habitflow bukan alat diagnosis atau saran medis. Angka yang tampil hanya ringkasan dari data yang kamu masukkan.
> Kamu bisa menghapus akun dan seluruh datamu kapan saja dari aplikasi atau situs web.

## 5. Mengundang penguji

**Mekanik** (verifikasi di Play Console): buat jalur Uji tertutup, tambahkan penguji lewat daftar email atau Google Group, bagikan **tautan ikut uji** (penguji harus membukanya dan menekan "Jadi penguji", baru bisa
memasang dari Play Store). Penguji butuh akun Google dan HP Android; 12 harus tetap bergabung selama 14 hari, jadi undang **15 sampai 25** dan jangan hanya 12.

**Pesan undangan (salin):**

> Halo <nama>! Aku lagi menguji aplikasi **Habitflow** (pelacak habit dan to-do harian) sebelum rilis di Play Store, dan butuh bantuanmu jadi penguji selama **14 hari berturut-turut**.
> Yang kubutuhkan: buka aplikasinya **sekali sehari** (centang satu habit sudah cukup) dan kabari kalau ada yang aneh.
>
> 1. Buka tautan ini di HP Android-mu dan tekan **Jadi penguji**: <tautan>
> 2. Pasang Habitflow dari Play Store lewat tautan yang sama.
> 3. Daftar dengan email (kode verifikasi dikirim ke emailmu) atau masuk dengan Google.
>
> Kalau ada masalah atau saran, kabari lewat <WhatsApp/email>, sertakan: jenis HP, apa yang kamu lakukan, dan tangkapan layar. Data kamu bisa dihapus kapan saja lewat Profil > Hapus akun.
> Penting: kalau kamu keluar dari uji sebelum 14 hari, kabari aku ya, supaya aku bisa cari pengganti. Terima kasih banyak!

Setelah hari ke-7, kirim pengingat singkat. Penguji yang berhenti memakai app membuat jumlah aktif turun; pantau bagian 7.

## 6. Skrip uji akhir (Gerbang H)

Di HP fisik yang **bukan HP pengembangan**, dipasang dari jalur uji Play Store (bukan adb). Catat tanggal, HP, versi Android, dan hasil tiap baris di rilis-play-store.md kolom Bukti.

| # | Langkah | Hasil yang diharapkan |
|---|---|---|
| 1 | Pasang dari tautan uji | App terpasang dan terbuka di layar Masuk |
| 2 | Daftar dengan email baru | Kode 6 angka sampai di **kotak masuk** (bukan spam) dalam 1 menit |
| 3 | Salah ketik kode 5 kali | Kode hangus, bisa kirim ulang setelah jeda |
| 4 | Masukkan kode benar | Masuk ke Hari ini; centang satu habit dan satu to-do |
| 5 | Mode pesawat, centang dua habit lagi, tutup dan buka app | Perubahan tetap ada (offline) |
| 6 | Matikan mode pesawat | Sinkron berhasil; di situs web (login akun yang sama) data muncul |
| 7 | Nyalakan alarm Subuh untuk 2 menit lagi, kunci layar | Berbunyi tepat waktu (dan layar alarm sesuai keputusan 3.A dan 3.B) |
| 8 | Profil > Keluar | Data lokal hilang, kembali ke layar Masuk, tidak ada alarm atau notifikasi tersisa |
| 9 | Masuk dengan **akun lain** (Google) | Tidak ada data akun pertama yang tampil |
| 10 | Dari web ganti kata sandi akun pertama | Sesi di HP yang masih masuk ditolak (kembali ke Masuk) |
| 11 | Profil > Hapus akun (akun kedua) | Akun hilang; coba masuk lagi gagal; halaman web hapus akun menampilkan alurnya |
| 12 | Lupa kata sandi untuk email tidak terdaftar | Jawaban sama dengan email terdaftar (tidak membocorkan) |

## 7. Pantau selama 14 hari

Cek **setiap hari** (5 menit); catat di tabel. Aturan keras: kalau penguji aktif < 12 dua hari berturut-turut, cari pengganti **hari itu juga** karena hitungan bisa mengulang dari nol.

| Tanggal | Hari ke- | Penguji aktif | Crash baru (Android vitals) | Gagal daftar/verifikasi | Email terkirim / batas | Backup terakhir OK | Catatan |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

Sumber: Play Console (statistik penguji, Android vitals), `docker logs habitflow-queue` dan `failed_jobs`, dashboard Resend, pesan ntfy/Healthchecks dari `monitor.sh`.
Perbaikan bug lewat **build uji baru** (versionCode naik) ke penguji yang sama; jangan mengganti daftar penguji. Perubahan manifest atau izin = uji ulang bagian 6 dan catat.

## 8. Produksi dan sesudahnya

1. Ajukan produksi **hanya setelah semua gerbang A-H berstatus `[x]` dengan bukti** (cek dengan hitungan di rilis-play-store.md; per 15 Oktober 2026: 0 dari 8 gerbang selesai).
2. Rilis bertahap: **10%, 50%, 100%**, dengan jeda minimal 2 hari di tiap tahap. Di tiap jeda: tingkat crash, ulasan, antrean email, dan backup. Hentikan peningkatan dan jangan lanjut kalau ada crash baru atau pendaftaran yang gagal.
3. Pasca rilis: permintaan hapus akun ditangani otomatis oleh halaman; latihan pemulihan bulanan (`restore-drill.sh`); pembaruan keamanan bulanan ([operasional-server.md](operasional-server.md) bagian 7);
   tinjau kapasitas email (batas Resend 100 per hari) begitu pengguna bertambah, dan siap pindah ke Brevo atau paket berbayar lewat `.env`.
4. Setiap pembaruan app berikutnya menjalankan `scripts/cek-semua.ps1` dan, kalau izin atau data berubah, memperbarui inventaris di rilis-play-store.md di commit yang sama.
