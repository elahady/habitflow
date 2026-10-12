# Gerbang Rilis Play Store

Written for: Roziq, sebagai daftar periksa sebelum Habitflow dirilis ke publik di Google Play. Dibuat 11 Oktober 2026 dalam
rangka [tahap 31 (Login Wajib)](concept.md). Syarat Google dan hukum dicek ke sumber resmi pada tanggal itu dan **harus dicek
ulang saat rilis** karena sering berubah (lihat bagian Sumber).

## Cara memakai dokumen ini

Tidak ada rencana yang bisa menjamin nol celah. Yang bisa dijamin adalah tiga hal:

1. **Setiap risiko punya satu pemilik, satu bukti, dan satu tempat dicek.** Tidak ada item "ingat sendiri".
2. **Kesalahan yang paling mungkin tidak sengaja dibuat dijaga oleh tes yang gagal** (bagian Penjaga otomatis), bukan oleh ingatan.
3. **Rilis berjalan lewat gerbang berurutan.** Gerbang berikutnya tidak dibuka sebelum bukti gerbang sebelumnya ada. Status tiap
   item dicatat di sini (ganti `[ ]` jadi `[x]` dan isi kolom Bukti), sama seperti [concept.md](concept.md).

Batas dokumen ini: ia hanya sebaik inventaris data di bawah. **Setiap kali ada tabel, kolom, izin, atau SDK baru, inventaris
diperbarui di commit yang sama.** Itu satu-satunya aturan yang tidak bisa diganti tes.

Pemetaan ke pekerjaan: gerbang dan penjaga di sini dikerjakan lewat sub-tahap 31A sampai 31M di [concept.md](concept.md): penjaga server di 31B, 31D, 31E; penjaga Android di 31H dan 31J; Gerbang A di 31B sampai 31I; Gerbang B di 31B; Gerbang C di 31C dan 31M; Gerbang D di 31K; Gerbang E, G di 31L; Gerbang F, H di 31M.

## 1. Inventaris data (sumber tunggal)

Dipakai untuk tiga hal sekaligus: isi kebijakan privasi, jawaban formulir Data safety, dan daftar yang harus terhapus saat akun
dihapus. Kalau salah satu dari ketiganya berbeda dari tabel ini, tabel ini yang benar dan yang lain diperbaiki.

### Server (MySQL, `habitflow.roziqrizal.com`)

| Tabel | Isi | Sensitif | Dihapus saat hapus akun |
|---|---|---|---|
| `users` | nama, email, hash kata sandi, `google_sub`, avatar, waktu verifikasi email, waktu dan versi persetujuan kebijakan privasi | Identitas | Ya |
| `email_codes` | hash kode verifikasi dan reset (6 angka, berlaku 10 menit), jumlah percobaan | Kredensial | Ya |
| `jobs`, `failed_jobs` | antrean email. Berisi kode asli selama email belum terkirim (menit), dan kode asli bisa tertinggal di `failed_jobs` kalau pengiriman gagal | Kredensial, sementara | Dibersihkan otomatis saat terkirim. `failed_jobs` dibersihkan berkala (31K) |
| `user_tokens` | hash token sesi per perangkat | Kredensial | Ya |
| `api_tokens` | token per-HP tahap 19B | Kredensial | Dipensiunkan di tahap 31, lalu tabelnya dihapus |
| `snapshots` | seluruh data yang belum dimigrasi (jadwal, kesehatan, makan, kalender, pengaturan, EOD), terenkripsi dengan kunci app | **Kesehatan, lokasi** | Ya, termasuk 14 salinan terakhir dan berkasnya (sejak 31D, lewat `snapshots.user_id`; snapshot lama dari token 19B terikat ke akun lewat `habitflow:claim-legacy` di 31E, satu kali per HP) |
| `account_deletions` | waktu dan sumber penghapusan akun (`app`, `web`, `web-code`), tanpa id, nama, atau email | Bukan data pengguna | Tidak (bukti penghapusan) |
| `habits`, `habit_entries`, `todos` | habit, riwayat centang, to-do harian | Aktivitas | Ya |
| `follow_ups` | follow-up kerja (judul, orang, catatan) | Aktivitas, nama orang lain | Ya |
| `teams`, `team_members`, `team_invites`, `team_todos` | tim tugas rumah dan isinya | Aktivitas, melibatkan orang lain | **Aturan khusus, lihat Keputusan** |
| Rencana tahap 29 dan 30: `weight_entries`, `blood_pressure_entries`, `step_days`, `health_focus`, jadwal, `work_days`, makan, acara | berat, tensi, langkah, jadwal | **Kesehatan** | Ya. Wajib masuk daftar hapus **sebelum** tabelnya dibuat |

