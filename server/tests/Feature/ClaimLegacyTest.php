<?php

namespace Tests\Feature;

use App\Models\ApiToken;
use App\Models\Snapshot;
use App\Models\User;
use App\Models\UserToken;
use App\Services\AccountDeletionService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/** Perintah `habitflow:claim-legacy` (tahap 31E): snapshot token 19B pindah ke akun pemilik HP. */
class ClaimLegacyTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('local');
    }

    private function user(string $email): User
    {
        return User::create(['google_sub' => 'sub-'.$email, 'email' => $email, 'name' => $email]);
    }

    /** Snapshot lama milik token 19B, lengkap dengan berkasnya. Mengembalikan baris snapshot. */
    private function legacySnapshot(ApiToken $token, int $n = 1, string $isi = 'isi-lama'): Snapshot
    {
        $path = 'snapshots/'.$token->id.'/lama-'.$n.'.enc';
        Storage::disk('local')->put($path, $isi);

        return Snapshot::create([
            'api_token_id' => $token->id, 'schema_version' => 1, 'bytes' => strlen($isi),
            'sha256' => hash('sha256', $isi.$n), 'path' => $path,
        ]);
    }

    public function test_klaim_menautkan_snapshot_ke_akun_dan_terbaca_lewat_api(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token, $plainLama] = ApiToken::issue('hp-pemilik');
        [, $plainBaru] = UserToken::issue($user, 'hp');
        $body = json_encode(['schemaVersion' => 1, 'data' => ['habits' => []]]);
        $snap = $this->legacySnapshot($token, 1, Crypt::encryptString($body));

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])->assertSuccessful();

        $this->assertSame($user->id, $snap->fresh()->user_id);
        $this->getJson('/api/v1/snapshot/latest', ['Authorization' => 'Bearer '.$plainBaru])->assertOk();
        // Token lamanya sudah tidak diterima, walau barisnya masih ada (jalan mundur sampai 31L).
        $this->getJson('/api/v1/snapshot/latest', ['Authorization' => 'Bearer '.$plainLama])->assertStatus(401);
        $this->assertDatabaseHas('api_tokens', ['name' => 'hp-pemilik']);
    }

    public function test_dry_run_tidak_mengubah_apa_pun(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token);

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com', '--dry-run' => true])
            ->expectsOutputToContain('1 belum punya akun')
            ->assertSuccessful();

        $this->assertNull(Snapshot::first()->user_id);
        $this->assertSame(0, $user->snapshots()->count());
    }

    public function test_klaim_idempoten(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token, 1);
        $this->legacySnapshot($token, 2);

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])->assertSuccessful();
        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])
            ->expectsOutputToContain('0 belum punya akun')
            ->assertSuccessful();

        $this->assertSame(2, $user->snapshots()->count());
        $this->assertCount(2, Storage::disk('local')->allFiles('snapshots'));
    }

    public function test_dua_hp_ke_dua_akun_dan_tidak_saling_menimpa(): void
    {
        $pemilik = $this->user('pemilik@example.com');
        $istri = $this->user('istri@example.com');
        [$tokenA] = ApiToken::issue('hp-pemilik');
        [$tokenB] = ApiToken::issue('hp-istri');
        $this->legacySnapshot($tokenA, 1);
        $this->legacySnapshot($tokenB, 2);

        // Ada dua token: harus menyebut yang mana.
        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])
            ->expectsOutputToContain('hp-pemilik, hp-istri')->assertFailed();
        $this->assertSame(0, Snapshot::whereNotNull('user_id')->count());

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com', '--token' => 'hp-pemilik'])->assertSuccessful();
        $this->artisan('habitflow:claim-legacy', ['email' => 'istri@example.com', '--token' => 'hp-istri'])->assertSuccessful();

        $this->assertSame(1, $pemilik->snapshots()->count());
        $this->assertSame(1, $istri->snapshots()->count());
        $this->assertSame($tokenA->id, $pemilik->snapshots()->first()->api_token_id);
    }

    public function test_snapshot_yang_sudah_milik_akun_lain_tidak_dirampas(): void
    {
        $pemilik = $this->user('pemilik@example.com');
        $istri = $this->user('istri@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token, 1);

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])->assertSuccessful();
        $this->artisan('habitflow:claim-legacy', ['email' => 'istri@example.com'])->assertSuccessful();

        $this->assertSame(1, $pemilik->snapshots()->count());
        $this->assertSame(0, $istri->snapshots()->count());
    }

    public function test_akun_atau_token_tidak_ada_gagal_tanpa_mengubah_data(): void
    {
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token);

        $this->artisan('habitflow:claim-legacy', ['email' => 'tidak-ada@example.com'])->assertFailed();

        $this->user('pemilik@example.com');
        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com', '--token' => 'tidak-ada'])->assertFailed();

        $this->assertNull(Snapshot::first()->user_id);
    }

    public function test_email_dicocokkan_tanpa_peduli_huruf_besar(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token);

        $this->artisan('habitflow:claim-legacy', ['email' => ' Pemilik@Example.com '])->assertSuccessful();

        $this->assertSame(1, $user->snapshots()->count());
    }

    public function test_batas_14_salinan_tetap_berlaku_setelah_klaim(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        for ($i = 1; $i <= 16; $i++) {
            $this->legacySnapshot($token, $i);
        }

        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])->assertSuccessful();

        $this->assertSame(14, $user->snapshots()->count());
        $this->assertCount(14, Storage::disk('local')->allFiles('snapshots'));
    }

    public function test_snapshot_yang_sudah_diklaim_ikut_terhapus_saat_akun_dihapus(): void
    {
        $user = $this->user('pemilik@example.com');
        [$token] = ApiToken::issue('hp-pemilik');
        $this->legacySnapshot($token);
        $this->artisan('habitflow:claim-legacy', ['email' => 'pemilik@example.com'])->assertSuccessful();

        app(AccountDeletionService::class)->delete($user, AccountDeletionService::SOURCE_APP);

        $this->assertSame(0, Snapshot::count());
        $this->assertSame([], Storage::disk('local')->allFiles());
    }
}
