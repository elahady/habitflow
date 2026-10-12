<x-layout title="Hapus akun">
    <article class="prose">
        <h1>Hapus akun Habitflow</h1>
        <p>
            Kamu bisa menghapus akun Habitflow dan seluruh datamu kapan saja. Penghapusan <strong>langsung dan permanen</strong>,
            tanpa masa tunggu, dan tidak bisa dibatalkan.
        </p>

        <h2>Data yang dihapus</h2>
        <ul>
            <li>Akun: nama, email, kata sandi, tautan Google, dan semua sesi di semua perangkat.</li>
            <li>Habit dan riwayat centang, to-do, follow-up kerja, jadwal, dan waktu sholat.</li>
            <li>Data kesehatan: berat badan, tekanan darah, langkah, dan catatan makan.</li>
            <li>Salinan cadangan sinkronisasi (termasuk 14 salinan terakhir), dan kode verifikasi yang tersisa.</li>
            <li>Keanggotaan tim rumah. Kalau kamu pemilik tim dan ada anggota lain, kepemilikan pindah ke anggota terlama dan
                to-do yang kamu buat tetap ada dengan nama "Pengguna dihapus". Kalau tidak ada anggota lain, tim ikut dihapus.</li>
        </ul>

        <h2>Data yang tidak langsung hilang</h2>
        <p>
            Salinan di cadangan harian database hilang saat cadangan itu berputar, paling lama 30 hari setelah penghapusan.
            Data di HP-mu dihapus saat kamu keluar dari aplikasi atau mencopot aplikasinya.
        </p>

        <h2>Cara menghapus</h2>
        <ol>
            <li><strong>Di aplikasi:</strong> Profil, lalu Hapus akun, lalu masukkan kata sandimu (atau konfirmasi Google).</li>
            <li><strong>Di web, kalau sudah masuk:</strong> buka halaman <a href="{{ route('account') }}">Akun</a>, lalu Hapus akun.</li>
            <li><strong>Tanpa aplikasi dan tanpa masuk</strong> (misalnya aplikasinya sudah dicopot atau kamu lupa kata sandi): minta kode 6 angka ke emailmu di bawah ini.</li>
        </ol>

        <h2>Minta kode lewat email</h2>
        <p>Masukkan email akunmu. Kalau terdaftar, kami kirim kode 6 angka yang berlaku 10 menit. Jawaban kami sama untuk email yang terdaftar maupun tidak.</p>
        <form method="POST" action="{{ route('delete-account.code') }}" style="display: block; max-width: 380px;">
            @csrf
            <div class="field">
                <label for="email">Email akun</label>
                <input type="email" id="email" name="email" value="{{ old('email') }}" required autocomplete="email">
                @error('email')
                    <p class="error-text">{{ $message }}</p>
                @enderror
            </div>
            <button type="submit" class="primary">Kirim kode</button>
        </form>
        <p style="margin-top: 16px;">
            Sudah punya kode? <a href="{{ route('delete-account.confirm') }}">Masukkan kode</a>.
            @if (config('habitflow.privacy_contact_email'))
                Kalau tetap tidak bisa, kirim email dari alamat akunmu ke
                <a href="mailto:{{ config('habitflow.privacy_contact_email') }}">{{ config('habitflow.privacy_contact_email') }}</a>.
            @endif
        </p>
        <p>Lihat juga <a href="{{ route('privacy') }}">Kebijakan Privasi</a>.</p>
    </article>
</x-layout>
