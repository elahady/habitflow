<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Laravel\Passport\Passport;
use phpseclib4\Crypt\RSA;
use Tests\TestCase;

/**
 * MCP Habitflow (login OAuth lewat Passport): pendaftaran klien, alur authorization code + PKCE sampai token,
 * pemanggilan tool, dan pemisahan dari token REST /api/v1.
 */
class McpOAuthTest extends TestCase
{
    use RefreshDatabase;

    private const ACCEPT = ['Accept' => 'application/json, text/event-stream'];

    /** @var array{0: string, 1: string}|null */
    private static ?array $keys = null;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        // Pasangan kunci dibuat sekali per proses dengan phpseclib (ada lewat Socialite): openssl_pkey_new gagal di
        // PHP Windows tanpa openssl.cnf, dan kunci privat tidak boleh disimpan sebagai berkas di repo.
        if (! self::$keys) {
            $private = RSA::createKey(2048);
            self::$keys = [$private->toString('PKCS8'), $private->getPublicKey()->toString('PKCS8')];
        }
        config(['passport.private_key' => self::$keys[0], 'passport.public_key' => self::$keys[1]]);

        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
    }

    private function rpc(string $method, array $params = [], array $headers = self::ACCEPT, int $id = 1)
    {
        return $this->postJson('/mcp', ['jsonrpc' => '2.0', 'id' => $id, 'method' => $method, 'params' => $params], $headers);
    }

    private function callTool(string $name, array $arguments)
    {
        return $this->rpc('tools/call', ['name' => $name, 'arguments' => $arguments]);
    }

    public function test_mcp_tanpa_token_ditolak_dan_menunjuk_ke_metadata_oauth(): void
    {
        $response = $this->rpc('tools/list');

        $response->assertStatus(401);
        $this->assertStringContainsString('oauth-protected-resource', (string) $response->headers->get('WWW-Authenticate'));
    }

    public function test_metadata_oauth_menyebut_endpoint_dan_pkce(): void
    {
        $this->getJson('/.well-known/oauth-authorization-server')
            ->assertOk()
            ->assertJsonPath('code_challenge_methods_supported', ['S256'])
            ->assertJsonPath('scopes_supported', ['mcp:use'])
            ->assertJsonPath('grant_types_supported', ['authorization_code', 'refresh_token'])
            ->assertJsonPath('authorization_endpoint', url('/oauth/authorize'))
            ->assertJsonPath('token_endpoint', url('/oauth/token'))
            ->assertJsonPath('registration_endpoint', url('/oauth/register'));
    }

    public function test_klien_boleh_mendaftar_dengan_alamat_kembali_yang_diizinkan(): void
    {
        foreach (['https://claude.ai/api/mcp/auth_callback', 'http://localhost:54321/callback', 'http://127.0.0.1:8080/callback'] as $uri) {
            $this->postJson('/oauth/register', ['client_name' => 'Claude', 'redirect_uris' => [$uri]])
                ->assertCreated()
                ->assertJsonPath('token_endpoint_auth_method', 'none');
        }
    }

    public function test_klien_dengan_alamat_kembali_asing_ditolak(): void
    {
        foreach (['https://evil.example/cb', 'http://claude.ai.evil.example/cb', 'https://claude.ai.evil.example/cb'] as $uri) {
            $this->postJson('/oauth/register', ['client_name' => 'Claude', 'redirect_uris' => [$uri]])
                ->assertStatus(400)
                ->assertJsonPath('error', 'invalid_redirect_uri');
        }

        $this->assertDatabaseCount('oauth_clients', 0);
    }

    public function test_pendaftaran_klien_dibatasi_lajunya(): void
    {
        $status = null;
        for ($i = 0; $i < 12; $i++) {
            $status = $this->postJson('/oauth/register', ['redirect_uris' => ['https://claude.ai/cb']])->getStatusCode();
        }

        $this->assertSame(429, $status);
    }

    public function test_alur_authorization_code_dengan_pkce_sampai_tool_dipanggil(): void
    {
        $redirect = 'http://localhost:54321/callback';
        $clientId = $this->postJson('/oauth/register', ['client_name' => 'Claude Code', 'redirect_uris' => [$redirect]])
            ->assertCreated()->json('client_id');

        $verifier = bin2hex(random_bytes(32));
        $challenge = rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '=');
        $query = http_build_query([
            'response_type' => 'code', 'client_id' => $clientId, 'redirect_uri' => $redirect,
            'scope' => 'mcp:use', 'state' => 'abc123', 'code_challenge' => $challenge, 'code_challenge_method' => 'S256',
        ]);

        // Belum masuk: diarahkan ke halaman login, tujuan semula diingat.
        $this->get('/oauth/authorize?'.$query)->assertRedirect(route('login'));

        // Sudah masuk: layar persetujuan menampilkan nama klien dan email akun.
        $this->actingAs($this->user)
            ->get('/oauth/authorize?'.$query)
            ->assertOk()
            ->assertSee('Claude Code')
            ->assertSee('a@example.com')
            ->assertSee('Izinkan');

        $authToken = session('authToken');
        $approve = $this->post('/oauth/authorize', ['state' => 'abc123', 'client_id' => $clientId, 'auth_token' => $authToken]);
        $approve->assertRedirect();
        $location = $approve->headers->get('Location');
        parse_str((string) parse_url($location, PHP_URL_QUERY), $back);
        $this->assertSame('abc123', $back['state']);
        $this->assertStringStartsWith($redirect, $location);

        // Tukar kode dengan token (tanpa rahasia klien, hanya PKCE).
        $token = $this->postJson('/oauth/token', [
            'grant_type' => 'authorization_code', 'client_id' => $clientId, 'redirect_uri' => $redirect,
            'code' => $back['code'], 'code_verifier' => $verifier,
        ])->assertOk()->json();
        $this->assertArrayHasKey('refresh_token', $token);

        // Panggilan MCP memakai token saja, tanpa sesi web.
        $this->app['auth']->forgetGuards();
        $auth = ['Authorization' => 'Bearer '.$token['access_token']] + self::ACCEPT;

        $this->rpc('tools/list', [], $auth)->assertOk()->assertJsonFragment(['name' => 'save-daily-brief']);
        $this->rpc('tools/call', ['name' => 'save-daily-brief', 'arguments' => ['date' => '2026-10-21', 'email_digest' => '2 email belum dibaca']], $auth, 2)
            ->assertOk();

        $this->assertSame('2 email belum dibaca', $this->user->dailyBriefs()->where('date', '2026-10-21')->value('email_digest'));
    }

    public function test_kode_otorisasi_tidak_bisa_dipakai_dengan_verifier_salah(): void
    {
        $redirect = 'http://localhost:54321/callback';
        $clientId = $this->postJson('/oauth/register', ['redirect_uris' => [$redirect]])->json('client_id');
        $challenge = rtrim(strtr(base64_encode(hash('sha256', 'verifier-asli', true)), '+/', '-_'), '=');
        $query = http_build_query([
            'response_type' => 'code', 'client_id' => $clientId, 'redirect_uri' => $redirect,
            'scope' => 'mcp:use', 'state' => 's', 'code_challenge' => $challenge, 'code_challenge_method' => 'S256',
        ]);

        $this->actingAs($this->user)->get('/oauth/authorize?'.$query)->assertOk();
        $approve = $this->post('/oauth/authorize', ['state' => 's', 'client_id' => $clientId, 'auth_token' => session('authToken')]);
        parse_str((string) parse_url($approve->headers->get('Location'), PHP_URL_QUERY), $back);

        $this->postJson('/oauth/token', [
            'grant_type' => 'authorization_code', 'client_id' => $clientId, 'redirect_uri' => $redirect,
            'code' => $back['code'], 'code_verifier' => 'verifier-palsu',
        ])->assertStatus(400);
    }

    public function test_tool_menyimpan_dan_menimpa_ringkasan_milik_pemilik_token(): void
    {
        Passport::actingAs($this->user, ['mcp:use']);

        $this->callTool('save-daily-brief', ['date' => '2026-10-21', 'email_digest' => 'pertama'])
            ->assertOk()->assertSee('Ringkasan dibuat');
        $this->callTool('save-daily-brief', ['date' => '2026-10-21', 'email_digest' => 'kedua'])
            ->assertOk()->assertSee('Ringkasan ditimpa');

        $this->assertSame(1, $this->user->dailyBriefs()->count());
        $this->assertSame('kedua', $this->user->dailyBriefs()->value('email_digest'));
        $this->callTool('get-daily-brief', ['date' => '2026-10-21'])->assertOk()->assertSee('kedua');
    }

    public function test_tool_tidak_menyentuh_data_pengguna_lain_atau_meeting_note(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->dailyBriefs()->create(['date' => '2026-10-21', 'email_digest' => 'milik B']);
        $this->user->dailyBriefs()->create(['date' => '2026-10-21', 'meeting_note' => 'catatan meeting A']);

        Passport::actingAs($this->user, ['mcp:use']);
        $this->callTool('save-daily-brief', ['date' => '2026-10-21', 'email_digest' => 'milik A'])->assertOk();

        $this->assertSame('milik B', $other->dailyBriefs()->value('email_digest'));
        $this->assertSame('catatan meeting A', $this->user->dailyBriefs()->value('meeting_note'));
        $this->callTool('get-daily-brief', ['date' => '2026-10-21'])->assertOk()->assertDontSee('milik B');
    }

    public function test_masukan_tool_divalidasi(): void
    {
        Passport::actingAs($this->user, ['mcp:use']);

        $this->callTool('save-daily-brief', ['date' => '21-10-2026', 'email_digest' => 'x'])->assertOk()->assertSee('date');
        $this->callTool('save-daily-brief', ['date' => '2026-10-21', 'email_digest' => str_repeat('x', 20001)])->assertOk()->assertSee('20000');
        $this->assertSame(0, $this->user->dailyBriefs()->count());
    }

    public function test_token_oauth_tanpa_cakupan_mcp_ditolak(): void
    {
        Passport::actingAs($this->user, ['lain']);

        $this->rpc('tools/list')->assertStatus(403);
    }

    public function test_token_rest_tidak_berlaku_di_mcp_dan_token_oauth_tidak_berlaku_di_rest(): void
    {
        [, $plain] = UserToken::issue($this->user, 'hp');
        $this->rpc('tools/list', [], ['Authorization' => 'Bearer '.$plain] + self::ACCEPT)->assertStatus(401);

        Passport::actingAs($this->user, ['mcp:use']);
        $this->getJson('/api/v1/daily-briefs')->assertStatus(401);
    }

    public function test_cabut_semua_sesi_mencabut_token_oauth(): void
    {
        $clientId = $this->postJson('/oauth/register', ['redirect_uris' => ['https://claude.ai/cb']])->json('client_id');
        DB::table('oauth_access_tokens')->insert([
            'id' => 'token-uji', 'user_id' => $this->user->id, 'client_id' => $clientId, 'scopes' => '["mcp:use"]',
            'revoked' => false, 'created_at' => now(), 'updated_at' => now(), 'expires_at' => now()->addHour(),
        ]);
        DB::table('oauth_refresh_tokens')->insert([
            'id' => 'refresh-uji', 'access_token_id' => 'token-uji', 'revoked' => false, 'expires_at' => now()->addDays(30),
        ]);

        $this->user->revokeTokens();

        $this->assertTrue((bool) DB::table('oauth_access_tokens')->where('id', 'token-uji')->value('revoked'));
        $this->assertTrue((bool) DB::table('oauth_refresh_tokens')->where('id', 'refresh-uji')->value('revoked'));
    }
}