### Android (HP)

| Lokasi | Isi | Dihapus saat Keluar |
|---|---|---|
| Room `habitflow.db` (18 entitas, versi 10) | semua data di atas dalam bentuk lokal, termasuk berat, tensi, makan, kalender | Ya |
| `settings.xml` | tema, lokasi (nama dan koordinat), pengingat, tinggi dan target berat | Ya |
| `account.xml` | token sesi dan profil akun | Ya |
| `sync.xml` | alamat server dan token 19B | Dipensiunkan di tahap 31 |
| Izin yang diminta | lokasi perkiraan, kalender (baca), notifikasi, Health Connect (langkah, baca di latar belakang), alarm pasti, layar penuh | Izin tetap ada di sistem, tapi app berhenti memakainya |

## 2. Keputusan yang mengikat dokumen ini

Diputuskan pemilik 11 Oktober 2026 kecuali ditandai lain.

| Pertanyaan | Keputusan |
|---|---|
| Layanan email | **Resend gratis**, Brevo gratis sebagai cadangan, tanpa server email sendiri (bagian 6) |
| Verifikasi email | **Wajib** untuk akun email dan kata sandi. Kode 6 angka yang diketik di app, berlaku 10 menit, sekali pakai, disimpan sebagai hash, dengan batas kirim ulang. Akun Google dianggap terverifikasi (Google sudah memverifikasi email) |
| Penautan Google ke akun email | Hanya ke akun yang emailnya **sudah terverifikasi**. Akun email yang belum terverifikasi tidak pernah ditautkan, karena itu celah pengambilalihan akun |
| Lupa kata sandi | Kode 6 angka lewat email, pola yang sama. Jawaban server **sama** apakah email terdaftar atau tidak |
| Sesi | Tidak kedaluwarsa. Ganti kata sandi mencabut semua sesi lain. Ada tombol "Keluar dari semua perangkat" di Profil |
| Tim saat pemilik menghapus akun (disetujui pemilik 11 Oktober 2026) | Kalau ada anggota lain, kepemilikan pindah ke anggota terlama dan to-do yang dibuat pemilik tetap ada dengan nama "Pengguna dihapus". Kalau tidak ada anggota lain, tim dihapus. Anggota biasa yang menghapus akun hanya keluar dari tim |
| Penghapusan | Langsung dan permanen dalam satu transaksi, tanpa masa tunggu. Salinan di backup server hilang saat backup itu berputar (usulan 30 hari), dan kebijakan privasi menyebutnya |

## 3. Penjaga otomatis (yang membuat kesalahan tidak sengaja gagal)

Ini yang paling penting. Masing-masing berupa tes yang **harus lulus sebelum deploy dan sebelum build rilis**. Kalau
sebuah tes tidak bisa ditulis, itu tandanya aturannya belum jelas, bukan alasan untuk melewatinya.

**Server (`php artisan test`, wajib lulus sebelum deploy, dicatat di `server/DEPLOY.md`):**

| Penjaga | Mencegah |
|---|---|
| **Tes inventaris skema**: membaca semua tabel dari database, dan gagal kalau ada tabel yang tidak ada di daftar "dihapus saat hapus akun" maupun di daftar "bukan data pengguna, beserta alasannya" | Menambah tabel baru (misalnya berat dan tensi di tahap 30) lalu lupa menghapusnya saat hapus akun |
| **Tes hapus akun menyeluruh**: membuat pengguna dengan baris di setiap tabel, menghapus akun, lalu memastikan **nol baris** tersisa di semua tabel, termasuk snapshot dan token | Data yang tertinggal setelah hapus akun |
| **Tes kasus tim**: pemilik dengan dan tanpa anggota lain, anggota biasa | Tim yatim atau data orang lain ikut terhapus |
| **Tes pengambilalihan akun**: daftar email tanpa verifikasi, lalu login Google dengan email itu, hasilnya tidak ditautkan | Celah pre-hijack |
| **Tes kode verifikasi**: kedaluwarsa, sekali pakai, salah 5 kali menghanguskan kode, batas kirim ulang, disimpan sebagai hash | Kode ditebak atau dipakai ulang |
| **Tes pencabutan sesi**: Keluar, Keluar dari semua perangkat, ganti kata sandi, hapus akun semuanya membuat token lama menjawab 401 | Sesi yang masih hidup setelah seharusnya mati |
| **Tes tanpa bocor**: jawaban lupa kata sandi dan login gagal identik untuk email terdaftar dan tidak terdaftar | Enumerasi akun |
| **Tes log**: permintaan berisi berat, tensi, atau snapshot tidak masuk log aplikasi | Data kesehatan di berkas log |

