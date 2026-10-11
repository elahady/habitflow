<x-layout title="Kebijakan Privasi">
    <article class="prose">
        <h1>Kebijakan Privasi Habitflow</h1>
        <p>
            Versi {{ config('habitflow.privacy_policy_version') }}, berlaku sejak {{ config('habitflow.privacy_policy_effective') }}.
            Kebijakan ini menjelaskan data apa yang disimpan Habitflow (aplikasi Android dan situs
            habitflow.roziqrizal.com), untuk apa, berapa lama, dan bagaimana kamu bisa menghapusnya.
        </p>

        <h2>1. Siapa yang bertanggung jawab</h2>
        <p>
            Habitflow dikelola oleh <strong>{{ config('habitflow.owner_name') }}</strong>, perorangan, sebagai pengendali data.
            @if (config('habitflow.privacy_contact_email'))
                Untuk pertanyaan, permintaan data, atau laporan masalah privasi, hubungi
                <a href="mailto:{{ config('habitflow.privacy_contact_email') }}">{{ config('habitflow.privacy_contact_email') }}</a>.
            @endif
        </p>

        <h2>2. Data yang kami simpan</h2>
        <table>
            <thead><tr><th>Jenis</th><th>Isi</th></tr></thead>
            <tbody>
                <tr><td>Akun</td><td>Nama, alamat email, kata sandi (disimpan sebagai hash, bukan kata sandinya), foto dan ID akun Google kalau kamu masuk dengan Google, waktu verifikasi email, serta waktu dan versi kebijakan yang kamu setujui.</td></tr>
                <tr><td>Sesi</td><td>Token masuk per perangkat (disimpan sebagai hash) beserta nama perangkat dan waktu terakhir dipakai.</td></tr>
                <tr><td>Aktivitas</td><td>Habit dan riwayat centang, to-do, follow-up kerja (termasuk nama orang dan catatan yang kamu tulis), jadwal harian, dan tugas tim rumah.</td></tr>
                <tr><td>Kesehatan</td><td>Berat badan, tinggi badan, target berat, tekanan darah, langkah (dari Health Connect kalau kamu mengizinkan), dan catatan makan yang kamu masukkan.</td></tr>
                <tr><td>Lokasi perkiraan</td><td>Nama dan koordinat kota yang kamu pilih untuk menghitung waktu sholat. Bukan lokasi real-time.</td></tr>
                <tr><td>Kalender</td><td>Acara kalender di HP dibaca di HP untuk ditampilkan di jadwal; salinan acara ikut tersimpan di server bersama data jadwalmu.</td></tr>
                <tr><td>Catatan teknis</td><td>Alamat IP dipakai sementara untuk membatasi percobaan masuk yang berlebihan, dan muncul di log server. Berat, tekanan darah, dan isi cadangan tidak dicatat di log.</td></tr>
            </tbody>
        </table>
        <p>Data kesehatan di atas termasuk data pribadi yang bersifat spesifik. Kami hanya menyimpannya setelah kamu menyetujui kebijakan ini saat mendaftar.</p>
        <p><strong>Habitflow bukan alat diagnosis.</strong> Kategori BMI, tekanan darah, dan fokus harian hanya ringkasan angka yang kamu masukkan, bukan saran atau diagnosis medis.</p>

        <h2>3. Untuk apa data dipakai</h2>
        <ul>
            <li>Menampilkan habit, to-do, jadwal, dan ringkasan kesehatanmu di HP dan di situs web.</li>
            <li>Menyinkronkan data antar perangkatmu dan menyimpannya supaya tidak hilang kalau HP rusak.</li>
            <li>Mengirim kode verifikasi dan kode atur ulang kata sandi lewat email.</li>
            <li>Mengamankan akun (batas percobaan masuk, pencabutan sesi).</li>
        </ul>
        <p>Kami tidak menjual datamu, tidak memakainya untuk iklan, dan tidak membagikannya ke pihak lain di luar yang tertulis di bawah.</p>

        <h2>4. Pihak lain yang terlibat</h2>
        <ul>
            <li><strong>Google</strong>: hanya kalau kamu memilih masuk dengan Google. Kami menerima nama, email, dan foto profil.</li>
            <li><strong>Layanan pengirim email</strong> (Resend, dengan Brevo sebagai cadangan): menerima alamat emailmu dan kode verifikasi atau atur ulang saat email dikirim.</li>
            <li><strong>Cloudflare</strong>: meneruskan lalu lintas ke server kami. Lalu lintas memakai HTTPS.</li>
            <li><strong>Anggota tim rumah</strong>: kalau kamu bergabung ke tim, anggota lain melihat namamu dan to-do tim yang kamu buat atau ambil.</li>
        </ul>

        <h2>5. Di mana dan berapa lama data disimpan</h2>
        <p>
            Data disimpan di server milik pengelola di rumah pengelola di Indonesia, di database MySQL. Data cadangan
            sinkronisasi dienkripsi dengan kunci aplikasi. Cadangan harian database disimpan terenkripsi di penyimpanan
            terpisah di luar server dan dihapus otomatis setelah paling lama 30 hari.
        </p>
        <p>Datamu disimpan selama akunmu ada. Setelah akun dihapus, datanya dihapus seperti dijelaskan di bawah.</p>

        <h2>6. Hakmu</h2>
        <ul>
            <li><strong>Melihat dan mengubah</strong> datamu: lewat aplikasi dan situs web.</li>
            <li><strong>Mengganti kata sandi</strong> dan <strong>keluar dari semua perangkat</strong>: di halaman Akun dan di aplikasi.</li>
            <li><strong>Menghapus akun dan seluruh datamu</strong>: lihat <a href="{{ route('delete-account') }}">cara menghapus akun</a>. Penghapusan langsung dan permanen. Salinan di cadangan hilang saat cadangan itu berputar, paling lama 30 hari.</li>
            <li><strong>Menarik persetujuan</strong>: dengan menghapus akun.</li>
        </ul>

        <h2>7. Kalau terjadi kebocoran data</h2>
        <p>
            Kalau ada kebocoran data pribadi yang berisiko bagimu, kami akan memberi tahu kamu dan lembaga yang berwenang
            paling lambat 3 x 24 jam sejak kebocoran diketahui, sesuai Undang-Undang Pelindungan Data Pribadi.
        </p>

        <h2>8. Anak-anak</h2>
        <p>Habitflow tidak ditujukan untuk anak di bawah 13 tahun, dan kami tidak sengaja menyimpan data mereka.</p>

        <h2>9. Perubahan kebijakan</h2>
        <p>
            Kalau isi kebijakan berubah, nomor versi di atas naik dan versi yang kamu setujui tercatat di akunmu.
            Perubahan yang berarti kami beritahukan lewat aplikasi atau email sebelum berlaku.
        </p>
    </article>
</x-layout>
