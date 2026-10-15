<?php

namespace Tests\Support;

/**
 * Daftar sumber tunggal nasib setiap tabel saat akun dihapus (tahap 31D, docs/rilis-play-store.md
 * bagian 1 dan 3). SchemaInventoryTest gagal kalau ada tabel di database yang tidak ada di salah satu
 * daftar ini. Menambah tabel baru (misalnya berat dan tensi di tahap 30) berarti memutuskan di sini
 * DAN menambahkannya ke AccountDeletionService kalau berisi data pengguna.
 */
class DataInventory
{
    /** Berisi data pengguna: harus nol baris setelah akunnya dihapus (kecuali data tim yang dipakai orang lain). */
    public const DELETED_WITH_ACCOUNT = [
        'users',
        'user_tokens',
        'email_codes',
        'habits',
        'habit_entries',
        'schedule_blocks',
        'schedule_block_habits',
        'todos',
        'follow_ups',
        'prayer_days',
        'daily_briefs',
        'work_days',
        'snapshots',
        'teams',
        'team_members',
        'team_invites',
        'team_todos',
    ];

    /** Bukan data pengguna, atau ditangani dengan cara lain, beserta alasannya. */
    public const NOT_USER_DATA = [
        'migrations' => 'Riwayat migrasi Laravel, tidak berisi data pengguna.',
        'account_deletions' => 'Catatan audit penghapusan: hanya waktu dan sumber, tanpa id, nama, atau email.',
        'jobs' => 'Antrean email. Baris yang memuat email pengguna dihapus oleh AccountDeletionService; sisanya hilang saat email terkirim.',
        'failed_jobs' => 'Email gagal kirim. Baris yang memuat email pengguna dihapus oleh AccountDeletionService; pembersihan berkala di 31K.',
    ];
}
