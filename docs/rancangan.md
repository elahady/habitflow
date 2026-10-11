# Rancangan Habitflow

Written for: Roziq (pemilik project), sebagai acuan sebelum dan selama coding.

## Tujuan

Satu layar untuk memantau kebiasaan harian dan menyelesaikan to-do kecil.
Habit adalah prioritas utama. To-do menjadi pendorong tambahan. Progres
divisualisasikan seperti kontribusi GitHub.

## Habit

- Daftar habit awal:
  1. **Sholat 5 waktu** (wajib)
  2. **Baca Al-Quran** (wajib)
  3. Jalan kaki 20 menit (menjadi "8.000 langkah" di tahap 21)
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
- Habit boleh punya sumber otomatis. Yang sudah ada: "Air putih 2 liter" tercentang otomatis dari
  penghitung 8 gelas (tahap 20). Yang direncanakan: habit "8.000 langkah" (dulu "Jalan kaki 20 menit")
  dari Health Connect (tahap 21).

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
- Habitflow menggantikan task harian di Notion. Task lama tidak dimigrasi.

## Pengingat kerja (tahap 20)

- Dua pengingat tingkat **Info** di jam kerja: **minum air** setiap 60 menit dan **break** setiap 90 menit.
  Tanpa bunyi, getar, atau pop-up. Satu notifikasi senyap yang menggantikan dirinya sendiri, tidak menumpuk.
- **Jam kerja** adalah blok jadwal yang punya tanda "Pengingat air dan break". Bawaan: Kerja pagi dan Kerja
  sore. Tanda ini bisa diubah di editor blok (Atur jadwal), jadi jam kerja ikut berubah kalau blok diubah.
  Karena berasal dari blok, **Hari ini libur** otomatis mematikan pengingat (blok Kerja hanya aktif Senin sampai
  Jumat).
- **Waktu pengingat** dihitung per blok bertanda: air di menit mulai blok, lalu setiap 60 menit selama masih
  sebelum blok berakhir (08.00, 09.00, 10.00, 11.00 dan 13.00, 14.00, 15.00 = 7 kali). Break di mulai + 90,
  + 180, dan seterusnya (09.30, 11.00 dan 14.30). Pengingat tidak pernah jatuh tepat di akhir blok.
- **Digabung.** Air dan break yang berjarak 15 menit atau kurang menjadi satu notifikasi "Break + minum" di waktu
  yang lebih awal (11.00 bawaan).
- **Ditahan.** Pengingat yang jatuh di tengah blok sholat (blok yang mulai tepat di waktu sholat) digeser ke akhir
  blok sholat itu. Kalau akhirnya di luar blok kerja, pengingat itu dibuang.
- **Penghitung gelas** per hari, target 8 gelas. Tombol "Sudah minum" di notifikasi dan tombol "+ Segelas" di
  kartu air dashboard menambah satu. Tombol "−" mengurangi satu (tidak di bawah 0). Gelas boleh lebih dari 8.
- **Habit "Air putih 2 liter"** tercentang otomatis saat hitungan naik sampai 8. Centang manual tetap menang:
  membatalkan centang tidak dicentang ulang oleh gelas ke-9. Mengurangi gelas dari 8 ke 7 membatalkan centang yang
  dibuat otomatis (salah ketuk bisa dibatalkan), tetapi tidak menyentuh centang yang pernah diubah sendiri hari itu.
- **Diabaikan.** Pengingat air yang tidak dijawab (tidak ada gelas bertambah sampai pengingat air berikutnya)
  tiga kali berturut-turut di satu blok menghentikan pengingat air sampai blok berikutnya. Break tetap jalan.
- Pengingat air dan break masing-masing bisa dimatikan di Tentang. Awalnya nyala.
- Teks dan nada tenang, tanpa menyalahkan: "Waktunya minum" dengan "3 dari 8 gelas hari ini", dan "Break sebentar"
  dengan "Berdiri dan regangkan badan."
- Pengingat air dan break tidak memengaruhi level hari, selain lewat centang otomatis habit air.

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

Rincian (9 Oktober 2026):

