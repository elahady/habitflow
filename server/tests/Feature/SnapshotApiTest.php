<?php

namespace Tests\Feature;

use App\Models\ApiToken;
use App\Models\Snapshot;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/** Snapshot terikat ke akun (user-token) sejak tahap 31E. Token per-HP 19B tidak lagi diterima. */
class SnapshotApiTest extends TestCase
{
    use RefreshDatabase;

    private string $plain;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('local');
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        [, $this->plain] = UserToken::issue($this->user, 'hp-uji');
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

    /** Pengguna kedua lengkap dengan token-nya. */
    private function otherUser(string $email = 'b@example.com'): array
    {
        $other = User::create(['google_sub' => 'sub-'.$email, 'email' => $email, 'name' => 'B']);
        [, $plain] = UserToken::issue($other, 'hp-lain');

        return [$other, $plain];
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

    public function test_terlalu_banyak_percobaan_salah_diblokir(): void
    {
        for ($i = 0; $i < 10; $i++) {
            $this->getJson('/api/v1/ping', $this->auth('salah'.$i))->assertStatus(401);
        }
        $this->getJson('/api/v1/ping', $this->auth('salah-lagi'))->assertStatus(429);
        // Alamat yang sama diblokir juga untuk token yang sah selama jendela percobaan gagal masih berlaku.
        $this->getJson('/api/v1/ping', $this->auth())->assertStatus(429);
    }

    public function test_unggah_snapshot_tersimpan_di_akun_terenkripsi_dan_bisa_diambil_persis(): void
    {
        $payload = $this->snapshot();

        $this->unggah($payload)->assertOk()->assertJson(['unchanged' => false])->assertJsonStructure(['id', 'receivedAt', 'bytes']);

        $row = Snapshot::firstOrFail();
        $this->assertSame($this->user->id, $row->user_id);
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
            $this->unggah($this->snapshot(['createdAt' => $i, 'data' => ['todos' => [['id' => $i]]]]))->assertOk();
        }

        $this->assertSame(14, Snapshot::count());
        $this->assertCount(14, Storage::disk('local')->allFiles('snapshots'));
        // Yang terbaru tetap bisa diambil.
        $latest = $this->get('/api/v1/snapshot/latest', $this->auth() + ['Accept' => 'application/json']);
        $this->assertSame(16, json_decode($latest->getContent(), true)['createdAt']);
    }

    public function test_batas_14_dihitung_per_akun(): void
    {
        [$other, $otherPlain] = $this->otherUser();
        for ($i = 1; $i <= 16; $i++) {
            $this->unggah($this->snapshot(['data' => ['todos' => [['id' => $i]]]]))->assertOk();
        }
        $this->unggah($this->snapshot(['data' => ['todos' => [['id' => 99]]]]), $otherPlain)->assertOk();

        $this->assertSame(14, $this->user->snapshots()->count());
        $this->assertSame(1, $other->snapshots()->count());
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

    public function test_akun_lain_tidak_bisa_membaca_snapshot_orang_lain(): void
    {
        $this->unggah($this->snapshot())->assertOk();
        [, $otherPlain] = $this->otherUser();

        $this->getJson('/api/v1/snapshot/latest', $this->auth($otherPlain))->assertStatus(404);
    }

    public function test_isi_sama_milik_dua_akun_tidak_dianggap_tidak_berubah(): void
    {
        [$other, $otherPlain] = $this->otherUser();
        $payload = $this->snapshot();
        $this->unggah($payload)->assertOk();

        $this->unggah($payload, $otherPlain)->assertOk()->assertJson(['unchanged' => false]);
        $this->assertSame(1, $other->snapshots()->count());
    }

    public function test_keluar_mencabut_token_tapi_snapshot_akun_tetap_ada(): void
    {
        $this->unggah($this->snapshot())->assertOk();
        $this->postJson('/api/v1/auth/logout', [], $this->auth())->assertSuccessful();

        $this->assertSame(1, $this->user->snapshots()->count());
        $this->getJson('/api/v1/snapshot/latest', $this->auth())->assertStatus(401);
    }

    public function test_dibatasi_60_permintaan_per_menit(): void
    {
        for ($i = 0; $i < 60; $i++) {
            $this->getJson('/api/v1/ping', $this->auth())->assertOk();
        }
        $this->getJson('/api/v1/ping', $this->auth())->assertStatus(429);
    }

    public function test_isi_data_sama_tapi_waktu_kirim_beda_dianggap_tidak_berubah(): void
    {
        $this->unggah($this->snapshot(['createdAt' => 1000]))->assertOk()->assertJson(['unchanged' => false]);
        $this->unggah($this->snapshot(['createdAt' => 2000, 'deviceId' => 'perangkat-lain']))
            ->assertOk()->assertJson(['unchanged' => true]);

        $this->assertSame(1, Snapshot::count());
        $this->assertCount(1, Storage::disk('local')->allFiles('snapshots'));
    }
}
