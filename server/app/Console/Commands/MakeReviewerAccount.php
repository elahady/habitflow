<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Tahap 31L: akun tetap untuk peninjau Google Play (kolom "App access" di Play Console). Peninjau tertahan di layar
 * Masuk kalau tidak punya akun yang sudah terverifikasi, jadi akun ini dibuat langsung (tanpa email verifikasi),
 * berisi data contoh supaya layar tidak kosong. Aman diulang: data contoh hanya dibuat kalau akun belum punya habit,
 * dan kata sandi hanya diganti dengan --reset-password.
 */
class MakeReviewerAccount extends Command
{
    protected $signature = 'habitflow:make-reviewer-account
        {email : Email akun peninjau (alamat yang sungguh dikelola pemilik)}
        {--name=Peninjau Play : Nama yang tampil}
        {--password= : Kata sandi (kalau kosong dibuat acak dan dicetak sekali)}
        {--reset-password : Ganti kata sandi akun yang sudah ada}';

    protected $description = 'Buat akun peninjau Play Store yang terverifikasi dengan data contoh (tahap 31L)';

    public function handle(): int
    {
        $email = mb_strtolower(trim((string) $this->argument('email')));
        if (! filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $this->error("\"$email\" bukan alamat email yang sah.");

            return self::FAILURE;
        }

        $password = (string) ($this->option('password') ?: Str::password(20, symbols: false));
        if (mb_strlen($password) < 8) {
            $this->error('Kata sandi minimal 8 karakter.');

            return self::FAILURE;
        }

        $user = User::where('email', $email)->first();
        if ($user && ! $this->option('reset-password')) {
            $this->error("Akun $email sudah ada. Pakai --reset-password untuk mengganti kata sandinya.");

            return self::FAILURE;
        }

        DB::transaction(function () use (&$user, $email, $password) {
            $user ??= new User(['email' => $email, 'name' => (string) $this->option('name')]);
            $user->forceFill([
                'password' => $password,
                'email_verified_at' => $user->email_verified_at ?? now(),
                'privacy_consented_at' => $user->privacy_consented_at ?? now(),
                'privacy_policy_version' => $user->privacy_policy_version ?? config('habitflow.privacy_policy_version'),
            ])->save();
            // Kata sandi baru mencabut sesi lama, seperti ganti kata sandi biasa.
            $user->revokeTokens();
            $user->revokeWebSessions();

            if (! DB::table('habits')->where('user_id', $user->id)->exists()) {
                $this->seedSampleData($user);
            }
        });

        $this->info("Akun peninjau siap: $email");
        if (! $this->option('password')) {
            $this->line("Kata sandi (hanya tampil sekali, simpan untuk kolom App access): $password");
        }
        $this->line('Isi kolom App access di Play Console dengan email dan kata sandi ini, dan jangan menghapus akunnya selama app ditinjau.');

        return self::SUCCESS;
    }

    private function seedSampleData(User $user): void
    {
        $now = now();
        $names = ['Sholat Subuh berjamaah', 'Baca Al-Quran', 'Olahraga 20 menit'];
        foreach ($names as $i => $name) {
            $habitId = DB::table('habits')->insertGetId([
                'user_id' => $user->id, 'name' => $name, 'sort_order' => $i, 'is_mandatory' => $i === 0,
                'created_at' => $now, 'updated_at' => $now,
            ]);
            // Riwayat 10 hari terakhir dengan pola yang berbeda per habit, supaya kotak kontribusi berwarna.
            for ($d = 1; $d <= 10; $d++) {
                if (($d + $i) % 3 !== 0) {
                    DB::table('habit_entries')->insert([
                        'habit_id' => $habitId, 'date' => $now->copy()->subDays($d)->toDateString(),
                        'created_at' => $now, 'updated_at' => $now,
                    ]);
                }
            }
        }

        foreach ([['Beli galon air', 0], ['Kirim laporan mingguan', 0], ['Servis motor', 1]] as [$title, $plus]) {
            DB::table('todos')->insert([
                'user_id' => $user->id, 'title' => $title, 'date' => $now->copy()->addDays($plus)->toDateString(),
                'created_at' => $now, 'updated_at' => $now,
            ]);
        }

        DB::table('follow_ups')->insert([
            ['user_id' => $user->id, 'title' => 'Tanya kabar proposal', 'status' => 'INBOX', 'person' => 'Pak Andi', 'note' => null, 'created_at' => $now, 'updated_at' => $now],
            ['user_id' => $user->id, 'title' => 'Kirim revisi dokumen', 'status' => 'INBOX', 'person' => 'Bu Sari', 'note' => 'Data contoh untuk peninjau.', 'created_at' => $now, 'updated_at' => $now],
        ]);
    }
}
