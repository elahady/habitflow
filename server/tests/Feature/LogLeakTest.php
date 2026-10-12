<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/**
 * Penjaga tahap 31D: isi permintaan (snapshot, kata sandi, kode) tidak boleh masuk log aplikasi,
 * termasuk saat permintaannya ditolak atau memicu galat server. Saat tahap 30 menambah endpoint
 * berat dan tekanan darah, tambahkan permintaannya ke tes ini.
 */
class LogLeakTest extends TestCase
{
    use RefreshDatabase;

    private const SECRET = 'RAHASIA-UJI-JANGAN-DI-LOG';

    private string $logPath;

    protected function setUp(): void
    {
        parent::setUp();
        $this->logPath = sys_get_temp_dir().'/habitflow-log-uji-'.uniqid().'.log';
        config([
            'logging.default' => 'single',
            'logging.channels.single.path' => $this->logPath,
            'logging.channels.single.level' => 'debug',
        ]);
        Storage::fake('local');
    }

    protected function tearDown(): void
    {
        @unlink($this->logPath);
        parent::tearDown();
    }

    private function log(): string
    {
        return is_file($this->logPath) ? file_get_contents($this->logPath) : '';
    }

    /** Token API sebuah akun (snapshot terikat akun sejak 31E). */
    private function tokenAkun(): string
    {
        $user = User::create(['google_sub' => 'sub-log', 'email' => 'log@example.com', 'name' => 'Log']);
        [, $plain] = UserToken::issue($user, 'hp-uji');

        return $plain;
    }

    private function kirimSnapshot(string $body, string $plain)
    {
        return $this->call('PUT', '/api/v1/snapshot', [], [], [], $this->transformHeadersToServerVars([
            'Authorization' => 'Bearer '.$plain, 'Content-Type' => 'application/json', 'Accept' => 'application/json',
        ]), $body);
    }

    public function test_isi_snapshot_tidak_masuk_log_walau_diterima_ditolak_atau_memicu_galat(): void
    {
        $plain = $this->tokenAkun();

        // Diterima.
        $this->kirimSnapshot(json_encode(['schemaVersion' => 1, 'data' => ['berat' => self::SECRET, 'tensi' => self::SECRET]]), $plain)->assertOk();
        // Ditolak: bukan JSON, struktur salah, terlalu besar.
        $this->kirimSnapshot('bukan-json '.self::SECRET, $plain)->assertStatus(422);
        $this->kirimSnapshot(json_encode(['schemaVersion' => 'x', 'data' => [self::SECRET]]), $plain)->assertStatus(422);
        $this->kirimSnapshot(json_encode(['schemaVersion' => 1, 'data' => ['x' => str_repeat(self::SECRET, 400000)]]), $plain)->assertStatus(413);
        // Token salah.
        $this->kirimSnapshot(json_encode(['schemaVersion' => 1, 'data' => ['x' => self::SECRET]]), 'token-salah')->assertStatus(401);

        $this->assertStringNotContainsString(self::SECRET, $this->log());
    }

    public function test_galat_server_saat_menyimpan_snapshot_tidak_membocorkan_isi_ke_log(): void
    {
        $plain = $this->tokenAkun();
        // Penyimpanan rusak: galat 500 sungguhan, lengkap dengan jejak tumpukan yang dicatat Laravel.
        Storage::shouldReceive('disk')->andThrow(new \RuntimeException('disk rusak'));

        $this->kirimSnapshot(json_encode(['schemaVersion' => 1, 'data' => ['berat' => self::SECRET]]), $plain)->assertStatus(500);

        $log = $this->log();
        $this->assertStringContainsString('disk rusak', $log, 'Galatnya harus tercatat supaya tes ini bermakna.');
        $this->assertStringNotContainsString(self::SECRET, $log);
    }

    public function test_kata_sandi_dan_kode_tidak_masuk_log_saat_masuk_daftar_dan_hapus_akun(): void
    {
        $this->postJson('/api/v1/auth/login', ['email' => 'a@example.com', 'password' => self::SECRET, 'device_name' => 'hp'])->assertStatus(422);
        $this->postJson('/api/v1/auth/register', [
            'name' => 'A', 'email' => 'a@example.com', 'password' => self::SECRET, 'password_confirmation' => self::SECRET,
            'privacy_policy_version' => '1',
        ]);
        $this->post('/login', ['email' => 'a@example.com', 'password' => self::SECRET]);
        $this->post('/hapus-akun/konfirmasi', ['email' => 'a@example.com', 'code' => self::SECRET]);
        $this->postJson('/api/v1/auth/reset-password', [
            'email' => 'a@example.com', 'code' => '000000', 'password' => self::SECRET, 'password_confirmation' => self::SECRET,
        ]);

        $this->assertStringNotContainsString(self::SECRET, $this->log());
    }
}
