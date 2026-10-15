<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31L: token per-HP tahap 19B (api_tokens) dan kolom snapshots.api_token_id dihapus. Sejak 31E API
     * tidak menerima token itu lagi dan snapshot baru terikat ke akun.
     *
     * PENGAMAN: migrasi ini menolak jalan kalau masih ada snapshot tanpa akun (belum diklaim lewat
     * `habitflow:claim-legacy`), karena setelah kolom api_token_id hilang snapshot itu tidak punya pemilik dan
     * tidak bisa diklaim lagi. Perintah klaim hanya ada di rilis server SEBELUM migrasi ini, jadi urutannya:
     * deploy rilis sebelumnya, klaim tiap HP (pemilik, istri), baru deploy rilis ini. Lihat server/DEPLOY.md.
     */
    public function up(): void
    {
        if (Schema::hasTable('snapshots')) {
            $unclaimed = DB::table('snapshots')->whereNull('user_id')->count();
            if ($unclaimed > 0) {
                throw new RuntimeException(
                    "Migrasi dibatalkan: masih ada $unclaimed snapshot tanpa akun. Klaim dulu dengan "
                    .'`php artisan habitflow:claim-legacy <email> --token=<nama>` dari rilis server sebelumnya, lalu ulangi.'
                );
            }
        }

        Schema::table('snapshots', function (Blueprint $table) {
            $table->dropForeign(['api_token_id']);
            $table->dropIndex(['api_token_id', 'id']);
        });
        Schema::table('snapshots', function (Blueprint $table) {
            $table->dropColumn('api_token_id');
        });
        Schema::dropIfExists('api_tokens');
    }

    /** Menghidupkan kembali bentuk tabelnya saja; token dan tautannya tidak bisa dipulihkan. */
    public function down(): void
    {
        Schema::create('api_tokens', function (Blueprint $table) {
            $table->id();
            $table->string('name')->unique();
            $table->char('token_hash', 64)->unique();
            $table->timestamp('last_used_at')->nullable();
            $table->timestamps();
        });
        Schema::table('snapshots', function (Blueprint $table) {
            $table->foreignId('api_token_id')->nullable()->constrained()->cascadeOnDelete();
            $table->index(['api_token_id', 'id']);
        });
    }
};
