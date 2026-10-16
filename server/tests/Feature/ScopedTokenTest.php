<?php

namespace Tests\Feature;

use App\Models\DailyBrief;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/** Tahap 32 langkah 5: token berscope `daily-briefs` hanya boleh ping dan menulis ringkasan harian. */
class ScopedTokenTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
    }

    private function bearer(?string $scope): array
    {
        [, $plain] = UserToken::issue($this->user, 'uji', $scope);

        return ['Authorization' => 'Bearer '.$plain];
    }

    public function test_token_berscope_boleh_ping_dan_menulis_ringkasan(): void
    {
        $auth = $this->bearer(UserToken::SCOPE_DAILY_BRIEFS);

        $this->getJson('/api/v1/ping', $auth)->assertOk();
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-16', 'email_digest' => '2 email belum dibaca'], $auth)
            ->assertStatus(201);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-16', 'email_digest' => 'diperbarui'], $auth)
            ->assertOk();

        $this->assertSame('diperbarui', DailyBrief::first()->email_digest);
    }

    public function test_token_berscope_tidak_boleh_membaca_atau_menyentuh_data_lain(): void
    {
        $auth = $this->bearer(UserToken::SCOPE_DAILY_BRIEFS);

        $this->getJson('/api/v1/daily-briefs', $auth)->assertStatus(403);
        $this->getJson('/api/v1/follow-ups', $auth)->assertStatus(403);
        $this->getJson('/api/v1/work-days', $auth)->assertStatus(403);
        $this->putJson('/api/v1/work-days/2026-10-16', ['eod_note' => 'x'], $auth)->assertStatus(403);
        $this->getJson('/api/v1/snapshot/latest', $auth)->assertStatus(403);
        $this->putJson('/api/v1/snapshot', ['data' => 'x'], $auth)->assertStatus(403);
        $this->postJson('/api/v1/auth/logout-all', [], $auth)->assertStatus(403);

        $this->assertSame(0, DailyBrief::count());
        $this->assertSame(1, UserToken::count(), 'logout-all tidak boleh mencabut token');
    }

    public function test_token_biasa_tanpa_scope_tetap_akses_penuh(): void
    {
        $auth = $this->bearer(null);

        $this->getJson('/api/v1/daily-briefs', $auth)->assertOk();
        $this->getJson('/api/v1/follow-ups', $auth)->assertOk();
        $this->getJson('/api/v1/work-days', $auth)->assertOk();
    }

    public function test_scope_yang_tidak_dikenal_menolak_semuanya(): void
    {
        $auth = $this->bearer('scope-ngawur');

        $this->getJson('/api/v1/ping', $auth)->assertStatus(403);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-16'], $auth)->assertStatus(403);
    }

    public function test_tanpa_token_tetap_401_bukan_403(): void
    {
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-16'])->assertStatus(401);
    }
}
