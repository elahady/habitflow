<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class IssueScopedTokenCommandTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
    }

    /** Token ada di baris terakhir; di terminal sungguhan pesan lain ke stderr, tapi buffer tes mencampurnya. */
    private function lastLine(string $output): string
    {
        $lines = array_values(array_filter(array_map('trim', explode("\n", $output))));

        return end($lines);
    }

    public function test_menerbitkan_token_berscope_yang_langsung_bisa_dipakai(): void
    {
        $code = Artisan::call('habitflow:issue-token', ['email' => 'a@example.com']);
        $plain = $this->lastLine(Artisan::output());

        $this->assertSame(0, $code);
        $this->assertSame(48, strlen($plain));

        $token = UserToken::first();
        $this->assertSame('daily-briefs', $token->scope);
        $this->assertSame('claude-morning-routine', $token->name);
        $this->assertSame($this->user->id, $token->user_id);
        $this->assertSame(UserToken::hash($plain), $token->token_hash);

        $this->getJson('/api/v1/ping', ['Authorization' => 'Bearer '.$plain])->assertOk();
    }

    public function test_email_tidak_dikenal_gagal_dan_tidak_membuat_token(): void
    {
        $code = Artisan::call('habitflow:issue-token', ['email' => 'tidak-ada@example.com']);

        $this->assertSame(1, $code);
        $this->assertSame(0, UserToken::count());
    }

    public function test_scope_tidak_dikenal_gagal(): void
    {
        $code = Artisan::call('habitflow:issue-token', ['email' => 'a@example.com', '--scope' => 'full']);

        $this->assertSame(1, $code);
        $this->assertSame(0, UserToken::count());
    }

    public function test_ulang_tanpa_replace_ditolak_dan_token_lama_utuh(): void
    {
        Artisan::call('habitflow:issue-token', ['email' => 'a@example.com']);
        $first = UserToken::first();

        $code = Artisan::call('habitflow:issue-token', ['email' => 'a@example.com']);

        $this->assertSame(1, $code);
        $this->assertSame(1, UserToken::count());
        $this->assertSame($first->id, UserToken::first()->id);
    }

    public function test_replace_mencabut_token_lama_dan_membuat_yang_baru(): void
    {
        Artisan::call('habitflow:issue-token', ['email' => 'a@example.com']);
        $old = $this->lastLine(Artisan::output());

        Artisan::call('habitflow:issue-token', ['email' => 'a@example.com', '--replace' => true]);
        $new = $this->lastLine(Artisan::output());

        $this->assertSame(1, UserToken::count());
        $this->assertNotSame($old, $new);
        $this->getJson('/api/v1/ping', ['Authorization' => 'Bearer '.$old])->assertStatus(401);
        $this->getJson('/api/v1/ping', ['Authorization' => 'Bearer '.$new])->assertOk();
    }

    public function test_replace_tidak_menyentuh_token_login_biasa(): void
    {
        UserToken::issue($this->user, 'claude-morning-routine', null);

        Artisan::call('habitflow:issue-token', ['email' => 'a@example.com', '--replace' => true]);

        $this->assertSame(2, UserToken::count());
        $this->assertSame(1, UserToken::whereNull('scope')->count());
    }
}