**Android (unit test di `app/src/test`):**

| Penjaga | Mencegah |
|---|---|
| Tes Keluar: setelah Keluar, database dan semua `SharedPreferences` kosong, dan semua pekerjaan terjadwal dibatalkan | Data akun lama tersisa di HP |
| Tes ganti akun: login akun B setelah akun A keluar tidak menampilkan data A | Kebocoran antar akun |
| Tes gerbang: tanpa sesi, tidak ada alarm, notifikasi, worker, atau receiver boot yang terjadwal | App berjalan di latar belakang sebelum login |
| Tes Keluar saat ada perubahan belum terkirim: jumlahnya dihitung benar dan konfirmasi muncul | Kehilangan data tanpa peringatan |

**Skrip cek rilis (`scripts/cek-rilis`, dijalankan sebelum mengunggah AAB; gagal berarti jangan unggah):**

| Pemeriksaan | Alasan |
|---|---|
| `debuggable` mati, dan `src/debug` (receiver debug) tidak ada di manifest rilis | Receiver debug membuka jalan masuk tanpa login |
| `usesCleartextTraffic` mati dan alamat server `https://` | Data kesehatan tidak boleh lewat HTTP |
| Daftar izin di manifest rilis **sama persis** dengan daftar yang disetujui di bagian 5 | Izin baru yang terselip, atau izin yang tidak dideklarasikan di Play Console |
| `targetSdk` memenuhi syarat Play saat itu | Unggahan ditolak. **Sekarang 34, hampir pasti di bawah syarat untuk rilis baru**, cek angka terbaru |
| `backup_rules.xml` dan `data_extraction_rules.xml` tidak memuat `habitflow.db` dan `settings.xml` | Database akun lama terpulihkan ke HP lain |
| Client ID Google bukan nilai uji, dan tidak ada kunci rahasia di repo | Login Google gagal di rilis, atau rahasia bocor |
| `versionCode` lebih besar dari yang terakhir diunggah | Unggahan ditolak |

## 4. Gerbang rilis

Urutan wajib. Kolom **Bukti** diisi saat item selesai (tautan, tangkapan layar, atau hasil perintah).

### Gerbang A. Fondasi akun (server dan Android) - tahap 31 langkah 2-8
- [ ] Semua tes di bagian 3 ada dan lulus. Bukti:
- [ ] Verifikasi email dan lupa kata sandi jalan end-to-end di HP fisik dengan email sungguhan. Bukti:
- [ ] Hapus akun di app dan di web (halaman publik yang bisa dibuka tanpa login ke app) jalan dan terbukti menghapus semua tabel. Bukti:
- [ ] "Keluar dari semua perangkat" dan ganti kata sandi diuji dengan dua HP atau HP dan web. Bukti:

### Gerbang B. Email
Email menjadi **jalur kritis**: tanpa email, orang baru tidak bisa daftar, tidak bisa verifikasi, dan tidak bisa reset kata sandi.
- [x] Layanan pengirim dipilih: **Resend gratis, Brevo gratis sebagai cadangan** (bagian 6, diputuskan 11 Oktober 2026). Bukti: keputusan di [concept.md](concept.md) tahap 31
- [ ] Domain pengirim diverifikasi: **SPF, DKIM (2048 bit), dan DMARC** lulus, dicek dengan alat uji email. Bukti:
- [ ] Email dikirim lewat **antrean** (queue) supaya pendaftaran tidak macet kalau layanan email lambat, dan gagal kirim tercatat. Bukti:
- [ ] Batas kirim per email dan per IP, supaya tidak bisa dipakai membanjiri kotak orang lain. Bukti:
- [ ] Jumlah email terkirim per hari dihitung, dan pemilik diberi tahu saat mencapai 80% batas harian paket. Bukti:
- [ ] Email uji sampai di Gmail dan satu penyedia lain **di kotak masuk, bukan spam**. Bukti:

