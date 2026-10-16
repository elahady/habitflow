<?php

namespace Tests\Feature;

use App\Livewire\Teams;
use App\Models\Team;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class TeamsPageTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private User $partner;

    protected function setUp(): void
    {
        parent::setUp();
        Carbon::setTestNow(Carbon::parse('2026-10-16 07:30', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'Roziq']);
        $this->partner = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'Istri']);
        $this->actingAs($this->user);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    private function team(string $name = 'Rumah Roziq'): Team
    {
        $team = Team::create(['name' => $name, 'created_by' => $this->user->id]);
        $team->members()->attach([$this->user->id, $this->partner->id]);

        return $team;
    }

    public function test_halaman_butuh_login(): void
    {
        auth()->logout();

        $this->get('/tugas-rumah')->assertRedirect('/login');
    }

    public function test_tanpa_tim_halaman_menawarkan_buat_tim(): void
    {
        $this->get('/tugas-rumah')->assertOk()
            ->assertSee('Jumat, 16 Oktober 2026')
            ->assertSee('Belum gabung tim manapun')
            ->assertDontSee('Tugas saya');

        Livewire::test(Teams::class)->set('newTeamName', 'Rumah Roziq')->call('createTeam');

        $this->assertSame(['Rumah Roziq'], $this->user->teams()->pluck('name')->all());
    }

    public function test_kartu_ringkas_menghitung_tugas_saya_dan_semua_tugas(): void
    {
        $team = $this->team();
        $team->todos()->create(['title' => 'Cuci piring', 'created_by' => $this->user->id, 'assigned_to' => $this->user->id]);
        $team->todos()->create(['title' => 'Jemur baju', 'created_by' => $this->user->id, 'assigned_to' => $this->user->id, 'done' => true]);
        $team->todos()->create(['title' => 'Belanja', 'created_by' => $this->user->id, 'assigned_to' => $this->partner->id]);

        $this->get('/tugas-rumah')->assertOk()
            ->assertSee('Tugas saya: 1 dari 2', false)
            ->assertSee('Semua tugas tim: 1 dari 3', false)
            ->assertSee('Cuci piring')
            ->assertSee('Roziq (Anda)')
            ->assertSee('Istri');
    }

    public function test_tim_lain_tidak_ikut_terhitung(): void
    {
        $asing = Team::create(['name' => 'Tim Asing', 'created_by' => $this->partner->id]);
        $asing->members()->attach($this->partner->id);
        $asing->todos()->create(['title' => 'Rahasia', 'created_by' => $this->partner->id, 'assigned_to' => $this->user->id]);

        $this->get('/tugas-rumah')->assertOk()
            ->assertDontSee('Rahasia')
            ->assertDontSee('Tim Asing');
    }

    public function test_link_undangan_hanya_tampil_di_kartu_timnya(): void
    {
        $a = $this->team('Tim A');
        $this->team('Tim B');

        $html = Livewire::test(Teams::class)->call('createInvite', $a->id)->html();

        $this->assertSame(1, substr_count($html, 'Kirim link ini'));
    }

    public function test_centang_tugas_menandai_selesai_dan_membatalkannya(): void
    {
        $todo = $this->team()->todos()->create(['title' => 'Cuci piring', 'created_by' => $this->user->id]);

        Livewire::test(Teams::class)->call('toggleDone', $todo->id);
        $this->assertTrue($todo->fresh()->done);
        $this->assertSame($this->user->id, $todo->fresh()->done_by);

        Livewire::test(Teams::class)->call('toggleDone', $todo->id);
        $this->assertFalse($todo->fresh()->done);
    }
}
