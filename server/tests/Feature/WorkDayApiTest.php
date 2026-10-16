<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\UserToken;
use App\Models\WorkDay;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class WorkDayApiTest extends TestCase
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
        $this->getJson('/api/v1/work-days')->assertStatus(401);
        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'x'])->assertStatus(401);
    }

    public function test_tanggal_baru_dibuat_tanpa_updated_at(): void
    {
        $this->putJson('/api/v1/work-days/2026-10-15', [
            'eod_note' => 'Modul supplier dipindah ke besok.',
            'scrum_done_at' => '2026-10-15T01:00:00Z',
            'eod_done_at' => '2026-10-15T09:03:00Z',
        ], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('work_day.date', '2026-10-15')
            ->assertJsonPath('work_day.eod_note', 'Modul supplier dipindah ke besok.')
            ->assertJsonPath('work_day.eod_done_at', '2026-10-15T09:03:00.000000Z');

        $this->assertSame(1, WorkDay::count());
    }

    public function test_ubah_dengan_updated_at_yang_sama_berhasil_dan_kolom_lain_tidak_disentuh(): void
    {
        $created = $this->putJson('/api/v1/work-days/2026-10-15', [
            'eod_note' => 'lama', 'eod_done_at' => '2026-10-15T09:03:00Z',
        ], $this->auth())->json('work_day');

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'baru', 'updated_at' => $created['updated_at']], $this->auth())
            ->assertOk()
            ->assertJsonPath('work_day.eod_note', 'baru')
            ->assertJsonPath('work_day.eod_done_at', '2026-10-15T09:03:00.000000Z');

        $this->assertSame(1, WorkDay::count());
    }

    public function test_null_mengosongkan_catatan(): void
    {
        $created = $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'isi'], $this->auth())->json('work_day');

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => null, 'updated_at' => $created['updated_at']], $this->auth())
            ->assertOk()
            ->assertJsonPath('work_day.eod_note', null);
    }

    public function test_updated_at_lama_dibalas_409_berisi_versi_terbaru(): void
    {
        $created = $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'v1'], $this->auth())->json('work_day');
        WorkDay::first()->forceFill(['eod_note' => 'v2 dari web', 'updated_at' => now()->addMinute()])->saveQuietly();

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'v1 diubah di HP', 'updated_at' => $created['updated_at']], $this->auth())
            ->assertStatus(409)
            ->assertJsonPath('work_day.eod_note', 'v2 dari web');

        $this->assertSame('v2 dari web', WorkDay::first()->eod_note);
    }

    public function test_baris_ada_tanpa_updated_at_dibalas_409_bukan_menimpa(): void
    {
        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'asli'], $this->auth())->assertStatus(201);

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'menimpa tanpa tahu'], $this->auth())
            ->assertStatus(409)
            ->assertJsonPath('work_day.eod_note', 'asli');

        $this->assertSame('asli', WorkDay::first()->eod_note);
    }

    public function test_validasi(): void
    {
        $this->putJson('/api/v1/work-days/besok', ['eod_note' => 'x'], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => str_repeat('a', 5001)], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_at' => 'bukan waktu'], $this->auth())->assertStatus(422);
        $this->getJson('/api/v1/work-days?from=kemarin', $this->auth())->assertStatus(422);

        $this->assertSame(0, WorkDay::count());
    }

    public function test_daftar_milik_sendiri_terbaru_dulu_dengan_filter(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'milik orang lain']);
        $this->user->workDays()->create(['date' => '2026-10-13', 'eod_note' => 'a']);
        $this->user->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'b']);

        $this->getJson('/api/v1/work-days', $this->auth())
            ->assertJsonCount(2, 'work_days')
            ->assertJsonPath('work_days.0.date', '2026-10-15');

        $this->getJson('/api/v1/work-days?from=2026-10-14', $this->auth())
            ->assertJsonCount(1, 'work_days')
            ->assertJsonPath('work_days.0.eod_note', 'b');
    }

    public function test_tanggal_sama_boleh_dimiliki_dua_user_dan_tidak_bisa_menimpa_milik_orang_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->workDays()->create(['date' => '2026-10-15', 'eod_note' => 'rahasia B']);

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'punya A'], $this->auth())->assertStatus(201);

        $this->assertSame('rahasia B', $other->workDays()->first()->eod_note);
        $this->assertSame('punya A', $this->user->workDays()->first()->eod_note);
    }
    // --- Angka ringkasan EOD "X dari Y selesai" (tahap 33 sisa) ---

    public function test_angka_ringkasan_eod_disimpan_dan_dikembalikan(): void
    {
        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_at' => '2026-10-15T09:03:00Z', 'eod_done_count' => 7, 'eod_total_count' => 9], $this->auth())
            ->assertStatus(201)
            ->assertJsonPath('work_day.eod_done_count', 7)
            ->assertJsonPath('work_day.eod_total_count', 9);

        $this->assertSame(7, WorkDay::first()->eod_done_count);
    }

    public function test_ubah_tanpa_mengirim_angka_tidak_menyentuhnya(): void
    {
        $created = $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_count' => 7, 'eod_total_count' => 9], $this->auth())->json('work_day');

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_note' => 'baru', 'updated_at' => $created['updated_at']], $this->auth())
            ->assertOk()
            ->assertJsonPath('work_day.eod_done_count', 7)
            ->assertJsonPath('work_day.eod_total_count', 9);
    }

    public function test_angka_bisa_diperbarui_atau_dikosongkan_bersama(): void
    {
        $created = $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_count' => 7, 'eod_total_count' => 9], $this->auth())->json('work_day');
        $updated = $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_count' => 9, 'eod_total_count' => 9, 'updated_at' => $created['updated_at']], $this->auth())
            ->assertOk()->assertJsonPath('work_day.eod_done_count', 9)->json('work_day');

        $this->putJson('/api/v1/work-days/2026-10-15', ['eod_done_count' => null, 'eod_total_count' => null, 'updated_at' => $updated['updated_at']], $this->auth())
            ->assertOk()->assertJsonPath('work_day.eod_done_count', null)->assertJsonPath('work_day.eod_total_count', null);
    }

    public function test_angka_ringkasan_yang_tidak_sah_ditolak(): void
    {
        $url = '/api/v1/work-days/2026-10-15';
        $this->putJson($url, ['eod_done_count' => 7], $this->auth())->assertStatus(422); // harus berpasangan
        $this->putJson($url, ['eod_total_count' => 9], $this->auth())->assertStatus(422);
        $this->putJson($url, ['eod_done_count' => 10, 'eod_total_count' => 9], $this->auth())->assertStatus(422); // selesai > total
        $this->putJson($url, ['eod_done_count' => -1, 'eod_total_count' => 9], $this->auth())->assertStatus(422);
        $this->putJson($url, ['eod_done_count' => 'tujuh', 'eod_total_count' => 9], $this->auth())->assertStatus(422);
        $this->putJson($url, ['eod_done_count' => 1, 'eod_total_count' => 1001], $this->auth())->assertStatus(422);

        $this->assertSame(0, WorkDay::count());
    }
}
