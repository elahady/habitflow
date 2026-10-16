<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 30 langkah 3: data kesehatan dari Android, satu arah (HP menulis, server menyimpan, web hanya membaca).
     * Berat dan tensi hanya bertambah di HP (tidak ada edit atau hapus), jadi kunci unik `user_id` + `time_millis`
     * cukup untuk membuat pengiriman ulang tidak menggandakan catatan. Ini data kesehatan: ikut dihapus saat akun
     * dihapus (DataInventory, AccountDeletionService) dan masuk inventaris data Play Store.
     */
    public function up(): void
    {
        Schema::create('weight_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->unsignedBigInteger('time_millis');
            $table->decimal('kg', 4, 1);
            $table->timestamps();

            $table->unique(['user_id', 'time_millis']);
        });

        Schema::create('blood_pressure_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->unsignedBigInteger('time_millis');
            $table->unsignedSmallInteger('systolic');
            $table->unsignedSmallInteger('diastolic');
            $table->unsignedSmallInteger('pulse')->nullable();
            $table->string('note', 500)->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'time_millis']);
        });

        // Langkah per tanggal (Health Connect di HP). Hari yang sama ditimpa karena langkah hari ini terus bertambah.
        Schema::create('step_days', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->date('date');
            $table->unsignedInteger('steps');
            $table->timestamps();

            $table->unique(['user_id', 'date']);
        });

        // Satu baris per pengguna: zona waktu dan profil tubuh (tinggi, target), serta fokus terakhir yang dihitung HP
        // lengkap dengan teksnya, supaya web menampilkan kalimat yang sama persis tanpa menyalin aturan. Kolom fokus
        // kosong kalau HP belum pernah mengirimnya.
        Schema::create('health_focus', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->unique()->constrained()->cascadeOnDelete();
            $table->string('timezone', 64);
            $table->decimal('height_cm', 4, 1)->nullable();
            $table->decimal('target_kg', 4, 1)->nullable();
            $table->string('rule', 40)->nullable();
            $table->unsignedTinyInteger('walk_minutes')->nullable();
            $table->string('title', 200)->nullable();
            $table->string('sentence', 600)->nullable();
            $table->string('note', 600)->nullable();
            $table->json('reasons')->nullable();
            $table->timestamp('computed_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('health_focus');
        Schema::dropIfExists('step_days');
        Schema::dropIfExists('blood_pressure_entries');
        Schema::dropIfExists('weight_entries');
    }
};
