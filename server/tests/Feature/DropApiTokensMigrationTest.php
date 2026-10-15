<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use RuntimeException;
use Tests\TestCase;

/** Migrasi 31L yang menghapus api_tokens harus menolak jalan selagi ada snapshot yang belum diklaim ke akun. */
class DropApiTokensMigrationTest extends TestCase
{
    use RefreshDatabase;

    private function migration(): Migration
    {
        return require database_path('migrations/2026_10_14_100000_drop_api_tokens.php');
    }

    /** Kembali ke bentuk sebelum 31L: tabel api_tokens dan kolom snapshots.api_token_id ada. */
    private function bukaKembali(): void
    {
        $this->migration()->down();
        $this->assertTrue(Schema::hasTable('api_tokens'));
        $this->assertTrue(Schema::hasColumn('snapshots', 'api_token_id'));
    }

    private function snapshot(?int $userId, ?int $tokenId): void
    {
        DB::table('snapshots')->insert([
            'api_token_id' => $tokenId, 'user_id' => $userId, 'schema_version' => 1, 'bytes' => 1,
            'sha256' => str_repeat('a', 64), 'path' => 'snapshots/x.enc', 'created_at' => now(), 'updated_at' => now(),
        ]);
    }

    public function test_database_baru_tidak_punya_api_tokens(): void
    {
        $this->assertFalse(Schema::hasTable('api_tokens'));
        $this->assertFalse(Schema::hasColumn('snapshots', 'api_token_id'));
    }

    public function test_menolak_jalan_kalau_ada_snapshot_yang_belum_diklaim(): void
    {
        $this->bukaKembali();
        $tokenId = DB::table('api_tokens')->insertGetId([
            'name' => 'hp-pemilik', 'token_hash' => str_repeat('c', 64), 'created_at' => now(), 'updated_at' => now(),
        ]);
        $this->snapshot(null, $tokenId);

        try {
            $this->migration()->up();
            $this->fail('Migrasi seharusnya menolak jalan.');
        } catch (RuntimeException $e) {
            $this->assertStringContainsString('1 snapshot tanpa akun', $e->getMessage());
        }

        $this->assertTrue(Schema::hasTable('api_tokens'), 'Tabel tidak boleh hilang kalau migrasi menolak.');
        $this->assertSame(1, DB::table('snapshots')->count());
    }

    public function test_jalan_kalau_semua_snapshot_sudah_punya_akun(): void
    {
        $this->bukaKembali();
        $user = User::create(['google_sub' => 'sub-1', 'email' => 'a@contoh.com', 'name' => 'A']);
        $tokenId = DB::table('api_tokens')->insertGetId([
            'name' => 'hp-pemilik', 'token_hash' => str_repeat('c', 64), 'created_at' => now(), 'updated_at' => now(),
        ]);
        $this->snapshot($user->id, $tokenId);
        $this->snapshot($user->id, null);

        $this->migration()->up();

        $this->assertFalse(Schema::hasTable('api_tokens'));
        $this->assertFalse(Schema::hasColumn('snapshots', 'api_token_id'));
        $this->assertSame(2, DB::table('snapshots')->where('user_id', $user->id)->count(), 'Snapshot akun tetap ada.');
    }
}
