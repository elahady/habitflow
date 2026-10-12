<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31D, persiapan hapus akun:
     * - team_todos.created_by boleh kosong: to-do tim buatan pengguna yang dihapus tetap ada
     *   (pembuatnya tampil sebagai "Pengguna dihapus"), bukan ikut terhapus oleh cascade.
     * - snapshots.user_id: snapshot harus bisa dicari lewat akun supaya ikut terhapus (baris dan
     *   berkas). Diisi oleh 31E saat snapshot pindah dari token per-HP ke akun; sampai itu,
     *   snapshot lama hanya terikat ke api_tokens dan tidak punya pemilik akun.
     * - account_deletions: catatan audit tanpa data pribadi (hanya waktu dan sumber).
     */
    public function up(): void
    {
        Schema::table('team_todos', function (Blueprint $table) {
            $table->dropForeign(['created_by']);
        });
        Schema::table('team_todos', function (Blueprint $table) {
            $table->foreignId('created_by')->nullable()->change();
            $table->foreign('created_by')->references('id')->on('users')->nullOnDelete();
        });

        Schema::table('snapshots', function (Blueprint $table) {
            $table->foreignId('user_id')->nullable()->after('api_token_id')->constrained()->cascadeOnDelete();
        });

        Schema::create('account_deletions', function (Blueprint $table) {
            $table->id();
            $table->string('source', 16);
            $table->timestamp('deleted_at');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('account_deletions');

        Schema::table('snapshots', function (Blueprint $table) {
            $table->dropConstrainedForeignId('user_id');
        });

        Schema::table('team_todos', function (Blueprint $table) {
            $table->dropForeign(['created_by']);
        });
        Schema::table('team_todos', function (Blueprint $table) {
            $table->foreignId('created_by')->nullable(false)->change();
            $table->foreign('created_by')->references('id')->on('users')->cascadeOnDelete();
        });
    }
};
