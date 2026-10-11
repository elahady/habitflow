<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Waktu sholat per tanggal yang sudah dihitung Android (tahap 29, opsi a): server tidak
        // menghitung hisab, hanya menyimpan kiriman HP. `timezone` dipakai web untuk tahu jam
        // "sekarang" di tempat user. Waktu null berarti tidak ada (mis. lintang kutub).
        Schema::create('prayer_days', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('date');
            $table->string('timezone', 64);
            $table->time('subuh')->nullable();
            $table->time('dzuhur')->nullable();
            $table->time('ashar')->nullable();
            $table->time('maghrib')->nullable();
            $table->time('isya')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('prayer_days');
    }
};
