<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Models\UserToken;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

/**
 * Tahap 32 langkah 5: menerbitkan token terbatas untuk skill `habitflow-morning-routine`, tanpa kata sandi (akun Google
 * tidak punya, dan email produksi belum terkirim). Hanya token asli yang dicetak ke stdout, supaya bisa dialihkan ke
 * berkas (`... > ~/.habitflow/claude-token`) tanpa pernah tampil di layar atau log. Pesan lain ke stderr.
 */
class IssueScopedToken extends Command
{
    protected $signature = 'habitflow:issue-token
        {email : Email akun pemilik token}
        {--scope=daily-briefs : Cakupan token (hanya daily-briefs)}
        {--name=claude-morning-routine : Nama token, tampil di daftar perangkat}
        {--replace : Cabut dulu token lama dengan nama dan cakupan yang sama}';

    protected $description = 'Terbitkan token terbatas (tanpa kata sandi); hanya token yang dicetak ke stdout (tahap 32 langkah 5)';

    public function handle(): int
    {
        $scope = (string) $this->option('scope');
        if (! in_array($scope, UserToken::SCOPES, true)) {
            return $this->refuse('Cakupan tidak dikenal. Pilihan: '.implode(', ', UserToken::SCOPES).'.');
        }

        $user = User::where('email', (string) $this->argument('email'))->first();
        if (! $user) {
            return $this->refuse('Akun dengan email itu tidak ditemukan.');
        }

        $name = (string) $this->option('name');
        $existing = UserToken::where('user_id', $user->id)->where('name', $name)->where('scope', $scope);

        if ($existing->exists() && ! $this->option('replace')) {
            return $this->refuse('Sudah ada token dengan nama dan cakupan itu. Pakai --replace untuk mencabutnya dan membuat yang baru.');
        }

        [, $plain] = DB::transaction(function () use ($existing, $user, $name, $scope) {
            $existing->delete();

            return UserToken::issue($user, $name, $scope);
        });

        $this->getOutput()->getErrorStyle()->writeln("Token `$name` (cakupan $scope) dibuat untuk akun #{$user->id}. Token hanya tampil sekali, di keluaran ini.");
        $this->line($plain);

        return self::SUCCESS;
    }

    private function refuse(string $message): int
    {
        $this->getOutput()->getErrorStyle()->writeln($message);

        return self::FAILURE;
    }
}
