<?php

namespace Tests\Feature;

use App\Models\Team;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class TeamTodoApiTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private Team $team;

    private string $plain;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'Roziq']);
        [, $this->plain] = UserToken::issue($this->user, 'hp-uji');
        $this->team = Team::create(['name' => 'Rumah Roziq', 'created_by' => $this->user->id]);
        $this->team->members()->attach([$this->user->id]);
    }

    private function auth(): array
    {
        return ['Authorization' => 'Bearer '.$this->plain];
    }

    public function test_tugas_baru_dibalas_lengkap_termasuk_done_false(): void
    {
        // Android membaca `done` dari balasan 201 dan menolak balasan yang tidak memuatnya ("Server bermasalah (kode 200)").
        $this->postJson("/api/v1/teams/{$this->team->id}/todos", ['title' => 'Cuci piring'], $this->auth())
            ->assertCreated()
            ->assertJsonPath('todo.title', 'Cuci piring')
            ->assertJsonPath('todo.done', false)
            ->assertJsonPath('todo.assigned_to', null);
    }

    public function test_daftar_tugas_memuat_done_dan_assigned_to(): void
    {
        $this->postJson("/api/v1/teams/{$this->team->id}/todos", ['title' => 'Cuci piring', 'assigned_to' => $this->user->id], $this->auth())
            ->assertCreated();

        $this->getJson("/api/v1/teams/{$this->team->id}/todos", $this->auth())
            ->assertOk()
            ->assertJsonPath('todos.0.title', 'Cuci piring')
            ->assertJsonPath('todos.0.done', false)
            ->assertJsonPath('todos.0.assigned_to', $this->user->id);
    }

    public function test_menyelesaikan_tugas_membalas_done_true(): void
    {
        $id = $this->postJson("/api/v1/teams/{$this->team->id}/todos", ['title' => 'Cuci piring'], $this->auth())
            ->json('todo.id');

        $this->putJson("/api/v1/teams/todos/{$id}", ['done' => true], $this->auth())
            ->assertOk()
            ->assertJsonPath('todo.done', true);
    }

    public function test_bukan_anggota_tidak_bisa_menambah_tugas(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'Lain']);
        [, $otherToken] = UserToken::issue($other, 'hp-lain');

        $this->postJson("/api/v1/teams/{$this->team->id}/todos", ['title' => 'Intip'], ['Authorization' => 'Bearer '.$otherToken])
            ->assertNotFound();
    }
}