- **Langkah.** Dibaca dari Health Connect saat app dibuka, saat dashboard Hari ini tampil, dan setiap sekitar 1 jam
  di latar belakang. Habit "8.000 langkah" dicentang otomatis **sekali per tanggal**, saat langkah mencapai 8.000.
  Kalau centang itu dibatalkan manual, tidak dicentang ulang di hari yang sama. Tanpa Health Connect, tanpa izin, atau
  tanpa data, app tetap jalan penuh dan habit dicentang manual. Kartu langkah menjelaskan cara mengaktifkan dengan
  tenang.
- **Berat.** Satu angka kg dengan satu desimal (20,0 sampai 300,0). Tinggi badan (100 sampai 250 cm) diisi sekali di
  sheet berat kalau belum ada, dan bisa diubah di Tentang bagian Kesehatan. BMI = kg / (m²), dibulatkan satu desimal
  dan kategorinya dihitung dari angka yang tampil: kurus < 18,5, normal 18,5 sampai 25,0, gemuk di atas 25,0 sampai
  27,0, obesitas di atas 27,0. Target berat bisa diisi sendiri; tanpa itu dipakai batas atas BMI normal
  (25,0 × tinggi²). Sisa ke target = berat terakhir − target. **Tren 4 minggu** = berat terbaru dikurangi berat
  paling awal dalam 28 hari terakhir (butuh dua catatan atau lebih), dianggap stabil kalau selisihnya kurang dari
  0,2 kg.
- **Tensi.** Sistolik 50 sampai 300, diastolik 30 sampai 200 (sistolik harus lebih besar), nadi opsional 20 sampai 250,
  catatan opsional. Kategori diambil dari yang **lebih tinggi** antara tingkat sistolik dan tingkat diastolik:
  optimal < 120 dan < 80, normal 120 sampai 129 atau 80 sampai 84, normal-tinggi 130 sampai 139 atau 85 sampai 89,
  hipertensi derajat 1 mulai 140 atau 90, derajat 2 mulai 160 atau 100, derajat 3 mulai 180 atau 110. Saran khusus
  ("istirahat, ukur ulang, hubungi dokter kalau tetap tinggi atau ada keluhan") muncul bila sistolik ≥ 180 atau
  diastolik ≥ 110, sebagai teks biasa tanpa warna merah dan tanpa alarm.
- **Pengingat** bertingkat Info (senyap, satu notifikasi yang menggantikan dirinya sendiri) pada **Subuh + 60 menit**
  (akhir blok Jamaah Subuh dan ngaji, sebelum aktivitas fisik dan kopi). Berat: hari Senin. Tensi: setiap hari, setiap
  Senin (mingguan, default), atau mati. Kalau jatuh di hari dan jam yang sama, digabung menjadi satu notifikasi.
  Pengingat tidak dikirim kalau hari itu sudah ada catatan jenis itu. Tap membuka app di Hari ini.
- **Grafik** di tab Progres memakai catatan 90 hari terakhir. Tanpa data, tampil teks ajakan mencatat, bukan grafik
  kosong.

## Fokus kesehatan harian (tahap 30)

- Satu kartu **Fokus hari ini** menampilkan **satu** hal yang paling berguna dikerjakan hari ini untuk
  target kesehatan, bukan daftar. Letaknya paling atas di dashboard Hari ini (Android) dan di atas
  dashboard web.
- Dipilih oleh **aturan**, bukan AI. Aturan dicek berurutan dan aturan pertama yang cocok yang menang.
  Hasilnya selalu sama untuk data yang sama, jalan tanpa internet, dan bisa diuji.
- Kartu menjelaskan alasannya ("Kenapa ini?"): angka yang dipakai dan aturan yang cocok.
- Berat dan tensi **hanya dicatat di Android**. Web hanya menampilkan. Aturan hanya ada di kode
  Android; web tidak punya salinan aturan dan menampilkan hasil yang dikirim Android.
- Beda dengan **saran pola** di Asisten AI (tahap 24): saran pola adalah ringkasan mingguan dari AI,
  fokus ini adalah satu langkah harian dari aturan. Kalau keduanya tampil, fokus di atas.

Rincian (11 Oktober 2026):

