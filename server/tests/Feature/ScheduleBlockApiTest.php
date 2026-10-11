<?php

namespace Tests\Feature;

use App\Models\ScheduleBlock;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ScheduleBlockApiTest extends TestCase
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

    private function fixedBlock(array $extra = []): array
    {
        return $extra + ['name' => 'Kerja fokus', 'start_type' => 0, 'start_value' => 9 * 60, 'duration_minutes' => 120];
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/schedule-blocks')->assertStatus(401);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock())->assertStatus(401);
    }

    public function test_buat_blok_jam_tetap_dengan_nilai_awal(): void
    {
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(), $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('schedule_block.name', 'Kerja fokus')
            ->assertJsonPath('schedule_block.start_value', 540)
            ->assertJsonPath('schedule_block.active_days', 127)
            ->assertJsonPath('schedule_block.level', 'INFO')
            ->assertJsonPath('schedule_block.work_reminders', false)
            ->assertJsonPath('schedule_block.habit_ids', []);
    }

    public function test_buat_blok_berpatokan_sholat_dengan_selisih_negatif(): void
    {
        $this->postJson('/api/v1/schedule-blocks', [
            'name' => 'Bangun', 'start_type' => 1, 'start_value' => -15, 'prayer' => 'SUBUH',
            'duration_minutes' => 15, 'level' => 'ALARM',
        ], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('schedule_block.prayer', 'SUBUH')
            ->assertJsonPath('schedule_block.start_value', -15)
            ->assertJsonPath('schedule_block.level', 'ALARM');
    }

    public function test_blok_sholat_tanpa_prayer_ditolak(): void
    {
        $this->postJson('/api/v1/schedule-blocks', ['name' => 'x', 'start_type' => 1, 'start_value' => 0], $this->auth())
            ->assertStatus(422);
    }

    public function test_jam_tetap_harus_dalam_satu_hari_dan_tanpa_prayer(): void
    {
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['start_value' => 1440]), $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['start_value' => -1]), $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['prayer' => 'SUBUH']), $this->auth())->assertStatus(422);
    }

    public function test_field_tidak_sah_ditolak(): void
    {
        $this->postJson('/api/v1/schedule-blocks', [], $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['level' => 'LAIN']), $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['active_days' => 0]), $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['active_days' => 128]), $this->auth())->assertStatus(422);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['work_action' => 'LAIN']), $this->auth())->assertStatus(422);
    }

    public function test_daftar_urut_sort_order_lalu_id(): void
    {
        $this->user->scheduleBlocks()->create(['name' => 'b', 'start_value' => 600, 'sort_order' => 2]);
        $this->user->scheduleBlocks()->create(['name' => 'a', 'start_value' => 500, 'sort_order' => 1]);

        $this->getJson('/api/v1/schedule-blocks', $this->auth())
            ->assertOk()
            ->assertJsonPath('schedule_blocks.0.name', 'a')
            ->assertJsonPath('schedule_blocks.1.name', 'b');
    }

    public function test_tautan_habit_disimpan_dan_diganti_seluruhnya(): void
    {
        $h1 = $this->user->habits()->create(['name' => 'Baca Al-Quran']);
        $h2 = $this->user->habits()->create(['name' => 'Olahraga']);

        $id = $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['habit_ids' => [$h1->id, $h2->id]]), $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('schedule_block.habit_ids', [$h1->id, $h2->id])
            ->json('schedule_block.id');

        $block = ScheduleBlock::findOrFail($id);
        $this->putJson('/api/v1/schedule-blocks/'.$id, [
            'habit_ids' => [$h2->id],
            'updated_at' => $block->updated_at->toIso8601String(),
        ], $this->auth())
            ->assertOk()
            ->assertJsonPath('schedule_block.habit_ids', [$h2->id]);
    }

    public function test_habit_milik_user_lain_ditolak(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $habit = $other->habits()->create(['name' => 'Punya B']);

        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['habit_ids' => [$habit->id]]), $this->auth())
            ->assertStatus(422);
        $this->assertSame(0, ScheduleBlock::count());
    }

    public function test_hapus_habit_melepas_tautan_tapi_blok_tetap(): void
    {
        $habit = $this->user->habits()->create(['name' => 'Baca']);
        $this->postJson('/api/v1/schedule-blocks', $this->fixedBlock(['habit_ids' => [$habit->id]]), $this->auth())->assertStatus(201);

        $habit->delete();

        $this->getJson('/api/v1/schedule-blocks', $this->auth())
            ->assertJsonCount(1, 'schedule_blocks')
            ->assertJsonPath('schedule_blocks.0.habit_ids', []);
    }

    public function test_update_dengan_updated_at_cocok_berhasil(): void
    {
        $block = $this->user->scheduleBlocks()->create(['name' => 'lama', 'start_value' => 540])->fresh();

        $this->putJson('/api/v1/schedule-blocks/'.$block->id, [
            'name' => 'baru',
            'level' => 'REMINDER',
            'updated_at' => $block->updated_at->toIso8601String(),
        ], $this->auth())
            ->assertOk()
            ->assertJsonPath('schedule_block.name', 'baru')
            ->assertJsonPath('schedule_block.level', 'REMINDER')
            ->assertJsonPath('schedule_block.start_value', 540);
    }

    public function test_update_memeriksa_aturan_mulai_terhadap_nilai_tersimpan(): void
    {
        $block = $this->user->scheduleBlocks()->create([
            'name' => 'Subuh', 'start_type' => 1, 'start_value' => 0, 'prayer' => 'SUBUH',
        ])->fresh();

        // Mengosongkan prayer pada blok berpatokan sholat harus ditolak.
        $this->putJson('/api/v1/schedule-blocks/'.$block->id, [
            'prayer' => null,
            'updated_at' => $block->updated_at->toIso8601String(),
        ], $this->auth())->assertStatus(422);
    }

    public function test_update_bentrok_dibalas_409_dengan_versi_server(): void
    {
        $block = $this->user->scheduleBlocks()->create(['name' => 'lama', 'start_value' => 540])->fresh();
        $dilihatClient = $block->updated_at->toIso8601String();

        $this->travel(5)->minutes();
        $block->update(['name' => 'diubah di web']);

        $this->putJson('/api/v1/schedule-blocks/'.$block->id, [
            'name' => 'diubah di HP',
            'updated_at' => $dilihatClient,
        ], $this->auth())
            ->assertStatus(409)
            ->assertJsonPath('schedule_block.name', 'diubah di web');

        $this->assertSame('diubah di web', $block->fresh()->name);
    }

    public function test_update_tanpa_updated_at_ditolak(): void
    {
        $block = $this->user->scheduleBlocks()->create(['name' => 'lama', 'start_value' => 540]);

        $this->putJson('/api/v1/schedule-blocks/'.$block->id, ['name' => 'baru'], $this->auth())->assertStatus(422);
    }

    public function test_hapus_blok(): void
    {
        $block = $this->user->scheduleBlocks()->create(['name' => 'hapus aku', 'start_value' => 540]);

        $this->deleteJson('/api/v1/schedule-blocks/'.$block->id, [], $this->auth())->assertStatus(204);
        $this->assertSame(0, ScheduleBlock::count());
    }

    public function test_tidak_bisa_akses_milik_user_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $block = $other->scheduleBlocks()->create(['name' => 'rahasia', 'start_value' => 540])->fresh();

        $this->getJson('/api/v1/schedule-blocks', $this->auth())->assertJsonCount(0, 'schedule_blocks');
        $this->putJson('/api/v1/schedule-blocks/'.$block->id, [
            'name' => 'x',
            'updated_at' => $block->updated_at->toIso8601String(),
        ], $this->auth())->assertStatus(404);
        $this->deleteJson('/api/v1/schedule-blocks/'.$block->id, [], $this->auth())->assertStatus(404);
    }
}
