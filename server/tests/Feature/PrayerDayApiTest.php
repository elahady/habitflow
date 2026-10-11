<?php

namespace Tests\Feature;

use App\Models\PrayerDay;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class PrayerDayApiTest extends TestCase
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

    private function day(string $date, array $override = []): array
    {
        return $override + [
            'date' => $date, 'subuh' => '04:40', 'dzuhur' => '11:55',
            'ashar' => '15:10', 'maghrib' => '17:58', 'isya' => '19:08',
        ];
    }

    private function payload(array $days, string $tz = 'Asia/Jakarta'): array
    {
        return ['timezone' => $tz, 'days' => $days];
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->getJson('/api/v1/prayer-days')->assertStatus(401);
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12')]))->assertStatus(401);
    }

    public function test_kirim_beberapa_hari_sekaligus(): void
    {
        $this->putJson('/api/v1/prayer-days', $this->payload([
            $this->day('2026-10-12'), $this->day('2026-10-13', ['subuh' => '04:39']),
        ]), $this->auth())
            ->assertOk()
            ->assertJsonCount(2, 'prayer_days')
            ->assertJsonPath('prayer_days.0.date', '2026-10-12')
            ->assertJsonPath('prayer_days.0.timezone', 'Asia/Jakarta');

        $this->assertSame(2, PrayerDay::count());
        $this->assertSame(4 * 60 + 39, PrayerDay::where('date', '2026-10-13')->first()->minuteOf('SUBUH'));
    }

    public function test_kirim_ulang_menimpa_dan_tidak_menggandakan(): void
    {
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12')]), $this->auth())->assertOk();
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12', ['subuh' => '04:41'])], 'Asia/Makassar'), $this->auth())->assertOk();

        $this->assertSame(1, PrayerDay::count());
        $row = PrayerDay::first();
        $this->assertSame(4 * 60 + 41, $row->minuteOf('subuh'));
        $this->assertSame('Asia/Makassar', $row->timezone);
    }

    public function test_waktu_null_diterima_untuk_lintang_kutub(): void
    {
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-06-21', ['subuh' => null, 'isya' => null])]), $this->auth())
            ->assertOk();

        $this->assertNull(PrayerDay::first()->minuteOf('subuh'));
        $this->assertSame(11 * 60 + 55, PrayerDay::first()->minuteOf('dzuhur'));
    }

    public function test_validasi(): void
    {
        $this->putJson('/api/v1/prayer-days', [], $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/prayer-days', $this->payload([]), $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12')], 'Bukan/Zona'), $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('12-10-2026')]), $this->auth())->assertStatus(422);
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12', ['subuh' => '4.40'])]), $this->auth())->assertStatus(422);
        // Tanggal kembar dalam satu kiriman.
        $this->putJson('/api/v1/prayer-days', $this->payload([$this->day('2026-10-12'), $this->day('2026-10-12')]), $this->auth())->assertStatus(422);
        // Kunci waktu sholat wajib ada (null boleh, hilang tidak).
        $kurang = $this->day('2026-10-12');
        unset($kurang['isya']);
        $this->putJson('/api/v1/prayer-days', $this->payload([$kurang]), $this->auth())->assertStatus(422);

        $this->assertSame(0, PrayerDay::count());
    }

    public function test_maksimal_14_hari_per_kiriman(): void
    {
        $days = [];
        for ($i = 1; $i <= 15; $i++) {
            $days[] = $this->day(sprintf('2026-10-%02d', $i));
        }

        $this->putJson('/api/v1/prayer-days', $this->payload($days), $this->auth())->assertStatus(422);
    }

    public function test_daftar_milik_sendiri_dan_filter_from(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $other->prayerDays()->create($this->day('2026-10-12') + ['timezone' => 'Asia/Jakarta']);
        $this->user->prayerDays()->create($this->day('2026-10-10') + ['timezone' => 'Asia/Jakarta']);
        $this->user->prayerDays()->create($this->day('2026-10-12') + ['timezone' => 'Asia/Jakarta']);

        $this->getJson('/api/v1/prayer-days', $this->auth())->assertJsonCount(2, 'prayer_days');
        $this->getJson('/api/v1/prayer-days?from=2026-10-11', $this->auth())
            ->assertJsonCount(1, 'prayer_days')
            ->assertJsonPath('prayer_days.0.date', '2026-10-12');
    }
}
