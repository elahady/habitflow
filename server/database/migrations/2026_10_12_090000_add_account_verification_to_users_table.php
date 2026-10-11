<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 31B: login wajib dengan verifikasi email. Akun yang sudah ada saat migrasi ini
     * jalan (pemilik dan istri, sudah dikenal) langsung dianggap terverifikasi satu kali.
     * Akun yang dibuat sesudahnya mulai dari belum terverifikasi.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->timestamp('email_verified_at')->nullable()->after('email');
            $table->timestamp('privacy_consented_at')->nullable()->after('avatar');
            $table->string('privacy_policy_version', 32)->nullable()->after('privacy_consented_at');
        });

        DB::table('users')->whereNull('email_verified_at')->update(['email_verified_at' => now()]);

        Schema::create('email_codes', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('purpose', 16);
            $table->char('code_hash', 64);
            $table->timestamp('expires_at');
            $table->unsignedTinyInteger('attempts')->default(0);
            $table->timestamp('consumed_at')->nullable();
            $table->timestamps();

            $table->index(['user_id', 'purpose']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('email_codes');

        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['email_verified_at', 'privacy_consented_at', 'privacy_policy_version']);
        });
    }
};
