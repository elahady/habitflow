<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Yang disimpan hanya hash SHA-256 token. Token asli hanya tampil sekali saat dibuat.
        Schema::create('api_tokens', function (Blueprint $table) {
            $table->id();
            $table->string('name')->unique();
            $table->char('token_hash', 64)->unique();
            $table->timestamp('last_used_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('api_tokens');
    }
};
