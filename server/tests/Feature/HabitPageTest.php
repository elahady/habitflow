<?php

namespace Tests\Feature;

use App\Livewire\HabitToday;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Livewire\Livewire;
use Tests\TestCase;

class HabitPageTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        Carbon::setTestNow(Carbon::parse('2026-10-15 07:30', 'Asia/Jakarta'));
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        $this->actingAs($this->user);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    public function test_halaman_butuh_login(): void
    {
        auth()->logout();

        $this->get('/habit')->assertRedirect('/login');
    }

    public function test_halaman_menampilkan_tanggal_kartu_streak_dan_kontribusi(): void
    {
        $habit = $this->user->habits()->create(['name' => 'Sholat', 'is_mandatory' => true]);
        $habit->entries()->create(['date' => '2026-10-14']);
        $habit->entries()->create(['date' => '2026-10-15']);

        $this->get('/habit')->assertOk()
            ->assertSee('Kamis, 15 Oktober 2026')
            ->assertSee('Habit hari ini')
            ->assertSee('1 / 1')
            ->assertSee('2 hari')
            ->assertSee('Terpanjang 2 hari')
            ->assertSee('Gabungan, 53 minggu terakhir')
            ->assertSee('Heatmap Sholat, 14 minggu terakhir')
            ->assertSee('Habit wajib selalu tampil di atas');
    }

    public function test_heatmap_hanya_menggambar_sel_sampai_hari_ini(): void
    {
        // Tanpa habit tidak ada heatmap per habit, jadi hanya gabungan dan legenda yang tergambar.
        $html = Livewire::test(HabitToday::class)->html();

        $drawn = preg_match_all('/<i class="hf-heat-[0-4]"/', $html);
        $future = preg_match_all('/<i class="hf-heat-none"/', $html);

        // 15 Okt 2026 (Kamis): 52 minggu penuh + Senin sampai Kamis = 368 sel, ditambah 5 sel legenda. Jumat sampai Minggu kosong.
        $this->assertSame(52 * 7 + 4 + 5, $drawn);
        $this->assertSame(3, $future);
    }

    public function test_mini_heatmap_per_habit_biner(): void
    {
        $habit = $this->user->habits()->create(['name' => 'Sholat']);
        $habit->created_at = Carbon::parse('2026-09-01', 'Asia/Jakarta'); // dibuat sebelum 14 Oktober, jadi hari itu "belum", bukan tak digambar
        $habit->saveQuietly();
        $habit->entries()->create(['date' => '2026-10-15']);

        Livewire::test(HabitToday::class)
            ->assertSeeHtml('<i class="hf-heat-4" title="Kam, 15 Okt 2026 · selesai"')
            ->assertSeeHtml('<i class="hf-heat-0" title="Rab, 14 Okt 2026 · belum"');
    }

    public function test_centang_habit_memperbarui_kartu_dan_streak(): void
    {
        $habit = $this->user->habits()->create(['name' => 'Sholat']);

        Livewire::test(HabitToday::class)
            ->assertSee('0 / 1')
            ->assertSee('0 hari')
            ->call('toggleHabit', $habit->id)
            ->assertSee('1 / 1')
            ->assertSee('1 hari');

        $this->assertSame(1, $habit->entries()->count());
    }

    public function test_kartu_todo_menghitung_selesai_dari_yang_ada(): void
    {
        $this->user->todos()->create(['title' => 'A', 'date' => '2026-10-15', 'done' => true]);
        $this->user->todos()->create(['title' => 'B', 'date' => '2026-10-15']);

        Livewire::test(HabitToday::class)->assertSee('1 / 2')->assertSee('To-do hari ini (2/5)');
    }

    public function test_ubah_nama_habit_dengan_tanda_kutip_tidak_merusak(): void
    {
        $habit = $this->user->habits()->create(['name' => "Baca Qur'an \"pagi\""]);

        Livewire::test(HabitToday::class)
            ->call('startEditing', $habit->id)
            ->assertSet('editingName.'.$habit->id, "Baca Qur'an \"pagi\"")
            ->set('editingName.'.$habit->id, 'Baca Quran')
            ->call('saveName', $habit->id);

        $this->assertSame('Baca Quran', $habit->fresh()->name);
    }

    public function test_tidak_bisa_mengubah_habit_milik_orang_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $theirs = $other->habits()->create(['name' => 'Rahasia']);

        // Habit orang lain tidak ditemukan (404); nama tidak pernah sampai ke form pengubahan.
        Livewire::test(HabitToday::class)
            ->call('startEditing', $theirs->id)
            ->assertStatus(404);
    }

    public function test_nama_habit_di_halaman_di_escape(): void
    {
        $this->user->habits()->create(['name' => '<script>alert(1)</script>']);

        $this->get('/habit')->assertDontSee('<script>alert(1)</script>', false)->assertSee('&lt;script&gt;alert(1)&lt;/script&gt;', false);
    }
}
