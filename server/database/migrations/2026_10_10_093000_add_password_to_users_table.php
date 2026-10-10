<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Revisi arsitektur (10 Oktober 2026): login utama kembali ke email/password.
     * Google jadi fitur "connect" opsional setelah akun ada, bukan cara login langsung
     * di web lagi (API Android tetap Google-only, tidak berubah). google_sub jadi
     * nullable karena tidak semua user akan connect Google.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('password')->nullable()->after('email');
        });

        Schema::table('users', function (Blueprint $table) {
            $table->string('google_sub')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn('password');
            $table->string('google_sub')->nullable(false)->change();
        });
    }
};
