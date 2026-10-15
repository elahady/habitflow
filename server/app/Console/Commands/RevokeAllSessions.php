<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Models\UserToken;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

/**
 * Tahap 31K: menahan kebocoran. Mencabut semua token app Android dan memutus semua sesi browser sekaligus, sehingga
 * setiap pengguna harus masuk lagi. Data dan kata sandi tidak disentuh. Dipakai di langkah "menahan" pada
 * docs/insiden-data.md, dan saat memutar APP_KEY atau kredensial server.
 */
class RevokeAllSessions extends Command
{
    protected $signature = 'habitflow:revoke-all-sessions
        {--force : Jalankan tanpa bertanya (untuk skrip)}';

    protected $description = 'Cabut semua token app dan putuskan semua sesi web; semua pengguna harus masuk lagi (tahap 31K)';

    public function handle(): int
    {
        $users = User::count();
        $tokens = UserToken::count();

        if (! $this->option('force') && ! $this->confirm("Ini mencabut $tokens token app dan memutus sesi web $users akun. Lanjut?")) {
            $this->line('Dibatalkan, tidak ada yang diubah.');

            return self::FAILURE;
        }

        DB::transaction(function () {
            UserToken::query()->delete();
            // Cookie "ingat saya" tidak cocok lagi kalau remember_token kosong, dan sesi yang sedang hidup ditolak
            // EnsureSessionNotRevoked begitu session_epoch naik.
            User::query()->update(['remember_token' => null]);
            User::query()->increment('session_epoch');
        });

        $this->info("Selesai: $tokens token app dicabut, sesi web $users akun diputus.");

        return self::SUCCESS;
    }
}
