<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Angka "X dari Y selesai" untuk riwayat rekap web (tahap 33 sisa). Server tidak bisa merekonstruksinya karena
        // tidak menyimpan daftar hari itu sebelum EOD memindahkan item, jadi HP menghitungnya saat EOD diselesaikan.
        // Keduanya null untuk hari yang belum pernah di-EOD dari HP yang sudah membawa angka ini.
        Schema::table('work_days', function (Blueprint $table) {
            $table->unsignedSmallInteger('eod_done_count')->nullable()->after('eod_done_at');
            $table->unsignedSmallInteger('eod_total_count')->nullable()->after('eod_done_count');
        });
    }

    public function down(): void
    {
        Schema::table('work_days', function (Blueprint $table) {
            $table->dropColumn(['eod_done_count', 'eod_total_count']);
        });
    }
};