- **Urutan aturan** (yang pertama cocok menang):
  1. **Tensi.** Tensi terakhir ≥ 180/110: tampil saran tenang yang sudah ada, tanpa fokus gaya hidup. Dua catatan
     tensi terakhir sama-sama ≥ 140/90: fokus "Ukur ulang besok pagi, setelah duduk tenang 5 menit. Kalau tetap
     tinggi, temui dokter."
  2. **Data awal.** Belum ada berat atau tinggi: fokus "Catat berat pertama". Tidak ada rekomendasi lain sampai
     data dasar ada.
  3. **Data lama.** Berat terakhir lebih dari 10 hari lalu: fokus "Timbang berat hari ini", supaya rekomendasi
     lain tidak bersandar pada angka basi.
  4. **Turun terlalu cepat.** Tren 4 minggu turun 2,0 kg atau lebih (lebih cepat dari 0,5 kg per minggu): fokus
     "Cukup, pertahankan porsi makan". Tidak ada saran menambah usaha menurun.
  5. **Minuman.** Kopi hari ini 3 gelas atau lebih, atau minuman manis 1 atau lebih: fokus "Ganti minuman
     berikutnya dengan air".
  6. **Langkah rendah.** Pukul 10.00 atau lebih dan langkah di bawah 50% target (8.000): fokus "Jalan N menit
     sore ini". N = sisa langkah ÷ 120 langkah per menit, dibulatkan ke atas ke kelipatan 5, paling banyak 60.
  7. **Catat makan.** Berat di atas target, tren naik, pukul 13.00 atau lebih, dan belum ada catatan makan hari
     ini: fokus "Catat makan hari ini dengan pola Isi Piringku".
  8. **Di jalur** (bawaan kalau tidak ada yang cocok): "Hari ini sudah di jalur" beserta satu kalimat posisi
     terhadap target. Tanpa tugas tambahan.
- **Pagar keselamatan**, berlaku untuk semua aturan:
  - Tidak pernah menyarankan menurunkan berat kalau BMI kurus (< 18,5) atau berat sudah di bawah atau sama dengan
    target. Aturan 7 tidak berlaku dan kalimat posisi hanya menyebut "sudah di target".
  - Tidak menyebut kalori, puasa, atau diet. Laju turun yang disebut wajar adalah 0,25 sampai 0,5 kg per minggu.
  - Fokus langkah dibatasi 60 menit dan selalu berupa jalan santai, tidak pernah olahraga berat.
  - Tensi ≥ 180/110 selalu menang atas semua fokus lain.
  - Data kurang berarti tidak menebak. Aturan yang butuh angka yang belum ada dilewati.
  - Nada tenang dan netral, tanpa warna merah dan tanpa menghakimi. Kategori adalah informasi, bukan diagnosis.
- **Kapan dihitung.** Saat app dibuka, saat berat, tensi, makan, atau minuman dicatat, dan saat langkah dibaca. Fokus
  berubah sendiri begitu kondisinya terpenuhi (misalnya langkah mencapai 8.000 berpindah ke aturan berikutnya).
- **Tampilan Android.** Kartu hero sage muda (`primaryFixed`, teks `onPrimaryContainer`) di atas kartu Sekarang.
  Isi: label "Fokus hari ini", judul, satu kalimat, progres langkah, dan tombol teks "Kenapa ini?" yang
  membuka alasan. Kartu Berat dan Tensi tetap punya "+ Catat".
- **Tampilan web** (`/dashboard`). Kartu fokus dengan panel "Dasar rekomendasi", lalu bagian Kesehatan: berat dengan
  grafik 6 minggu dan garis target, langkah 7 hari dengan garis target, dan tensi terakhir. Tanpa tombol catat; ada
  keterangan bahwa berat dan tensi dicatat lewat aplikasi di ponsel. Menampilkan waktu sinkron terakhir.
- Rancangan visual: mockup web dan mobile di https://claude.ai/artifact/TWJ8Uus2hKtQQa81wKujG5

## Kalender (tahap 22)

- Acara berbeda dari blok jadwal: blok adalah rutinitas harian, acara adalah kejadian di
  tanggal tertentu atau berulang (misalnya meeting reguler setiap 2 minggu).
- Acara dibuat di Habitflow dengan label **Kerja** atau **Pribadi**, dan bisa berulang: harian,
  setiap N minggu di hari tertentu, bulanan per tanggal atau per urutan hari, tahunan, dengan
  tanggal berakhir opsional. Satu kejadian bisa dilewati atau diubah sendiri.
