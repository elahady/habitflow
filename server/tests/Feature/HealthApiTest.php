<?php

namespace Tests\Feature;

use App\Models\BloodPressureEntry;
use App\Models\HealthFocus;
use App\Models\StepDay;
use App\Models\User;
use App\Models\UserToken;
use App\Models\WeightEntry;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/** Tahap 30 langkah 3: PUT /api/v1/health, satu arah dari Android, idempotent. */
class HealthApiTest extends TestCase
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

    private function payload(array $override = []): array
    {
        return $override + [
            'timezone' => 'Asia/Jakarta',
            'profile' => ['height_cm' => 170, 'target_kg' => 65],
            'weights' => [
                ['time_millis' => 1792000000000, 'kg' => 70.4],
                ['time_millis' => 1792086400000, 'kg' => 70.1],
            ],
            'blood_pressures' => [
                ['time_millis' => 1792000000000, 'systolic' => 128, 'diastolic' => 82, 'pulse' => 72, 'note' => 'pagi'],
            ],
            'step_days' => [
                ['date' => '2026-10-15', 'steps' => 8123],
                ['date' => '2026-10-16', 'steps' => 2400],
            ],
            'focus' => [
                'rule' => 'LOW_STEPS',
                'walk_minutes' => 30,
                'title' => 'Jalan santai 30 menit sore ini',
                'sentence' => 'Langkah baru 2.400 dari 8.000.',
                'note' => 'Dipilih karena langkah di bawah separuh target.',
                'reasons' => [['label' => 'Berat', 'value' => '5,4 kg di atas target'], ['label' => 'Tensi', 'value' => '128/82 · Normal']],
                'computed_at' => '2026-10-16T08:00:00Z',
            ],
        ];
    }

    public function test_tanpa_token_ditolak(): void
    {
        $this->putJson('/api/v1/health', $this->payload())->assertStatus(401);
    }

    public function test_menyimpan_semuanya(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())
            ->assertOk()
            ->assertJson(['weights' => 2, 'blood_pressures' => 1, 'step_days' => 2, 'focus' => true]);

        $this->assertSame(2, WeightEntry::count());
        $this->assertEqualsWithDelta(70.4, WeightEntry::orderBy('time_millis')->first()->kg, 0.001);
        $bp = BloodPressureEntry::first();
        $this->assertSame([128, 82, 72, 'pagi'], [$bp->systolic, $bp->diastolic, $bp->pulse, $bp->note]);
        $this->assertSame(2400, StepDay::where('date', '2026-10-16')->first()->steps);

        $focus = HealthFocus::first();
        $this->assertSame('LOW_STEPS', $focus->rule);
        $this->assertSame(30, $focus->walk_minutes);
        $this->assertSame('Asia/Jakarta', $focus->timezone);
        $this->assertSame(170.0, $focus->height_cm);
        $this->assertSame('Berat', $focus->reasons[0]['label']);
    }

    public function test_mengirim_ulang_tidak_menggandakan_dan_langkah_hari_ini_ditimpa(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();
        $again = $this->payload(['step_days' => [['date' => '2026-10-16', 'steps' => 5100]]]);
        $this->putJson('/api/v1/health', $again, $this->auth())->assertOk();
        $this->putJson('/api/v1/health', $again, $this->auth())->assertOk();

        $this->assertSame(2, WeightEntry::count());
        $this->assertSame(1, BloodPressureEntry::count());
        $this->assertSame(2, StepDay::count());
        $this->assertSame(5100, StepDay::where('date', '2026-10-16')->first()->steps);
        $this->assertSame(1, HealthFocus::count());
    }

    public function test_berat_yang_sama_waktunya_diperbarui_bukan_digandakan(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();
        $this->putJson('/api/v1/health', $this->payload(['weights' => [['time_millis' => 1792000000000, 'kg' => 69.9]]]), $this->auth())->assertOk();

        $this->assertSame(2, WeightEntry::count());
        $this->assertEqualsWithDelta(69.9, WeightEntry::where('time_millis', 1792000000000)->first()->kg, 0.001);
    }

    public function test_kiriman_lebih_pendek_tidak_menghapus_catatan_yang_sudah_ada(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Jakarta', 'weights' => [['time_millis' => 1792172800000, 'kg' => 69.5]]], $this->auth())->assertOk();

        $this->assertSame(3, WeightEntry::count());
        $this->assertSame(1, BloodPressureEntry::count());
    }

    public function test_tanpa_fokus_baris_fokus_lama_tidak_tersentuh_tapi_zona_waktu_diperbarui(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Makassar'], $this->auth())->assertOk();

        $focus = HealthFocus::first();
        $this->assertSame('Asia/Makassar', $focus->timezone);
        $this->assertSame('LOW_STEPS', $focus->rule);
        $this->assertSame(170.0, $focus->height_cm);
    }

    public function test_hanya_zona_waktu_membuat_baris_tanpa_fokus(): void
    {
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Jakarta'], $this->auth())->assertOk()->assertJson(['focus' => false]);

        $this->assertNull(HealthFocus::first()->rule);
        $this->assertSame(0, WeightEntry::count());
    }

    public function test_profil_null_mengosongkan_tinggi_dan_target(): void
    {
        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Jakarta', 'profile' => ['height_cm' => null, 'target_kg' => null]], $this->auth())->assertOk();

        $this->assertNull(HealthFocus::first()->height_cm);
        $this->assertNull(HealthFocus::first()->target_kg);
    }

    public function test_data_pengguna_lain_tidak_tersentuh(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        WeightEntry::create(['user_id' => $other->id, 'time_millis' => 1792000000000, 'kg' => 80.0]);

        $this->putJson('/api/v1/health', $this->payload(), $this->auth())->assertOk();

        $this->assertEqualsWithDelta(80.0, WeightEntry::where('user_id', $other->id)->first()->kg, 0.001);
        $this->assertSame(3, WeightEntry::count());
    }

    public function test_validasi_menolak_kiriman_yang_tidak_masuk_akal_dan_tidak_menyimpan_apa_pun(): void
    {
        $bad = [
            'tanpa zona waktu' => ['weights' => [['time_millis' => 1792000000000, 'kg' => 70]]],
            'zona waktu ngawur' => ['timezone' => 'Mars/Olympus', 'weights' => []],
            'berat 5 kg' => $this->payload(['weights' => [['time_millis' => 1792000000000, 'kg' => 5]]]),
            'berat 500 kg' => $this->payload(['weights' => [['time_millis' => 1792000000000, 'kg' => 500]]]),
            'waktu catat nol' => $this->payload(['weights' => [['time_millis' => 0, 'kg' => 70]]]),
            'waktu sama dua kali' => $this->payload(['weights' => [['time_millis' => 1792000000000, 'kg' => 70], ['time_millis' => 1792000000000, 'kg' => 71]]]),
            'sistolik 20' => $this->payload(['blood_pressures' => [['time_millis' => 1792000000000, 'systolic' => 20, 'diastolic' => 80]]]),
            'diastolik 400' => $this->payload(['blood_pressures' => [['time_millis' => 1792000000000, 'systolic' => 120, 'diastolic' => 400]]]),
            'nadi 5' => $this->payload(['blood_pressures' => [['time_millis' => 1792000000000, 'systolic' => 120, 'diastolic' => 80, 'pulse' => 5]]]),
            'tanggal langkah salah' => $this->payload(['step_days' => [['date' => '16-10-2026', 'steps' => 100]]]),
            'langkah negatif' => $this->payload(['step_days' => [['date' => '2026-10-16', 'steps' => -1]]]),
            'tanggal langkah ganda' => $this->payload(['step_days' => [['date' => '2026-10-16', 'steps' => 1], ['date' => '2026-10-16', 'steps' => 2]]]),
            'terlalu banyak hari langkah' => $this->payload(['step_days' => array_map(fn ($i) => ['date' => date('Y-m-d', 1792000000 + $i * 86400), 'steps' => 1], range(0, 14))]),
            'kode aturan huruf kecil' => $this->payload(['focus' => ['rule' => 'low steps', 'title' => 'x', 'sentence' => 'y', 'computed_at' => '2026-10-16T08:00:00Z']]),
            'fokus tanpa judul' => $this->payload(['focus' => ['rule' => 'ON_TRACK', 'sentence' => 'y', 'computed_at' => '2026-10-16T08:00:00Z']]),
            'tinggi 50 cm' => $this->payload(['profile' => ['height_cm' => 50]]),
        ];

        foreach ($bad as $name => $body) {
            $this->putJson('/api/v1/health', $body, $this->auth())->assertStatus(422, "Seharusnya ditolak: $name");
        }

        $this->assertSame(0, WeightEntry::count());
        $this->assertSame(0, BloodPressureEntry::count());
        $this->assertSame(0, StepDay::count());
        $this->assertSame(0, HealthFocus::count());
    }

    public function test_batas_jumlah_catatan_per_permintaan(): void
    {
        $many = array_map(fn ($i) => ['time_millis' => 1792000000000 + $i, 'kg' => 70], range(0, 500));
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Jakarta', 'weights' => $many], $this->auth())->assertStatus(422);

        $ok = array_slice($many, 0, 500);
        $this->putJson('/api/v1/health', ['timezone' => 'Asia/Jakarta', 'weights' => $ok], $this->auth())->assertOk();
        $this->assertSame(500, WeightEntry::count());
    }

    /** Bentuk yang dikirim Android untuk pengguna baru (HealthSyncPayload.toJson): daftar kosong, profil null, tanpa kunci fokus. */
    public function test_kiriman_android_pengguna_baru_dengan_daftar_kosong_dan_profil_null_diterima(): void
    {
        $this->putJson('/api/v1/health', [
            'timezone' => 'Asia/Jakarta',
            'profile' => ['height_cm' => null, 'target_kg' => null],
            'weights' => [],
            'blood_pressures' => [],
            'step_days' => [],
        ], $this->auth())->assertOk()->assertJson(['weights' => 0, 'blood_pressures' => 0, 'step_days' => 0, 'focus' => false]);

        $row = HealthFocus::first();
        $this->assertSame('Asia/Jakarta', $row->timezone);
        $this->assertNull($row->height_cm);
        $this->assertNull($row->rule);
    }

    public function test_token_terbatas_daily_briefs_tidak_boleh_menulis_data_kesehatan(): void
    {
        [, $scoped] = UserToken::issue($this->user, 'rekap', UserToken::SCOPE_DAILY_BRIEFS);

        $this->putJson('/api/v1/health', $this->payload(), ['Authorization' => 'Bearer '.$scoped])->assertStatus(403);
        $this->assertSame(0, WeightEntry::count());
    }
}
