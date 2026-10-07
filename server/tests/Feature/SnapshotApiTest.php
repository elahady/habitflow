<?php

namespace Tests\Feature;

use App\Models\ApiToken;
use App\Models\Snapshot;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

class SnapshotApiTest extends TestCase
{
    use RefreshDatabase;

    private string $plain;

    private ApiToken $token;

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('local');
        [$this->token, $this->plain] = ApiToken::issue('hp-uji');
    }

    private function auth(?string $plain = null): array
    {
        return ['Authorization' => 'Bearer '.($plain ?? $this->plain)];
    }

    private function snapshot(array $overrides = []): array
    {
        return array_replace_recursive([
            'schemaVersion' => 1,
            'createdAt' => 1791350953356,
            'deviceId' => 'perangkat-uji',
            'appVersion' => '1.0',
            'data' => ['habits' => [['id' => 1, 'name' => 'RAHASIA-HABIT-UJI']], 'todos' => []],
        ], $overrides);
    }

    private function unggah(array $payload, ?string $plain = null)
    {
        return $this->call('PUT', '/api/v1/snapshot', [], [], [], $this->transformHeadersToServerVars(
            $this->auth($plain) + ['Content-Type' => 'application/json', 'Accept' => 'application/json'],
        ), json_encode($payload));
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/ping')->assertStatus(401);
        $this->putJson('/api/v1/snapshot', $this->snapshot())->assertStatus(401);
        $this->getJson('/api/v1/snapshot/latest')->assertStatus(401);
    }

    public function test_token_salah_ditolak(): void
    {
        $this->getJson('/api/v1/ping', $this->auth('salah'))->assertStatus(401);
    }

    public function test_ping_dengan_token_sah(): void
    {
        $this->getJson('/api/v1/ping', $this->auth())->assertOk()->assertJson(['ok' => true]);
    }

    public function test_hanya_hash_token_yang_disimpan(): void
    {
        $this->assertSame(hash('sha256', $this->plain), $this->token->token_hash);
        $this->assertDatabaseMissing('api_tokens', ['token_hash' => $this->plain]);
    }

    public function test_terlalu_banyak_percobaan_salah_diblokir(): void
    {
        for ($i = 0; $i < 10; $i++) {
            $this->getJson('/api/v1/ping', $this->auth('salah'.$i))->assertStatus(401);
        }
        $this->getJson('/api/v1/ping', $this->auth('salah-lagi'))->assertStatus(429);
        // Alamat yang sama diblokir juga untuk token yang sah selama jendela percobaan gagal masih berlaku.
        $this->getJson('/api/v1/ping', $this->auth())->assertStatus(429);
    }

    public function test_unggah_snapshot_tersimpan_terenkripsi_dan_bisa_diambil_persis(): void
    {
        $payload = $this->snapshot();

        $this->unggah($payload)->assertOk()->assertJson(['unchanged' => false])->assertJsonStructure(['id', 'receivedAt', 'bytes']);

        $row = Snapshot::firstOrFail();
        $this->assertSame('perangkat-uji', $row->device_id);
        $this->assertSame(1, $row->schema_version);

        // Berkas di storage tidak boleh terbaca sebagai teks biasa.
        $stored = Storage::disk('local')->get($row->path);
        $this->assertStringNotContainsString('RAHASIA-HABIT-UJI', $stored);

        $response = $this->get('/api/v1/snapshot/latest', $this->auth() + ['Accept' => 'application/json']);
        $response->assertOk();
        $this->assertSame(json_encode($payload), $response->getContent());
        $this->assertNotEmpty($response->headers->get('X-Received-At'));
    }

    public function test_unggah_isi_yang_sama_tidak_menulis_ulang(): void
    {
        $payload = $this->snapshot();
        $this->unggah($payload)->assertOk();
        $this->unggah($payload)->assertOk()->assertJson(['unchanged' => true]);

        $this->assertSame(1, Snapshot::count());
        $this->assertCount(1, Storage::disk('local')->allFiles('snapshots'));
    }

    public function test_isi_berubah_membuat_snapshot_baru(): void
    {
        $this->unggah($this->snapshot())->assertOk();
        $this->unggah($this->snapshot(['data' => ['todos' => [['id' => 9]]]]))->assertOk()->assertJson(['unchanged' => false]);

        $this->assertSame(2, Snapshot::count());
    }

    public function test_hanya_14_snapshot_terakhir_disimpan(): void
    {
        for ($i = 1; $i <= 16; $i++) {
            $this->unggah($this->snapshot(['createdAt' => $i]))->assertOk();
        }

        $this->assertSame(14, Snapshot::count());
        $this->assertCount(14, Storage::disk('local')->allFiles('snapshots'));
        // Yang terbaru tetap bisa diambil.
        $latest = $this->get('/api/v1/snapshot/latest', $this->auth() + ['Accept' => 'application/json']);
        $this->assertSame(16, json_decode($latest->getContent(), true)['createdAt']);
    }

    public function test_isi_bukan_json_ditolak(): void
    {
        $this->call('PUT', '/api/v1/snapshot', [], [], [], $this->transformHeadersToServerVars(
            $this->auth() + ['Content-Type' => 'application/json', 'Accept' => 'application/json'],
        ), 'ini bukan json')->assertStatus(422);
        $this->assertSame(0, Snapshot::count());
    }

    public function test_tanpa_schema_version_atau_data_ditolak(): void
    {
        $this->unggah(['data' => []])->assertStatus(422);
        $this->unggah(['schemaVersion' => 1])->assertStatus(422);
        $this->unggah(['schemaVersion' => 'satu', 'data' => ['a' => 1]])->assertStatus(422);
        $this->unggah(['schemaVersion' => 0, 'data' => ['a' => 1]])->assertStatus(422);
        $this->unggah(['schemaVersion' => 1, 'data' => [1, 2, 3]])->assertStatus(422);
        $this->assertSame(0, Snapshot::count());
    }

    public function test_terlalu_besar_ditolak(): void
    {
        $big = $this->snapshot(['data' => ['blob' => str_repeat('x', 5 * 1024 * 1024)]]);

        $this->unggah($big)->assertStatus(413);
        $this->assertSame(0, Snapshot::count());
    }

    public function test_belum_ada_snapshot_menjawab_404(): void
    {
        $this->getJson('/api/v1/snapshot/latest', $this->auth())->assertStatus(404);
    }

    public function test_token_lain_tidak_bisa_membaca_snapshot_orang_lain(): void
    {
        $this->unggah($this->snapshot())->assertOk();
        [, $other] = ApiToken::issue('hp-lain');

        $this->getJson('/api/v1/snapshot/latest', $this->auth($other))->assertStatus(404);
    }

    public function test_dibatasi_60_permintaan_per_menit(): void
    {
        for ($i = 0; $i < 60; $i++) {
            $this->getJson('/api/v1/ping', $this->auth())->assertOk();
        }
        $this->getJson('/api/v1/ping', $this->auth())->assertStatus(429);
    }

    public function test_perintah_artisan_membuat_dan_mencabut_token(): void
    {
        $this->artisan('habitflow:token', ['name' => 'hp-baru'])->assertSuccessful();
        $this->assertDatabaseHas('api_tokens', ['name' => 'hp-baru']);

        $this->artisan('habitflow:token', ['name' => 'hp-baru'])->assertFailed();

        $this->artisan('habitflow:token', ['name' => 'hp-baru', '--revoke' => true])->assertSuccessful();
        $this->assertDatabaseMissing('api_tokens', ['name' => 'hp-baru']);
    }

    public function test_mencabut_token_menghapus_snapshot_dan_berkasnya(): void
    {
        $this->unggah($this->snapshot())->assertOk();
        $this->assertCount(1, Storage::disk('local')->allFiles('snapshots'));

        $this->artisan('habitflow:token', ['name' => 'hp-uji', '--revoke' => true])->assertSuccessful();

        $this->assertSame(0, Snapshot::count());
        $this->assertCount(0, Storage::disk('local')->allFiles('snapshots'));
        $this->getJson('/api/v1/ping', $this->auth())->assertStatus(401);
    }
}
