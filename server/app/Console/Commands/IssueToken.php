<?php

namespace App\Console\Commands;

use App\Models\ApiToken;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Storage;

/**
 * Membuat atau mencabut token pribadi untuk HP. Token asli (48 karakter acak) tampil sekali di terminal
 * dan tidak disimpan; database hanya menyimpan hash SHA-256-nya.
 */
class IssueToken extends Command
{
    protected $signature = 'habitflow:token {name=hp : Nama token, misalnya nama HP} {--revoke : Cabut token dengan nama ini}';

    protected $description = 'Buat token pribadi untuk HP (tampil sekali), atau cabut dengan --revoke';

    public function handle(): int
    {
        $name = (string) $this->argument('name');

        if ($this->option('revoke')) {
            $token = ApiToken::where('name', $name)->first();
            if (! $token) {
                $this->error("Token \"$name\" tidak ditemukan.");

                return self::FAILURE;
            }

            // Snapshot milik token ikut dihapus: baris database (cascade) dan berkas terenkripsinya.
            Storage::disk('local')->deleteDirectory('snapshots/'.$token->id);
            $token->delete();
            $this->info("Token \"$name\" dicabut beserta snapshot-nya.");

            return self::SUCCESS;
        }

        if (ApiToken::where('name', $name)->exists()) {
            $this->error("Token \"$name\" sudah ada. Cabut dulu dengan --revoke kalau ingin membuat ulang.");

            return self::FAILURE;
        }

        [, $plain] = ApiToken::issue($name);
        $this->info("Token \"$name\" dibuat. Salin sekarang, tidak akan ditampilkan lagi:");
        $this->line($plain);

        return self::SUCCESS;
    }
}
