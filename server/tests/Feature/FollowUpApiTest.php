<?php

namespace Tests\Feature;

use App\Models\FollowUp;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class FollowUpApiTest extends TestCase
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

    private function auth(?string $plain = null): array
    {
        return ['Authorization' => 'Bearer '.($plain ?? $this->plain)];
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/follow-ups')->assertStatus(401);
        $this->postJson('/api/v1/follow-ups', ['title' => 'x'])->assertStatus(401);
    }

    public function test_buat_dan_daftar_follow_up(): void
    {
        $this->postJson('/api/v1/follow-ups', [
            'title' => 'Tanya status deploy',
            'status' => 'ACTIVE',
            'date' => '2026-10-12',
            'time' => '09:30',
            'person' => 'Budi',
        ], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('follow_up.title', 'Tanya status deploy')
            ->assertJsonPath('follow_up.date', '2026-10-12')
            ->assertJsonPath('follow_up.status', 'ACTIVE');

        $this->getJson('/api/v1/follow-ups', $this->auth())
            ->assertOk()
            ->assertJsonCount(1, 'follow_ups');
    }

    public function test_judul_wajib_dan_status_harus_sah(): void
    {
        $this->postJson('/api/v1/follow-ups', [], $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/follow-ups', ['title' => 'x', 'status' => 'LAIN'], $this->auth())->assertStatus(422);
    }

    public function test_status_default_inbox(): void
    {
        $this->postJson('/api/v1/follow-ups', ['title' => 'Catatan cepat'], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('follow_up.status', 'INBOX');
    }

    public function test_filter_status(): void
    {
        $this->user->followUps()->create(['title' => 'a', 'status' => 'INBOX']);
        $this->user->followUps()->create(['title' => 'b', 'status' => 'DONE']);

        $this->getJson('/api/v1/follow-ups?status=DONE', $this->auth())
            ->assertOk()
            ->assertJsonCount(1, 'follow_ups')
            ->assertJsonPath('follow_ups.0.title', 'b');
    }

    public function test_update_dengan_updated_at_cocok_berhasil(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama'])->fresh();

        $this->putJson('/api/v1/follow-ups/'.$item->id, [
            'title' => 'baru',
            'status' => 'DONE',
            'updated_at' => $item->updated_at->toIso8601String(),
        ], $this->auth())
            ->assertOk()
            ->assertJsonPath('follow_up.title', 'baru')
            ->assertJsonPath('follow_up.status', 'DONE');
    }

    public function test_update_bentrok_dibalas_409_dengan_versi_server(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama'])->fresh();
        $dilihatClient = $item->updated_at->toIso8601String();

        $this->travel(5)->minutes();
        $item->update(['title' => 'diubah di web']);

        $this->putJson('/api/v1/follow-ups/'.$item->id, [
            'title' => 'diubah di HP',
            'updated_at' => $dilihatClient,
        ], $this->auth())
            ->assertStatus(409)
            ->assertJsonPath('follow_up.title', 'diubah di web');

        $this->assertSame('diubah di web', $item->fresh()->title);
    }

    public function test_update_tanpa_updated_at_ditolak(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama']);

        $this->putJson('/api/v1/follow-ups/'.$item->id, ['title' => 'baru'], $this->auth())->assertStatus(422);
    }

    public function test_hapus_follow_up(): void
    {
        $item = $this->user->followUps()->create(['title' => 'hapus aku']);

        $this->deleteJson('/api/v1/follow-ups/'.$item->id, [], $this->auth())->assertStatus(204);
        $this->assertSame(0, FollowUp::count());
    }

    public function test_tidak_bisa_akses_milik_user_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $item = $other->followUps()->create(['title' => 'rahasia'])->fresh();

        $this->getJson('/api/v1/follow-ups', $this->auth())->assertJsonCount(0, 'follow_ups');
        $this->putJson('/api/v1/follow-ups/'.$item->id, [
            'title' => 'x',
            'updated_at' => $item->updated_at->toIso8601String(),
        ], $this->auth())->assertStatus(404);
        $this->deleteJson('/api/v1/follow-ups/'.$item->id, [], $this->auth())->assertStatus(404);
    }
}