### Gerbang C. Privasi dan hukum
- [ ] Kebijakan privasi publik (bukan PDF, URL aktif) memuat: data yang dikumpulkan (sesuai inventaris), tujuan, penyimpanan di server di
  Indonesia atau tidak (isi sesuai kenyataan), masa penyimpanan dan backup, hak hapus dan cara memintanya, kontak pemilik. Bukti:
- [ ] Teks persetujuan di layar Daftar menyebut data kesehatan secara khusus, dan persetujuan tercatat (waktu dan versi kebijakan). Bukti:
- [ ] Ada **nama dan kontak yang bertanggung jawab** atas data pengguna di kebijakan privasi. Bukti:
- [ ] Ada **catatan prosedur kebocoran data**: siapa yang menilai, cara mencabut semua sesi dan mengganti kunci, dan templat pemberitahuan ke
  pengguna dan lembaga pengawas. UU PDP mewajibkan pemberitahuan paling lambat 3x24 jam. Prosedur ditulis sebelum dibutuhkan. Bukti:
- [ ] Teks "bukan diagnosis" ada di semua tempat yang menampilkan kategori BMI dan tensi, dan tidak ada klaim medis di deskripsi Play Store. Bukti:

### Gerbang D. Operasional server
Semua pengguna bergantung pada satu VM. Gerbang ini mencegah satu kesalahan menghapus data semua orang.
- [ ] **Backup MySQL otomatis harian ke lokasi di luar VM**, terenkripsi, dengan rotasi (usulan 30 hari). Bukti:
- [ ] **Latihan pemulihan**: backup dipulihkan ke database kosong dan datanya diperiksa. Backup yang belum pernah dipulihkan dianggap belum ada. Bukti:
- [ ] Pemantauan: pemberitahuan ke pemilik kalau situs mati, disk hampir penuh, atau backup gagal. Bukti:
- [ ] Rahasia (`.env.production`, kunci app yang mengenkripsi snapshot) disimpan di luar repo **dan** ada salinan aman terpisah. Kunci app hilang berarti
  semua snapshot tidak bisa dibuka. Bukti:
- [ ] Enkripsi disk VM atau keputusan tertulis kenapa tidak. Data kesehatan di tabel MySQL berupa teks biasa. Bukti:
- [ ] Pembaruan keamanan PHP, Laravel, dan image Docker dijadwalkan. Bukti:
- [ ] Akses admin ke database dibatasi dan tidak dibuka ke internet. Bukti:

### Gerbang E. Google Sign-In untuk publik
Sekarang layar persetujuan OAuth berstatus **uji** dengan daftar penguji (Roziq dan istri). Di status itu, orang lain tidak bisa login Google.
- [ ] Layar persetujuan OAuth dipindah ke **produksi**, dengan nama app, logo, tautan kebijakan privasi, dan domain resmi. Bukti:
- [ ] **Client ID Android memakai sidik jari SHA-1 dari kunci Play App Signing** (bukan hanya kunci debug atau kunci unggah). Tanpa ini login Google berhasil
  di build uji tapi gagal di app dari Play Store. Bukti:
- [ ] Login Google diuji dari build yang diinstal dari jalur uji Play Store, bukan dari adb. Bukti:

### Gerbang F. Play Console
- [ ] Akun pengembang diverifikasi. **Akun pribadi baru wajib uji tertutup dengan minimal 12 penguji aktif selama 14 hari berturut-turut sebelum boleh
  mengajukan produksi**, dan Google memeriksa bahwa penguji benar-benar memakai app. Hitungan 14 hari mengulang dari nol kalau penguji turun di bawah 12.
  **Ini jalur terpanjang dan tidak bisa dipercepat, jadi mulai secepat build pertama yang layak diuji.** Bukti:
- [ ] Formulir **Data safety** diisi dari inventaris bagian 1: data kesehatan dan kebugaran, email, nama, lokasi perkiraan, aktivitas, kalender. Dikirim lewat HTTPS,
  bisa dihapus pengguna. Bukti:
- [ ] Formulir **Health apps declaration** dan deklarasi **Health Connect** (langkah dan baca di latar belakang) diisi, dengan alasan tiap tipe data. Bukti:
- [ ] **URL hapus akun** diisi di Play Console (halaman web, bagian Gerbang A). Bukti:
- [ ] **Akses app untuk peninjau**: akun uji yang **sudah terverifikasi dan permanen**, dengan kata sandinya, diisi di bagian App access. Tanpa ini peninjau
  tertahan di layar Masuk dan app ditolak. Akun itu berisi data contoh. Bukti:
