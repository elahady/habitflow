<?php

return [

    // Versi kebijakan privasi yang berlaku (tahap 31B). Dicatat di users.privacy_policy_version saat
    // persetujuan. Naikkan setiap kali isi kebijakan berubah (halaman /kebijakan-privasi dibuat di 31C).
    'privacy_policy_version' => env('PRIVACY_POLICY_VERSION', '1'),

    // Tanggal berlaku versi di atas, ditampilkan di halaman /kebijakan-privasi. Ubah bersama versinya.
    'privacy_policy_effective' => env('PRIVACY_POLICY_EFFECTIVE', '12 Oktober 2026'),

    // Nama dan kontak yang bertanggung jawab atas data pengguna (wajib di kebijakan privasi, Gerbang C).
    'owner_name' => env('OWNER_NAME', 'Roziq Rizal'),
    'privacy_contact_email' => env('PRIVACY_CONTACT_EMAIL'),

    // Batas email per hari di paket layanan pengirim (Resend gratis: 100 per hari). Saat terkirim
    // mencapai 80% batas, pemilik diberi tahu satu kali per hari.
    'mail_daily_limit' => (int) env('MAIL_DAILY_LIMIT', 100),

    // Alamat pemilik untuk pemberitahuan operasional (batas email hampir habis).
    'owner_email' => env('OWNER_EMAIL'),

    // Kode 6 angka lewat email: masa berlaku dan batas salah (tahap 31B).
    'email_code_ttl_minutes' => 10,
    'email_code_max_attempts' => 5,

];
