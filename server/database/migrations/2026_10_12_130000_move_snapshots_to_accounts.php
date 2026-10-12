<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31E: snapshot pindah dari token per-HP (api_tokens) ke akun (snapshots.user_id).
     * Snapshot baru tidak punya token, jadi api_token_id boleh kosong. Kolomnya (dan tabel api_tokens)
     * sengaja tidak dihapus sampai 31L selesai, supaya rilis server sebelumnya masih bisa dipakai kalau
     * harus jalan mundur.
     */
    public function up(): void
    {
        Schema::table('snapshots', function (Blueprint $table) {
            $table->dropForeign(['api_token_id']);
            $table->dropIndex(['api_token_id', 'id']);
        });
        Schema::table('snapshots', function (Blueprint $table) {
            $table->foreignId('api_token_id')->nullable()->change();
            $table->foreign('api_token_id')->references('id')->on('api_tokens')->cascadeOnDelete();
            $table->index(['api_token_id', 'id']);
        });
    }

    public function down(): void
    {
        Schema::table('snapshots', function (Blueprint $table) {
            $table->dropForeign(['api_token_id']);
        });
        // Snapshot milik akun yang tidak pernah punya token tidak bisa ditampung kolom wajib isi.
        DB::table('snapshots')->whereNull('api_token_id')->delete();
        Schema::table('snapshots', function (Blueprint $table) {
            $table->foreignId('api_token_id')->nullable(false)->change();
            $table->foreign('api_token_id')->references('id')->on('api_tokens')->cascadeOnDelete();
        });
    }
};
