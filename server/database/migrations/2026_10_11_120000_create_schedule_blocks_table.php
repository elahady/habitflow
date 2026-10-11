<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Jadwal harian (tahap 29): kolom mengikuti ScheduleBlockEntity di Android (tahap 17).
        // start_type 0 = jam tetap (start_value = menit sejak 00.00), 1 = patokan waktu sholat
        // (prayer + start_value sebagai selisih menit, boleh negatif). active_days adalah bitmask
        // Senin = 1 sampai Minggu = 64.
        Schema::create('schedule_blocks', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->unsignedTinyInteger('start_type')->default(0);
            $table->integer('start_value');
            $table->string('prayer', 10)->nullable();
            $table->unsignedInteger('duration_minutes')->default(0);
            $table->unsignedSmallInteger('end_minute_of_day')->nullable();
            $table->unsignedTinyInteger('active_days')->default(127);
            $table->string('level', 10)->default('INFO');
            $table->unsignedInteger('sort_order')->default(0);
            $table->string('work_action', 10)->nullable();
            $table->boolean('work_reminders')->default(false);
            $table->timestamps();

            $table->index(['user_id', 'sort_order']);
        });

        // Tautan blok ke habit; hilang otomatis kalau blok atau habitnya dihapus.
        Schema::create('schedule_block_habits', function (Blueprint $table) {
            $table->foreignId('schedule_block_id')->constrained()->cascadeOnDelete();
            $table->foreignId('habit_id')->constrained()->cascadeOnDelete();
            $table->primary(['schedule_block_id', 'habit_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('schedule_block_habits');
        Schema::dropIfExists('schedule_blocks');
    }
};
