<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Follow-up kerja (tahap 27): pindah dari field snapshot 19B jadi tabel sungguhan.
        // Kolom mengikuti FollowUp di Android (docs/concept.md tahap 19 dan 27).
        Schema::create('follow_ups', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('title');
            $table->string('status', 10)->default('INBOX');
            $table->date('date')->nullable();
            $table->time('time')->nullable();
            $table->string('person')->nullable();
            $table->text('note')->nullable();
            $table->timestamp('done_at')->nullable();
            $table->date('picked_date')->nullable();
            $table->unsignedBigInteger('event_id')->nullable();
            $table->date('event_date')->nullable();
            $table->string('event_title')->nullable();
            $table->timestamps();

            $table->index(['user_id', 'status']);
            $table->index(['user_id', 'date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('follow_ups');
    }
};
