<?php

namespace Tests\Feature;

use App\Livewire\Dashboard;
use App\Models\Team;
use App\Models\User;
use App\Support\DaySummary;
use App\Support\UserDay;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class DashboardSummaryTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        // Kamis 15 Oktober 2026, 07.30 WIB (00.30 UTC).
        Carbon::setTestNow(Carbon::parse('2026-10-15 07:30', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'Roziq']);
        $this->actingAs($this->user);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function summary(): array
    {
        return DaySummary::for($this->user, '2026-10-15');
    }

    public function test_beranda_menampilkan_tanggal_dan_empat_kartu(): void
    {
        $this->get('/')->assertOk()
            ->assertSee('Kamis, 15 Oktober 2026')
            ->assertSee('Habit hari ini')
            ->assertSee('To-do hari ini')
            ->assertSee('Follow-up kerja')
            ->assertSee('Tugas Rumah');
    }

    public function test_kartu_menaut_ke_halamannya(): void
    {
        $this->get('/')->assertSee('href="'.route('habit').'"', false)
            ->assertSee('href="'.route('follow-ups').'"', false)
            ->assertSee('href="'.route('teams').'"', false);
    }

    public function test_habit_dihitung_dari_centang_hari_ini_saja(): void
    {
        $a = $this->user->habits()->create(['name' => 'Sholat']);
        $this->user->habits()->create(['name' => 'Baca']);
        $c = $this->user->habits()->create(['name' => 'Olahraga']);
        $a->entries()->create(['date' => '2026-10-15']);
        $c->entries()->create(['date' => '2026-10-14']); // kemarin tidak dihitung

        $this->assertSame(['done' => 1, 'total' => 3], $this->summary()['habits']);
    }

    public function test_todo_dihitung_per_hari_ini(): void
    {
        $this->user->todos()->create(['title' => 'A', 'date' => '2026-10-15', 'done' => true]);
        $this->user->todos()->create(['title' => 'B', 'date' => '2026-10-15']);
        $this->user->todos()->create(['title' => 'Kemarin', 'date' => '2026-10-14', 'done' => true]);

        $this->assertSame(['done' => 1, 'total' => 2], $this->summary()['todos']);
    }

    public function test_follow_up_mengikuti_aturan_bagian_di_android(): void
    {
        $f = fn (array $a) => $this->user->followUps()->create($a + ['title' => 'x']);
        $f(['status' => 'INBOX']);                                          // inbox
        $f(['status' => 'INBOX', 'date' => '2026-10-01']);                  // inbox menang atas lewat tanggal
        $f(['status' => 'ACTIVE', 'date' => '2026-10-15']);                 // hari ini
        $f(['status' => 'WAITING', 'date' => '2026-10-15']);                // menunggu dan dicek hari ini
        $f(['status' => 'ACTIVE', 'date' => '2026-10-20', 'picked_date' => '2026-10-15']); // dipilih hari ini
        $f(['status' => 'ACTIVE', 'date' => '2026-10-10']);                 // lewat tanggal
        $f(['status' => 'ACTIVE', 'date' => '2026-10-10', 'picked_date' => '2026-10-15']); // dipilih hari ini, bukan lewat tanggal
        $f(['status' => 'ACTIVE', 'date' => '2026-10-30']);                 // nanti: tidak dihitung
        $f(['status' => 'ACTIVE']);                                         // tanpa tanggal: tidak dihitung
        $f(['status' => 'DONE', 'date' => '2026-10-01']);                   // selesai: tidak dihitung

        $this->assertSame(['overdue' => 1, 'today' => 4, 'inbox' => 2], $this->summary()['followUps']);
    }

    public function test_tugas_rumah_hanya_milikku_yang_belum_selesai_di_tim_yang_kuikuti(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'Sari']);
        $team = Team::create(['name' => 'Rumah', 'created_by' => $this->user->id]);
        $team->members()->attach([$this->user->id, $other->id]);
        $team->todos()->create(['title' => 'Cuci piring', 'assigned_to' => $this->user->id]);
        $team->todos()->create(['title' => 'Sapu', 'assigned_to' => $this->user->id]);
        $team->todos()->create(['title' => 'Selesai', 'assigned_to' => $this->user->id, 'done' => true]);
        $team->todos()->create(['title' => 'Punya Sari', 'assigned_to' => $other->id]);
        $team->todos()->create(['title' => 'Belum ditugaskan']);

        $this->assertSame(2, $this->summary()['teamMine']);
    }

    public function test_tugas_rumah_di_tim_yang_sudah_ditinggalkan_tidak_dihitung(): void
    {
        $team = Team::create(['name' => 'Lama', 'created_by' => $this->user->id]);
        $team->todos()->create(['title' => 'Tugas lama', 'assigned_to' => $this->user->id]);

        $this->assertSame(0, $this->summary()['teamMine']);
    }

    public function test_data_pengguna_lain_tidak_ikut(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'Sari']);
        $other->habits()->create(['name' => 'Milik lain']);
        $other->todos()->create(['title' => 'Milik lain', 'date' => '2026-10-15']);
        $other->followUps()->create(['title' => 'Milik lain', 'status' => 'INBOX']);

        $this->assertSame(['done' => 0, 'total' => 0], $this->summary()['habits']);
        $this->assertSame(['done' => 0, 'total' => 0], $this->summary()['todos']);
        $this->assertSame(['overdue' => 0, 'today' => 0, 'inbox' => 0], $this->summary()['followUps']);
    }

    public function test_kosong_menampilkan_pesan_bukan_nol_per_nol(): void
    {
        Livewire::test(Dashboard::class)
            ->assertSee('Belum ada habit.')
            ->assertSee('Belum ada to-do hari ini.')
            ->assertDontSee('0 / 0');
    }

    public function test_angka_dan_bar_tampil(): void
    {
        $habit = $this->user->habits()->create(['name' => 'Sholat']);
        $this->user->habits()->create(['name' => 'Baca']);
        $habit->entries()->create(['date' => '2026-10-15']);

        Livewire::test(Dashboard::class)->assertSee('1 / 2')->assertSeeHtml('width: 50%');
    }

    public function test_hari_ini_mengikuti_zona_waktu_pengguna_bukan_utc(): void
    {
        // 15 Oktober 22.00 UTC sudah 16 Oktober 05.00 di Jakarta.
        Carbon::setTestNow(Carbon::parse('2026-10-15 22:00', 'UTC'));

        $this->assertSame('2026-10-16', UserDay::today($this->user));

        $habit = $this->user->habits()->create(['name' => 'Sholat']);
        $habit->entries()->create(['date' => '2026-10-16']);
        Livewire::test(Dashboard::class)->assertSee('Jumat, 16 Oktober 2026')->assertSee('1 / 1');
    }

    public function test_zona_waktu_terakhir_dari_hp_dipakai(): void
    {
        $this->user->prayerDays()->create(['date' => '2026-10-15', 'timezone' => 'Asia/Jayapura']);

        $this->assertSame('Asia/Jayapura', UserDay::timezone($this->user));
        $this->assertSame('Asia/Jakarta', UserDay::timezone(User::create(['google_sub' => 'sub-9', 'email' => 'z@example.com', 'name' => 'Z'])));
    }
}