- Acara dari kalender HP ikut tampil (hanya baca), dari kalender yang dipilih di pengaturan.
- Pengingat −15 menit (bisa diubah per acara) hanya untuk acara Habitflow. Acara kalender HP
  tidak diberi notifikasi oleh Habitflow.
- Acara hari ini masuk timeline dan Sekarang/Berikutnya. Acara Kerja tampil di Agenda tab
  Kerja, acara Pribadi di dashboard Hari ini.
- **Libur nasional** dan cuti bersama Indonesia otomatis mengaktifkan "Hari ini libur": blok
  dan acara Kerja mati, sholat, alarm Subuh, dan acara Pribadi tetap. Bisa dibatalkan per
  tanggal.
- Catatan cepat yang dibuat selama acara Kerja berlangsung tertaut ke acara itu.

Rincian (9 Oktober 2026):

- **Acara** punya judul, label, tanggal mulai, jam mulai dan durasi menit (bawaan 60) atau "Sepanjang hari", pengulangan,
  tanggal berakhir (ikut dihitung), pengingat, dan catatan. Acara tidak melewati satu hari: yang jamnya melewati 24.00
  dipotong di 24.00 pada timeline.
- **Pengulangan:** Sekali, Harian, Mingguan, Bulanan, Tahunan. Mingguan berarti setiap N minggu (1 sampai 12) di hari
  terpilih (bawaan hari tanggal mulai); minggu dihitung mulai Senin dan minggu pertama adalah minggu yang memuat tanggal
  mulai. Bulanan bisa per tanggal (setiap tanggal 12) atau per urutan hari (Senin kedua; urutan 5 menjadi "terakhir").
  Tahunan jatuh di tanggal dan bulan yang sama. **Tanggal yang tidak ada di bulan itu** (31 di bulan pendek, atau 29
  Februari di tahun biasa) jatuh di **hari terakhir bulan itu**.
- **Satu kejadian** bisa dilewati (hilang dari semua tampilan dan pengingat) atau diubah sendiri (judul, tanggal, jam, durasi).
  Mengubah acara berarti mengubah seluruh seri; kejadian yang sudah diubah satu per satu tetap memakai ubahannya. Menghapus
  acara menghapus semua kejadian dan pengecualiannya.
- **Kalender HP** dipilih per kalender di Tentang, masing-masing berlabel Kerja atau Pribadi (bawaan Pribadi). Hanya baca, tanpa
  notifikasi dari Habitflow, dan tidak ikut cadangan karena nomor kalender khusus perangkat. Acara yang dibatalkan di kalender
  HP tidak tampil. Acara sepanjang hari memakai tanggal lokal.
- **Tampilan:** timeline dan Sekarang/Berikutnya di Hari ini memuat semua acara berjam (Habitflow dan HP, kedua label) dengan
  penanda "Acara". Acara sepanjang hari tampil sebagai baris di awal timeline. Agenda di tab Kerja memuat 7 hari ke depan dan
  tampilan bulan.
- **Libur nasional** memakai daftar SKB 3 Menteri per tahun yang disimpan di app (2026 dan 2027, bersumber setneg.go.id).
  Tahun tanpa daftar berarti tidak ada libur otomatis. **Cuti bersama diperlakukan sama dengan libur nasional**, dan keduanya
  bisa dibatalkan per tanggal (misalnya kantor tetap masuk saat cuti bersama). "Hari ini libur" manual tetap ada. Pada hari
  libur (manual atau nasional): blok yang hanya aktif hari kerja dan semua acara berlabel Kerja (Habitflow maupun kalender HP)
  mati, termasuk pengingatnya. Sholat, alarm Subuh, dan acara Pribadi tetap.
- **Pengingat acara:** hanya untuk acara Habitflow berjam. Pilihan Tanpa, 5, 10, 15 (bawaan), 30, atau 60 menit sebelum mulai.
  Tingkat Pengingat (heads-up) dengan judul acara dan jam mulainya. Kejadian yang dilewati tidak diingatkan, dan kejadian yang
  diubah diingatkan menurut jam barunya.