- [ ] Izin sensitif dideklarasikan dan alasannya jujur (lihat bagian 5). Bukti:
- [ ] Peringkat konten, target audiens (bukan anak-anak), dan kategori app diisi. Bukti:
- [ ] Ikon, tangkapan layar, deskripsi tanpa klaim medis. Bukti:

### Gerbang G. Penandatanganan
- [ ] Memakai **Play App Signing**. Kunci unggah (upload keystore) dibuat, dan **dua salinan di tempat berbeda** disimpan bersama kata sandinya. Kehilangan kunci
  unggah memaksa proses reset lewat Google. Catatan saat ini: belum ada keystore rilis. Bukti:
- [ ] Build rilis (AAB) menjalankan skrip cek rilis dan lulus. Bukti:

### Gerbang H. Verifikasi akhir
- [ ] Pasang dari jalur uji tertutup di HP fisik **yang bukan HP pengembangan**, lalu: daftar, verifikasi email, pakai tanpa internet, sinkron, Keluar, login akun lain,
  hapus akun. Bukti:
- [ ] Semua item di atas berstatus `[x]` dengan bukti. Baru ajukan produksi.

## 5. Izin dan deklarasi yang harus dibenarkan

Hasil pemeriksaan manifest 11 Oktober 2026. Kolom terakhir adalah risiko penolakan atau pertanyaan peninjau.

| Izin | Dipakai untuk | Catatan untuk Play Console |
|---|---|---|
| `ACCESS_COARSE_LOCATION` | Waktu sholat dari lokasi | Data safety: lokasi perkiraan. Disimpan di pengaturan dan ikut snapshot ke server, jadi **dikumpulkan**, bukan hanya diproses di HP |
| `READ_CALENDAR` | Acara kalender HP di jadwal | Data safety: kalender. Minta izin hanya saat fitur dinyalakan, dengan penjelasan sebelum dialog sistem |
| `POST_NOTIFICATIONS` | Pengingat | Biasa |
| `USE_EXACT_ALARM` | Alarm Subuh dan adzan | **Dibatasi Google** untuk app yang fungsi utamanya alarm atau kalender. Perlu deklarasi dan pembenaran. Kalau ditolak, ganti `SCHEDULE_EXACT_ALARM` dengan izin yang diminta pengguna |
| `USE_FULL_SCREEN_INTENT` | Layar alarm di atas layar kunci | **Dibatasi** untuk app alarm dan panggilan. Perlu deklarasi. Risiko penolakan sedang |
| `FOREGROUND_SERVICE` dan `..._MEDIA_PLAYBACK` | Bunyi alarm | Tipe layanan foreground perlu dideklarasikan di Play Console |
| `RECEIVE_BOOT_COMPLETED` | Menjadwalkan ulang alarm setelah restart | Setelah tahap 31, hanya boleh bekerja kalau ada sesi |
| `health.READ_STEPS` dan `health.READ_HEALTH_DATA_IN_BACKGROUND` | Langkah untuk habit dan fokus | Deklarasi Health Connect. Data **dikirim ke server** (tahap 30), jadi harus tertulis di kebijakan privasi dan Data safety. Dilarang dipakai untuk iklan atau dijual |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Sinkron | Biasa |

Kalau satu izin dibatasi dan ditolak, fitur terkait harus tetap bisa **berjalan tanpa izin itu** (misalnya alarm lewat notifikasi biasa). Itu jalan keluar yang harus ada
sebelum rilis, bukan sesudah ditolak.

## 6. Email: bangun sendiri atau layanan pengirim

Pertanyaan pemilik: bagaimana kalau membangun mail hosting sendiri. **Rekomendasi: jangan untuk mengirim.** Alasannya, dari yang diketahui tentang kondisi di sini:

| Faktor | Server email sendiri di VM rumah | Layanan pengirim |
|---|---|---|
| Port 25 keluar | Umumnya diblokir ISP rumahan | Tidak relevan |
| IP dan reverse DNS (PTR) | IP rumahan dinamis dan biasanya tanpa PTR yang bisa diatur, dan penerima menganggap IP rumahan mencurigakan | IP dan reputasi dikelola penyedia |
| Cloudflare Tunnel (yang dipakai VM sekarang) | Hanya membawa lalu lintas web, bukan SMTP publik | Tidak relevan |
| Reputasi | Dimulai dari nol, dan satu kali salah konfigurasi membuat email ke Gmail masuk spam | Sudah dibangun |
| Kalau email gagal | **Tidak ada yang bisa daftar, verifikasi, atau reset kata sandi, jadi seluruh app tidak bisa dipakai pengguna baru** | Penyedia punya tim yang menangani |
| Kerja berkelanjutan | Pemantauan antrean, bounce, blacklist, pembaruan keamanan | Hampir nol |

