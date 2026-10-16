<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Tanggal libur efektif dari Android (tahap 29 langkah 5): libur manual ("Hari ini libur") digabung libur nasional
        // yang tidak dibatalkan, sudah dihitung di HP. Server tidak punya daftar libur nasional dan tidak membedakan
        // sumbernya; ia hanya tahu "tanggal ini libur" supaya kartu jadwal di web menyembunyikan blok hari kerja.
        Schema::create('days_off', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('date');
            $table->timestamps();

            $table->unique(['user_id', 'date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('days_off');
    }
};