- **Catat cepat tertaut** ke acara Kerja berjam yang sedang berlangsung (dari jam mulai sampai selesai, Habitflow atau kalender
  HP). Follow-up menyimpan judul dan tanggal acara, jadi keterangannya tetap ada walau acaranya dihapus. Di EOD, Inbox
  dikelompokkan per acara, dan di daftar Kerja kartunya diberi keterangan "Dari acara: ...".

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

Rincian (10 Oktober 2026):

- **Satu catatan per tanggal dan jenis** (sarapan, siang, malam, camilan). Menekan chip yang sudah dicatat membuka catatan itu
  untuk diubah atau dihapus. Jam catatan bawaannya sekarang dan bisa diubah; jam inilah yang dipakai aturan makan malam.
- **Batas tidur** adalah akhir rentang notifikasi tetap (jam blok "Batas tidur", 22.00 bawaan). Makan malam tepat waktu kalau
  jamnya paling lambat 2 jam sebelumnya (20.00), tepat 20.00 masih sah. Jam batas tidur kemarin disamakan dengan hari ini.
- **Hari tanpa catatan makan:** "Tanpa gorengan atau camilan manis" dan "Makan malam" tidak dicentang karena tidak ada data. Kopi
  dan minuman manis memakai penghitung, jadi hitungan 0 berarti dicentang.
- **Kapan dihitung:** saat batas tidur (alarm blok Batas tidur) dan saat app dibuka untuk kemarin serta hari ini yang sudah
  lewat batas tidur. Perhitungan hanya menambah centang, tidak pernah menghapus, dan bisa diulang aman.
- **Centang manual menang:** setiap centang atau batal-centang sendiri menandai pasangan habit dan tanggalnya, dan perhitungan
  otomatis melewatinya. Habit yang dibuat sesudah tanggal itu juga dilewati.
- **Kopi ke-3** menampilkan "Kopi hari ini sudah 3 gelas, di atas batas 2." di bawah kartu air, tanpa warna status.
- **Pengingat catatan makan** muncul 60 menit sebelum batas tidur (21.00), senyap, berisi "Belum dicatat: ..." untuk sarapan,
  makan siang, dan makan malam yang kosong (camilan tidak wajib). Tidak muncul kalau ketiganya sudah dicatat. Bisa dimatikan
  di Tentang, bagian Catatan makan.
- **Ringkasan mingguan** (Progres, Kesehatan): tujuh hari terakhir termasuk hari ini. Satu hari piring lengkap kalau makan siang
  atau malamnya memuat karbo, lauk, sayur, dan buah (dihitung sekali per hari). Gorengan dan manis dihitung per catatan makan,
  kopi per gelas.

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
> *Catatan tahap 31 (11 Oktober 2026): baris "Tidak ada akun dan tidak ada sinkron" di bawah juga sudah usang sejak
> akun jadi wajib — ditinggalkan di sini sebagai riwayat, bukan diperbaiki, karena seluruh bagian ini memang batal.*

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

## Akun dan login (tahap 31)

> Mengganti asumsi "tanpa akun, offline dan lokal" yang berlaku sejak tahap 1. Keputusan lengkap dan riwayat diskusi
> ada di `docs/concept.md` tahap 31; bagian ini ringkasan aturan fitur untuk acuan coding.

- **Login wajib.** Tanpa login, app tidak bisa dipakai — layar Masuk menjadi gerbang di depan semua layar, tab, alarm,
  notifikasi, dan worker latar belakang. Tidak ada lagi keadaan "belum masuk, tetap bisa pakai app lokal".
- **Offline setelah login tetap penuh.** Room tetap sumber data harian (offline-first); internet hanya dibutuhkan untuk
  login pertama kali dan sinkron. Login wajib mengubah **siapa boleh memakai app**, bukan **cara app menyimpan data**.
- **Cara masuk**: email dan kata sandi, atau Google — sama seperti web (tahap 25). API Android tidak lagi Google-only.
  Login Google dengan email yang sudah terdaftar dan **terverifikasi** ditautkan ke akun yang sama; email yang
  **belum** terverifikasi malah diambil alih oleh login Google (kata sandi lama dikosongkan, semua token dicabut) —
  ini sengaja, untuk menutup celah pengambilalihan akun.
