<?php

namespace Tests\Feature;

use App\Livewire\FollowUps;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Livewire\Livewire;
use Tests\TestCase;

class FollowUpsPageTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        $this->actingAs($this->user);
    }

    public function test_halaman_butuh_login(): void
    {
        auth()->logout();

        $this->get('/follow-up')->assertRedirect('/login');
    }

    public function test_halaman_tampil_dengan_daftar(): void
    {
        $this->user->followUps()->create(['title' => 'Tanya Budi', 'status' => 'ACTIVE']);

        $this->get('/follow-up')->assertOk()->assertSee('Tanya Budi')->assertSee('Aktif (1)');
    }

    public function test_catat_cepat_masuk_inbox(): void
    {
        Livewire::test(FollowUps::class)
            ->set('newTitle', 'Cek invoice')
            ->call('add')
            ->assertSet('newTitle', '');

        $this->assertSame('INBOX', $this->user->followUps()->first()->status);
    }

    public function test_catat_judul_kosong_diabaikan(): void
    {
        Livewire::test(FollowUps::class)->set('newTitle', '   ')->call('add');

        $this->assertSame(0, $this->user->followUps()->count());
    }

    public function test_ubah_menyimpan_semua_field(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama']);

        Livewire::test(FollowUps::class)
            ->call('edit', $item->id)
            ->set('form.title', 'baru')
            ->set('form.status', 'WAITING')
            ->set('form.date', '2026-10-15')
            ->set('form.time', '14:30')
            ->set('form.person', 'Sari')
            ->set('form.note', 'tunggu balasan')
            ->call('save')
            ->assertSet('editingId', null);

        $item->refresh();
        $this->assertSame('baru', $item->title);
        $this->assertSame('WAITING', $item->status);
        $this->assertSame('2026-10-15', $item->date->format('Y-m-d'));
        $this->assertSame('14:30', substr($item->time, 0, 5));
        $this->assertSame('Sari', $item->person);
    }

    public function test_kosongkan_field_menyimpan_null(): void
    {
        $item = $this->user->followUps()->create(['title' => 'x', 'person' => 'Sari', 'date' => '2026-10-15', 'time' => '09:00']);

        Livewire::test(FollowUps::class)
            ->call('edit', $item->id)
            ->set('form.person', '')
            ->set('form.date', '')
            ->set('form.time', '')
            ->call('save');

        $item->refresh();
        $this->assertNull($item->person);
        $this->assertNull($item->date);
        $this->assertNull($item->time);
    }

    public function test_validasi_judul_dan_jam(): void
    {
        $item = $this->user->followUps()->create(['title' => 'x']);

        Livewire::test(FollowUps::class)
            ->call('edit', $item->id)
            ->set('form.title', '')
            ->set('form.time', '25:99')
            ->call('save')
            ->assertHasErrors(['form.title', 'form.time']);
    }

    public function test_status_selesai_mengisi_done_at_dan_membukanya_lagi_menghapus(): void
    {
        $item = $this->user->followUps()->create(['title' => 'x', 'status' => 'ACTIVE']);
        $page = Livewire::test(FollowUps::class);

        $page->call('markDone', $item->id);
        $this->assertSame('DONE', $item->fresh()->status);
        $this->assertNotNull($item->fresh()->done_at);

        $page->call('reopen', $item->id);
        $this->assertSame('ACTIVE', $item->fresh()->status);
        $this->assertNull($item->fresh()->done_at);
    }

    public function test_ubah_status_ke_selesai_lewat_form_mengisi_done_at(): void
    {
        $item = $this->user->followUps()->create(['title' => 'x']);

        Livewire::test(FollowUps::class)
            ->call('edit', $item->id)
            ->set('form.status', 'DONE')
            ->call('save');

        $this->assertNotNull($item->fresh()->done_at);
    }

    public function test_bentrok_tidak_menimpa_diam_diam(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama']);
        $page = Livewire::test(FollowUps::class)->call('edit', $item->id);

        $this->travel(5)->minutes();
        $item->update(['title' => 'diubah di HP']);

        $page->set('form.title', 'diubah di web')->call('save')
            ->assertSet('conflict.title', 'diubah di HP')
            ->assertSet('editingId', $item->id);

        $this->assertSame('diubah di HP', $item->fresh()->title);
    }

    public function test_bentrok_pilih_timpa(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama']);
        $page = Livewire::test(FollowUps::class)->call('edit', $item->id);

        $this->travel(5)->minutes();
        $item->update(['title' => 'diubah di HP']);

        $page->set('form.title', 'diubah di web')->call('save')->call('overwrite')
            ->assertSet('editingId', null)
            ->assertSet('conflict', null);

        $this->assertSame('diubah di web', $item->fresh()->title);
    }

    public function test_bentrok_pilih_pakai_yang_terbaru(): void
    {
        $item = $this->user->followUps()->create(['title' => 'lama']);
        $page = Livewire::test(FollowUps::class)->call('edit', $item->id);

        $this->travel(5)->minutes();
        $item->update(['title' => 'diubah di HP']);

        $page->set('form.title', 'diubah di web')->call('save')->call('useLatest')
            ->assertSet('editingId', null)
            ->assertSet('conflict', null);

        $this->assertSame('diubah di HP', $item->fresh()->title);
    }

    public function test_hapus(): void
    {
        $item = $this->user->followUps()->create(['title' => 'x']);

        Livewire::test(FollowUps::class)->call('delete', $item->id);

        $this->assertSame(0, $this->user->followUps()->count());
    }

    public function test_tidak_bisa_menyentuh_milik_user_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $item = $other->followUps()->create(['title' => 'rahasia', 'status' => 'ACTIVE']);

        Livewire::test(FollowUps::class)
            ->call('edit', $item->id)
            ->assertSet('editingId', null)
            ->call('markDone', $item->id)
            ->call('delete', $item->id);

        $item->refresh();
        $this->assertSame('ACTIVE', $item->status);
        $this->assertSame('rahasia', $item->title);
    }
}
