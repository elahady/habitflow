<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Metadata saja. Isi snapshot ada di berkas terenkripsi di storage ($path).
        Schema::create('snapshots', function (Blueprint $table) {
            $table->id();
            $table->foreignId('api_token_id')->constrained()->cascadeOnDelete();
            $table->string('device_id')->nullable();
            $table->unsignedInteger('schema_version');
            $table->unsignedBigInteger('bytes');
            $table->char('sha256', 64);
            $table->string('path');
            $table->unsignedBigInteger('client_created_at')->nullable();
            $table->timestamps();

            $table->index(['api_token_id', 'id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('snapshots');
    }
};
