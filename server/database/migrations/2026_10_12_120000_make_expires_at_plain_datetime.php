<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31D: kolom `expires_at` bertipe TIMESTAMP NOT NULL yang menjadi kolom timestamp pertama di
     * tabelnya otomatis diberi "ON UPDATE CURRENT_TIMESTAMP" oleh MySQL lama (5.7) dan MariaDB
     * (explicit_defaults_for_timestamp mati). Akibatnya setiap UPDATE pada baris itu (misalnya hitungan
     * percobaan kode bertambah) menimpa waktu kedaluwarsa dengan "sekarang", sehingga satu tebakan
     * salah langsung menghanguskan kode email. DATETIME tidak punya perilaku itu dan sama di semua versi.
     */
    public function up(): void
    {
        foreach (['email_codes', 'team_invites'] as $name) {
            Schema::table($name, function (Blueprint $table) {
                $table->dateTime('expires_at')->nullable(false)->change();
            });
        }
    }

    public function down(): void
    {
        foreach (['email_codes', 'team_invites'] as $name) {
            Schema::table($name, function (Blueprint $table) {
                $table->timestamp('expires_at')->nullable(false)->change();
            });
        }
    }
};