Kalau tetap ingin memiliki infrastruktur sendiri, jalur yang masuk akal: **hanya mengirim** (Postfix dan OpenDKIM, tanpa kotak masuk) dari VPS dengan IP tetap dan PTR yang bisa
diatur, bukan dari VM rumah, dan tetap memerlukan SPF, DKIM, DMARC dan masa pemanasan reputasi. Itu pekerjaan operasional yang berkelanjutan untuk satu fitur kecil.

**Batas gratis (dicek 11 Oktober 2026, cek ulang saat memilih):**

| Layanan | Gratis | Catatan |
|---|---|---|
| Resend | 3.000 email per bulan, **maksimal 100 per hari**, 1 domain, log 30 hari | Gratis tanpa batas waktu. Berbayar mulai $20 per bulan (50.000 email, tanpa batas harian). Integrasi Laravel paling ringkas |
| Brevo | **300 email per hari** (sekitar 9.000 per bulan), SMTP dan API | Gratis tanpa batas waktu. Berbayar mulai sekitar $9 per bulan. Dipakai lewat SMTP biasa |
| Amazon SES | 3.000 email per bulan **hanya 12 bulan pertama** untuk pelanggan AWS baru, setelah itu bayar per email | Bukan gratis jangka panjang. Skema harganya berubah per Juli 2026 dan syarat gratisnya bergantung tanggal akun AWS. Pengaturan awal paling rumit |

Satu pengguna baru memakai sekitar 1 email (kode verifikasi), ditambah 1 setiap lupa kata sandi. Batas harian Resend (100) cukup untuk tahap uji tertutup dan awal rilis,
tapi **kalau batas harian terlewati, pendaftaran baru gagal**. Karena itu:
- Server mencatat jumlah email terkirim per hari dan memberi tahu pemilik saat mencapai 80% batas (bagian Gerbang B dan D).
- Pindah penyedia hanya mengubah `.env`, jadi pindah ke Brevo (batas harian 3x lebih besar) atau ke paket berbayar bisa dilakukan tanpa mengubah kode.

**Diputuskan pemilik 11 Oktober 2026: Resend gratis, dengan Brevo gratis sebagai cadangan.** Kode Laravel tidak terikat penyedia, jadi pindah hanya mengubah `.env`. Amazon SES ditolak karena gratisnya hanya 12 bulan.

## 7. Jalur kritis dan urutan waktu

1. **Uji tertutup 14 hari** adalah satu-satunya hal yang tidak bisa dipercepat. Mulai begitu build dengan login dan Keluar-hapus-data stabil di HP fisik,
   jangan menunggu semua gerbang selesai. Siapkan 15 sampai 25 penguji nyata, bukan hanya 12.
2. Gerbang B (email) dan E (Google Sign-In produksi) harus selesai **sebelum** penguji diundang, karena penguji harus bisa daftar sendiri.
3. Gerbang D (backup dan latihan pemulihan) harus selesai **sebelum** ada pengguna selain pemilik dan istri.

## Sumber (dicek 11 Oktober 2026)

- Syarat uji tertutup akun pengembang pribadi: https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026 (ringkasan pihak ketiga; cek ke Play Console Help)
- Syarat hapus akun: https://support.google.com/googleplay/android-developer/answer/13327111
- App kesehatan dan Health Connect: https://developer.android.com/health-and-fitness/health-connect/publish
- UU PDP (pemberitahuan kebocoran 3x24 jam): https://www.hukumonline.com/berita/a/4-langkah-penting-perusahaan-mengatasi-kebocoran-data-pribadi-lt66e0125375343
- Syarat pengirim email (SPF, DKIM, DMARC): https://redsift.com/blog/gmails-enforcement-ramps-up-what-bulk-senders-need-to-know

Hal yang **tidak** diverifikasi di sini dan harus dicek langsung saat rilis: angka `targetSdk` minimum, daftar izin yang dibatasi dan cara deklarasinya, bentuk formulir Data safety terbaru,
dan status lembaga pengawas PDP serta peraturan turunannya.