- **Verifikasi email wajib** untuk akun email dan kata sandi: kode 6 angka di app, berlaku 10 menit, sekali pakai,
  disimpan sebagai hash di server, 5 kali salah menghanguskan kode. Akun Google dianggap sudah terverifikasi.
- **Lupa kata sandi**: kode 6 angka lewat email (pola sama dengan verifikasi, **bukan tautan**). Jawaban server **sama**
  persis baik email terdaftar maupun tidak, supaya tidak bisa dipakai menebak akun mana yang ada (anti-enumerasi).
- **Aturan kata sandi**: minimal 8 karakter, ditolak kalau termasuk daftar kata sandi paling umum. Tanpa autentikasi
  dua langkah di tahap ini.
- **Sesi tidak kedaluwarsa.** Hanya dicabut oleh: Keluar, ganti kata sandi (mencabut sesi lain, bukan sesi yang
  sedang dipakai), "Keluar dari semua perangkat", hapus akun, atau dari web.
- **Keluar** menghapus **semua data lokal** di HP (database, pengaturan, cache), menghentikan semua alarm, notifikasi,
  dan worker, lalu app dimulai ulang ke layar Masuk — supaya HP bisa dipakai akun lain tanpa sisa data akun
  sebelumnya. Kalau ada perubahan yang belum terkirim ke server, app menampilkan jumlahnya dan minta konfirmasi dulu;
  tanpa internet, peringatannya lebih keras karena perubahan itu akan hilang permanen.
- **Hapus akun**: wajib ada, bisa dari app (Profil) **dan** dari halaman web publik yang bisa dibuka tanpa app
  (syarat Google Play). Permanen dan langsung dalam satu transaksi (tanpa masa tunggu), dengan konfirmasi ulang kata
  sandi (atau `id_token` Google untuk akun tanpa kata sandi). Menghapus seluruh data pengguna di server — daftar
  lengkap ada di inventaris `docs/rilis-play-store.md` bagian 1.
- **Data lama di HP** (dari sebelum tahap 31, format tanpa akun) **diambil alih oleh akun pertama yang login** di
  pemasangan itu, sekali saja — jalur gabung yang sama dengan tahap 27/28 dipakai ulang.
- **Snapshot (tahap 19B)** pindah ke akun: `PUT /snapshot` dan `GET /snapshot/latest` memakai `user-token`, bukan lagi
  token per-HP. Token lama (`api_tokens`) dipensiunkan setelah snapshot lama diklaim ke akun pemilik lewat satu
  perintah artisan. Data yang belum dimigrasi ke tabel sendiri (jadwal, kesehatan, makan, kalender, pengaturan, EOD)
  tetap lewat jalur snapshot, hanya saja sekarang terikat akun.
- **Tim (Tugas Rumah)** saat pemilik tim menghapus akunnya: kalau ada anggota lain, kepemilikan pindah ke anggota
  terlama dan to-do buatan pemilik tetap ada dengan nama "Pengguna dihapus". Kalau tidak ada anggota lain, timnya ikut
  dihapus. Anggota biasa yang menghapus akun hanya keluar dari tim, tidak menghapus tim.
- **Layar baru**: Masuk, Daftar, Verifikasi kode, Lupa kata sandi (dua langkah: minta kode, lalu kata sandi baru).
  Tombol "Lewati dulu" di layar Masuk/Daftar **dihapus** — tidak relevan lagi karena login wajib.
- **Navigasi berubah**: tab "Tentang" diganti **Pengaturan** (ikon roda gigi); nama app, versi, dan "Dibuat oleh"
  turun ke bagian paling bawah Pengaturan. Lingkaran inisial di Kepala layar Hari ini membuka halaman **Profil**
  (layar penuh di atas tab, bukan tab ketujuh) — berisi Data tubuh (tinggi dan target berat, dipindah dari Tentang
  lama), Akun (cara masuk, ubah kata sandi), Sinkron, tombol Keluar dan Hapus akun. Tidak ada lagi halaman "Profil
  belum masuk" karena keadaan itu tidak mungkin tercapai.
- **"Atur server" dan kolom token di pengaturan dihapus.** Alamat server menjadi konfigurasi build — pengguna Play
  Store tidak pernah mengisi alamat server atau token manual; itu dulunya alat pengembang saja.
