<?php

namespace Tests\Feature;

use App\Models\DayOff;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class DayOffApiTest extends TestCase
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

    private function dates(?User $user = null): array
    {
        return ($user ?? $this->user)->daysOff()->orderBy('date')->pluck('date')->all();
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/days-off')->assertStatus(401);
        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => []])->assertStatus(401);
    }

    public function test_kirim_menyimpan_tanggal_libur_dalam_jendela(): void
    {
        $this->putJson('/api/v1/days-off', [
            'from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => ['2026-10-17', '2026-10-18', '2026-10-20'],
        ], $this->auth())
            ->assertOk()
            ->assertJsonPath('days_off', ['2026-10-17', '2026-10-18', '2026-10-20']);

        $this->assertSame(['2026-10-17', '2026-10-18', '2026-10-20'], $this->dates());
    }

    public function test_kirim_ulang_menggantikan_isi_jendela_dan_tidak_menggandakan(): void
    {
        $window = ['from' => '2026-10-16', 'to' => '2026-10-29'];
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-17', '2026-10-18']], $this->auth())->assertOk();
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-18', '2026-10-19']], $this->auth())->assertOk();
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-18', '2026-10-19']], $this->auth())->assertOk();

        // "Hari ini libur" dimatikan di HP -> 17 hilang; 18 tidak digandakan.
        $this->assertSame(['2026-10-18', '2026-10-19'], $this->dates());
    }

    public function test_daftar_kosong_menghapus_libur_dalam_jendela(): void
    {
        $this->user->daysOff()->create(['date' => '2026-10-17']);

        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => []], $this->auth())
            ->assertOk()
            ->assertJsonPath('days_off', []);

        $this->assertSame([], $this->dates());
    }

    public function test_tanggal_di_luar_jendela_tidak_disentuh(): void
    {
        $this->user->daysOff()->create(['date' => '2026-10-01']); // riwayat sebelum jendela
        $this->user->daysOff()->create(['date' => '2026-11-20']); // jauh di depan

        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => ['2026-10-17']], $this->auth())->assertOk();
        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => []], $this->auth())->assertOk();

        $this->assertSame(['2026-10-01', '2026-11-20'], $this->dates());
    }

    public function test_tepi_jendela_ikut_digantikan(): void
    {
        $this->user->daysOff()->create(['date' => '2026-10-16']);
        $this->user->daysOff()->create(['date' => '2026-10-29']);

        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => []], $this->auth())->assertOk();

        $this->assertSame([], $this->dates());
    }

    public function test_tidak_menyentuh_libur_user_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->daysOff()->create(['date' => '2026-10-17']);

        $this->putJson('/api/v1/days-off', ['from' => '2026-10-16', 'to' => '2026-10-29', 'dates' => []], $this->auth())->assertOk();

        $this->assertSame(['2026-10-17'], $this->dates($other));
        $this->getJson('/api/v1/days-off', $this->auth())->assertJsonPath('days_off', []);
    }

    public function test_bentuk_kiriman_yang_salah_ditolak_dan_isi_lama_utuh(): void
    {
        $this->user->daysOff()->create(['date' => '2026-10-17']);
        $window = ['from' => '2026-10-16', 'to' => '2026-10-29'];

        $this->putJson('/api/v1/days-off', [], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/days-off', $window, $this->auth())->assertStatus(422); // dates wajib ada
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-15']], $this->auth())->assertStatus(422); // sebelum jendela
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-30']], $this->auth())->assertStatus(422); // sesudah jendela
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['2026-10-17', '2026-10-17']], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/days-off', $window + ['dates' => ['17-10-2026']], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/days-off', ['from' => '2026-10-29', 'to' => '2026-10-16', 'dates' => []], $this->auth())->assertStatus(422);

        $this->assertSame(['2026-10-17'], $this->dates());
    }

    public function test_jendela_terlalu_panjang_ditolak(): void
    {
        $this->putJson('/api/v1/days-off', ['from' => '2026-01-01', 'to' => '2026-12-31', 'dates' => []], $this->auth())->assertStatus(422);
        // 62 hari (from..to inklusif) masih boleh.
        $this->putJson('/api/v1/days-off', ['from' => '2026-10-01', 'to' => '2026-12-01', 'dates' => []], $this->auth())->assertOk();
        $this->putJson('/api/v1/days-off', ['from' => '2026-10-01', 'to' => '2026-12-02', 'dates' => []], $this->auth())->assertStatus(422);
    }

    public function test_baca_dengan_filter_from(): void
    {
        foreach (['2026-10-10', '2026-10-17', '2026-10-18'] as $date) {
            DayOff::create(['user_id' => $this->user->id, 'date' => $date]);
        }

        $this->getJson('/api/v1/days-off?from=2026-10-16', $this->auth())->assertOk()->assertJsonPath('days_off', ['2026-10-17', '2026-10-18']);
        $this->getJson('/api/v1/days-off', $this->auth())->assertJsonCount(3, 'days_off');
    }
}
