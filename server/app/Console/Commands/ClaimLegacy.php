<?php

namespace App\Console\Commands;

use App\Http\Controllers\SnapshotController;
use App\Models\ApiToken;
use App\Models\Snapshot;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

/**
 * Tahap 31E: menautkan snapshot milik token per-HP tahap 19B ke akun pemilik HP itu. Dijalankan satu
 * kali per HP (pemilik, lalu istri). Aman diulang: yang sudah punya akun tidak disentuh. Tokennya sendiri
 * tidak dihapus (sudah tidak diterima API sejak 31E; tabelnya dihapus di 31L supaya rilis sebelumnya
 * masih bisa dipakai kalau harus jalan mundur).
 */
class ClaimLegacy extends Command
{
    protected $signature = 'habitflow:claim-legacy
        {email : Email akun pemilik snapshot lama}
        {--token= : Nama token 19B yang diklaim (wajib kalau token-nya lebih dari satu)}
        {--dry-run : Hanya tampilkan hitungannya, tanpa mengubah apa pun}';

    protected $description = 'Tautkan snapshot token 19B ke sebuah akun (tahap 31E)';

    public function handle(): int
    {
        $email = mb_strtolower(trim((string) $this->argument('email')));
        $user = User::where('email', $email)->first();
        if (! $user) {
            $this->error("Akun dengan email \"$email\" tidak ditemukan.");

            return self::FAILURE;
        }

        $token = $this->resolveToken();
        if (! $token) {
            return self::FAILURE;
        }

        $legacy = Snapshot::where('api_token_id', $token->id);
        $total = (clone $legacy)->count();
        $unclaimed = (clone $legacy)->whereNull('user_id')->count();
        $byOthers = (clone $legacy)->whereNotNull('user_id')->where('user_id', '!=', $user->id)->count();
        $before = $user->snapshots()->count();

        $this->line("Token \"{$token->name}\" memiliki $total snapshot: $unclaimed belum punya akun, ".($total - $unclaimed - $byOthers).' sudah milik akun ini, '."$byOthers milik akun lain (tidak disentuh).");
        $this->line("Akun {$user->email} saat ini punya $before snapshot.");

        if ($this->option('dry-run')) {
            $this->info("Dry run: $unclaimed snapshot akan ditautkan. Tidak ada yang diubah.");

            return self::SUCCESS;
        }

        DB::transaction(function () use ($token, $user) {
            Snapshot::where('api_token_id', $token->id)->whereNull('user_id')->update(['user_id' => $user->id]);
        });

        $pruned = $this->prune($user);
        $after = $user->snapshots()->count();

        $this->info("Selesai: $unclaimed snapshot ditautkan. Akun {$user->email}: $before → $after snapshot".($pruned > 0 ? " ($pruned dipangkas ke batas ".SnapshotController::KEEP.')' : '').'.');

        return self::SUCCESS;
    }

    private function resolveToken(): ?ApiToken
    {
        $name = $this->option('token');
        if ($name !== null && $name !== '') {
            $token = ApiToken::where('name', $name)->first();
            if (! $token) {
                $this->error("Token \"$name\" tidak ditemukan.");
            }

            return $token;
        }

        $names = ApiToken::orderBy('id')->pluck('name');
        if ($names->count() === 1) {
            return ApiToken::where('name', $names->first())->first();
        }
        $this->error($names->isEmpty()
            ? 'Tidak ada token 19B.'
            : 'Ada beberapa token, sebutkan dengan --token=<nama>. Pilihan: '.$names->implode(', '));

        return null;
    }

    /** Batas 14 salinan per akun tetap berlaku, termasuk kalau akun sudah punya snapshot sendiri. */
    private function prune(User $user): int
    {
        $old = $user->snapshots()->latest('id')->skip(SnapshotController::KEEP)->take(PHP_INT_MAX)->get();
        foreach ($old as $snapshot) {
            Storage::disk('local')->delete($snapshot->path);
            $snapshot->delete();
        }

        return $old->count();
    }
}
