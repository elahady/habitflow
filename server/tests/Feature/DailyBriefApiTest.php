<?php

namespace Tests\Feature;

use App\Models\DailyBrief;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class DailyBriefApiTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private string $plain;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        [, $this->plain] = UserToken::issue($this->user, 'hp-uji');
    }

    private function auth(): array
    {
        return ['Authorization' => 'Bearer '.$this->plain];
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/daily-briefs')->assertStatus(401);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15'])->assertStatus(401);
    }

    public function test_kirim_pertama_membuat_baris_baru(): void
    {
        $this->postJson('/api/v1/daily-briefs', [
            'date' => '2026-10-15', 'email_digest' => '3 email belum dibaca',
        ], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('daily_brief.date', '2026-10-15')
            ->assertJsonPath('daily_brief.email_digest', '3 email belum dibaca')
            ->assertJsonPath('daily_brief.meeting_note', null);

        $this->assertSame(1, DailyBrief::count());
    }

    public function test_kirim_ulang_di_tanggal_sama_menimpa_dan_tidak_menggandakan(): void
    {
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => 'lama'], $this->auth())->assertStatus(201);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => 'baru'], $this->auth())
            ->assertOk()
            ->assertJsonPath('daily_brief.email_digest', 'baru');

        $this->assertSame(1, DailyBrief::count());
    }

    public function test_kolom_yang_tidak_dikirim_tidak_disentuh_dan_null_mengosongkan(): void
    {
        $this->postJson('/api/v1/daily-briefs', [
            'date' => '2026-10-15', 'email_digest' => 'email', 'meeting_note' => 'rapat',
        ], $this->auth())->assertStatus(201);

        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => 'email baru'], $this->auth())
            ->assertOk()
            ->assertJsonPath('daily_brief.meeting_note', 'rapat');

        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'meeting_note' => null], $this->auth())
            ->assertOk()
            ->assertJsonPath('daily_brief.email_digest', 'email baru')
            ->assertJsonPath('daily_brief.meeting_note', null);
    }

    public function test_validasi(): void
    {
        $this->postJson('/api/v1/daily-briefs', [], $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/daily-briefs', ['date' => '15-10-2026'], $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => str_repeat('a', 20001)], $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'meeting_note' => ['bukan teks']], $this->auth())->assertStatus(422);
        $this->getJson('/api/v1/daily-briefs?date=kemarin', $this->auth())->assertStatus(422);

        $this->assertSame(0, DailyBrief::count());
    }

    public function test_daftar_milik_sendiri_terbaru_dulu_dengan_filter(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => 'milik orang lain']);
        $this->user->dailyBriefs()->create(['date' => '2026-10-13', 'email_digest' => 'a']);
        $this->user->dailyBriefs()->create(['date' => '2026-10-15', 'email_digest' => 'b']);

        $this->getJson('/api/v1/daily-briefs', $this->auth())
            ->assertJsonCount(2, 'daily_briefs')
            ->assertJsonPath('daily_briefs.0.date', '2026-10-15')
            ->assertJsonPath('daily_briefs.0.email_digest', 'b');

        $this->getJson('/api/v1/daily-briefs?date=2026-10-13', $this->auth())
            ->assertJsonCount(1, 'daily_briefs')
            ->assertJsonPath('daily_briefs.0.email_digest', 'a');

        $this->getJson('/api/v1/daily-briefs?from=2026-10-14', $this->auth())
            ->assertJsonCount(1, 'daily_briefs')
            ->assertJsonPath('daily_briefs.0.date', '2026-10-15');
    }

    public function test_tanggal_sama_boleh_dimiliki_dua_user(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        [, $otherPlain] = UserToken::issue($other, 'hp-lain');

        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => 'a'], $this->auth())->assertStatus(201);
        $this->postJson('/api/v1/daily-briefs', ['date' => '2026-10-15', 'email_digest' => 'b'], ['Authorization' => 'Bearer '.$otherPlain])->assertStatus(201);

        $this->assertSame(2, DailyBrief::count());
        $this->assertSame('a', $this->user->dailyBriefs()->first()->email_digest);
    }
}
