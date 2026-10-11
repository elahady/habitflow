<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31C: penghitung pemutusan sesi web. Sesi browser menyimpan nilainya saat login;
     * "Keluar dari semua perangkat", ganti kata sandi, dan reset menaikkan angka ini, sehingga
     * semua sesi browser lama ditolak. Cookie "ingat saya" saja tidak cukup karena sesi file
     * tetap hidup selama cookie sesinya ada.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->unsignedInteger('session_epoch')->default(0)->after('remember_token');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn('session_epoch');
        });
    }
};
