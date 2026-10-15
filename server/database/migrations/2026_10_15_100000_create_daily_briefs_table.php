<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Ringkasan harian yang ditulis Claude lewat skill (tahap 32): satu baris per user per tanggal.
        // Sengaja terpisah dari work_days/snapshot 19B supaya Android tidak bisa menimpanya dengan
        // data lama saat sinkron (alasan yang sama dengan follow_ups di tahap 27). `email_digest`
        // memuat ringkasan email (pengirim dan subjek orang lain), jadi dihapus bersama akun.
        Schema::create('daily_briefs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('date');
            $table->text('email_digest')->nullable();
            $table->text('meeting_note')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('daily_briefs');
    }
};
