<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Catatan harian kerja (tahap 33 langkah 3): satu baris per user per tanggal, sama dengan `work_days` di
        // Android (catatan EOD dan kapan daily scrum dan EOD diselesaikan). Beda dari `daily_briefs` (ditulis
        // Claude): ini ditulis pemilik, dari HP atau dari web, dan PUT dicek konfliknya seperti follow-up.
        // `eod_note` bisa memuat nama orang lain dan isi pekerjaan, jadi ikut dihapus bersama akun.
        Schema::create('work_days', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('date');
            $table->text('eod_note')->nullable();
            $table->timestamp('scrum_done_at')->nullable();
            $table->timestamp('eod_done_at')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('work_days');
    }
};
