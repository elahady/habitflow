<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tahap 32 langkah 5: token yang dibatasi. `scope` kosong berarti akses penuh (semua token yang sudah ada);
     * isinya nama cakupan yang hanya boleh memanggil sebagian kecil rute (lihat UserToken::permits).
     */
    public function up(): void
    {
        Schema::table('user_tokens', function (Blueprint $table) {
            $table->string('scope', 32)->nullable()->after('name');
        });
    }

    public function down(): void
    {
        Schema::table('user_tokens', function (Blueprint $table) {
            $table->dropColumn('scope');
        });
    }
};