- Detail layar lengkap (nilai desain, kontrak API, peta sub-tahap 31A-31M) ada di `docs/design/README.md` bagian
  Pengaturan/Profil/Masuk-Daftar-Lupa kata sandi, dan `docs/concept.md` tahap 31.

## Sinkron ke server (tahap 19B)

> **Digantikan sebagian oleh tahap 31.** Mekanisme snapshot di bawah masih berlaku untuk data yang belum dimigrasi ke
> tabel sendiri (jadwal, kesehatan, makan, kalender, pengaturan, EOD), tapi dua hal berikut berubah: **login memakai
> akun, bukan token pribadi tanpa akun** (baris "Login" di bawah sudah usang), dan **pulihkan dari server pindah ke
> layar Pengaturan** (bukan Tentang). Lihat [Akun dan login (tahap 31)](#akun-dan-login-tahap-31) untuk aturan yang
> berlaku sekarang.

- Fungsinya **backup dan pindah HP**, bukan akses dari web atau laptop. HP tetap sumber data dan app
  tetap bisa dipakai penuh tanpa internet.
- Arah satu jalur: HP mengirim **snapshot lengkap** (satu berkas JSON) ke server Laravel sendiri.
  Otomatis sekitar 5 menit setelah ada perubahan (hanya saat ada internet) dan sekali sehari, atau
  lewat tombol "Sinkron sekarang" di Pengaturan.
- ~~Login memakai **token pribadi** yang dibuat sekali di server dan ditempel di Tentang, bersama alamat
  server (HTTPS). Tanpa akun.~~ **Digantikan tahap 31**: login memakai akun (email/kata sandi atau Google), snapshot
  terikat `user-token`. Tidak ada lagi kolom alamat server atau token manual di pengaturan.
- **Pulihkan dari server** hanya manual, di Pengaturan, dengan konfirmasi yang menyebut waktu snapshot.
  Pemulihan **mengganti seluruh data** di HP (habit, riwayat, to-do, jadwal, follow-up, EOD,
  pengaturan) dalam satu transaksi.
- Nada alarm dan tanggal alarm yang dimatikan sekali tidak ikut disinkron.
- Isi snapshot disimpan terenkripsi di server. Server menyimpan 14 snapshot terakhir.
- Kontrak API dan format snapshot ada di `docs/concept.md` tahap 19B. Perubahan jalur login ada di tahap 31.

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

- Nama app: Habitflow.
- Application ID: `com.roziqrizal.habitflow`.

## Data

- `habits`: id, nama, tanggal dibuat, urutan.
- `habit_entries`: id habit, tanggal (ISO `yyyy-MM-dd`). Satu baris = habit selesai di tanggal itu.
- `todos`: id, judul, tanggal, selesai (boolean), tanggal dibuat.

Semua disimpan lokal di perangkat lewat Room (offline-first, tetap sumber data harian). **Sejak tahap 31, akun
wajib** — data tidak lagi "tanpa akun dengan cadangan opsional", tapi terikat ke akun lewat `user-token`: habit,
to-do, follow-up, jadwal, dan tim (tahap 26-29) sudah sinkron dua arah langsung ke server; yang belum dimigrasi
(kesehatan, makan, kalender, pengaturan, EOD) masih lewat snapshot satu arah (lihat Sinkron ke server) tapi tetap
terikat akun yang sama, bukan token pribadi tanpa akun.

## Di luar versi ini

Diperbarui 11 Oktober 2026 — beberapa butir lama di bagian ini sudah jadi fitur sejak tahap 21-28 dan dihapus dari
daftar. Yang masih benar-benar di luar versi ini:

- Gula darah dan lingkar perut (berat dan tensi sudah ada sejak tahap 21).
- Ekspor CSV.
- Autentikasi dua langkah (2FA) untuk login — diputuskan eksplisit di tahap 31, tidak masuk versi ini.
- Mail hosting sendiri untuk verifikasi email — dipakai layanan pihak ketiga (Resend/Brevo, tahap 31).
- Menyalakan R8/pengecil kode build rilis — mati sampai app tayang dan stabil (tahap 31), jadi tahap tersendiri nanti.
