<?php

namespace App\Services;

use App\Models\Snapshot;
use App\Models\Team;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;

/**
 * Hapus akun menyeluruh (tahap 31D): langsung, permanen, dalam satu transaksi. Semua tabel di
 * inventaris data (docs/rilis-play-store.md bagian 1) ikut terhapus; tabel baru wajib masuk daftar
 * di sini atau di daftar "bukan data pengguna" di tests/Feature/SchemaInventoryTest.php, kalau
 * tidak tesnya gagal. Berkas snapshot dihapus setelah transaksi berhasil (berkas tidak bisa
 * dibatalkan, jadi tidak boleh hilang kalau transaksinya gagal).
 */
class AccountDeletionService
{
    public const SOURCE_APP = 'app';

    public const SOURCE_WEB = 'web';

    public const SOURCE_WEB_CODE = 'web-code';

    public function delete(User $user, string $source): void
    {
        $paths = [];

        DB::transaction(function () use ($user, $source, &$paths) {
            $paths = Snapshot::where('user_id', $user->id)->pluck('path')->filter()->all();

            $this->settleTeams($user);

            // Anggota biasa hanya keluar. To-do tim buatan pengguna tetap ada, pembuatnya dikosongkan.
            DB::table('team_todos')->where('created_by', $user->id)->update(['created_by' => null]);
            DB::table('team_todos')->where('assigned_to', $user->id)->update(['assigned_to' => null]);
            DB::table('team_todos')->where('done_by', $user->id)->update(['done_by' => null]);
            DB::table('team_invites')->where('created_by', $user->id)->delete(); // undangan aktif hangus
            DB::table('team_invites')->where('used_by', $user->id)->update(['used_by' => null]);
            DB::table('team_members')->where('user_id', $user->id)->delete();

            $habitIds = DB::table('habits')->where('user_id', $user->id)->pluck('id');
            $blockIds = DB::table('schedule_blocks')->where('user_id', $user->id)->pluck('id');
            DB::table('habit_entries')->whereIn('habit_id', $habitIds)->delete();
            DB::table('schedule_block_habits')->whereIn('schedule_block_id', $blockIds)->orWhereIn('habit_id', $habitIds)->delete();
            DB::table('schedule_blocks')->where('user_id', $user->id)->delete();
            DB::table('habits')->where('user_id', $user->id)->delete();
            foreach (['todos', 'follow_ups', 'prayer_days', 'daily_briefs', 'work_days', 'snapshots', 'email_codes', 'user_tokens'] as $table) {
                DB::table($table)->where('user_id', $user->id)->delete();
            }

            // Email yang menunggu terkirim atau gagal terkirim memuat alamat dan kode (tabel jobs dan failed_jobs).
            // Dicocokkan di PHP (bukan LIKE) supaya "_" dan "%" di alamat email tidak jadi karakter pengganti.
            foreach (['jobs', 'failed_jobs'] as $table) {
                $ids = DB::table($table)->select('id', 'payload')->get()
                    ->filter(fn ($row) => str_contains($row->payload, $user->email))
                    ->pluck('id');
                DB::table($table)->whereIn('id', $ids)->delete();
            }

            DB::table('users')->where('id', $user->id)->delete();

            // Bukti penghapusan tanpa data pribadi: tidak ada id, nama, atau email.
            DB::table('account_deletions')->insert(['source' => $source, 'deleted_at' => now()]);
        });

        $failed = 0;
        foreach ($paths as $path) {
            if (Storage::disk('local')->exists($path) && ! Storage::disk('local')->delete($path)) {
                $failed++;
            }
        }
        if ($failed > 0) {
            Log::error('Hapus akun: berkas snapshot gagal dihapus dan harus dibersihkan manual.', ['jumlah' => $failed]);
        }
    }

    /**
     * Tim yang dibuat pengguna: ada anggota lain, kepemilikan pindah ke anggota terlama (yang
     * paling dulu bergabung); tidak ada, tim dan isinya dihapus.
     */
    private function settleTeams(User $user): void
    {
        foreach (Team::where('created_by', $user->id)->get() as $team) {
            $successor = DB::table('team_members')
                ->where('team_id', $team->id)
                ->where('user_id', '!=', $user->id)
                ->orderBy('created_at')
                ->orderBy('id')
                ->value('user_id');

            if ($successor) {
                $team->forceFill(['created_by' => $successor])->save();

                continue;
            }

            DB::table('team_todos')->where('team_id', $team->id)->delete();
            DB::table('team_invites')->where('team_id', $team->id)->delete();
            DB::table('team_members')->where('team_id', $team->id)->delete();
            $team->delete();
        }
    }
}
