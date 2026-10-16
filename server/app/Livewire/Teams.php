<?php

namespace App\Livewire;

use App\Models\Team;
use App\Models\TeamInvite;
use App\Models\TeamTodo;
use App\Support\UserDay;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Kelola tim dan to-do tim (tahap 26): buat tim, buat link undangan, CRUD to-do dengan
 * assignee. Diakses dari Dashboard lewat nav "Tugas Rumah".
 */
class Teams extends Component
{
    public string $newTeamName = '';

    public ?string $inviteLink = null;

    /** Tim yang link undangannya baru dibuat, supaya link hanya tampil di kartu timnya. */
    public ?int $inviteTeamId = null;

    public array $newTodoTitle = [];

    private function team(int $teamId): Team
    {
        return auth()->user()->teams()->findOrFail($teamId);
    }

    public function createTeam(): void
    {
        $this->validate(['newTeamName' => ['required', 'string', 'max:255']]);

        $team = Team::create([
            'name' => $this->newTeamName,
            'created_by' => auth()->id(),
        ]);

        $team->members()->attach(auth()->id());

        $this->newTeamName = '';
    }

    public function createInvite(int $teamId): void
    {
        $invite = TeamInvite::issue($this->team($teamId), auth()->user());

        $this->inviteLink = route('invites.show', $invite->token);
        $this->inviteTeamId = $teamId;
    }

    public function addTodo(int $teamId): void
    {
        $title = trim($this->newTodoTitle[$teamId] ?? '');

        if ($title === '') {
            return;
        }

        $this->team($teamId)->todos()->create([
            'title' => $title,
            'created_by' => auth()->id(),
        ]);

        $this->newTodoTitle[$teamId] = '';
    }

    public function toggleDone(int $todoId): void
    {
        $todo = TeamTodo::whereIn('team_id', auth()->user()->teams()->pluck('teams.id'))->findOrFail($todoId);

        $todo->done
            ? $todo->update(['done' => false, 'done_by' => null, 'done_at' => null])
            : $todo->update(['done' => true, 'done_by' => auth()->id(), 'done_at' => now()]);
    }

    public function assignTodo(int $todoId, ?int $userId): void
    {
        $todo = TeamTodo::whereIn('team_id', auth()->user()->teams()->pluck('teams.id'))->findOrFail($todoId);

        $todo->update(['assigned_to' => $userId]);
    }

    public function deleteTodo(int $todoId): void
    {
        TeamTodo::whereIn('team_id', auth()->user()->teams()->pluck('teams.id'))->findOrFail($todoId)->delete();
    }

    public function render(): View
    {
        $user = auth()->user();
        $teams = $user->teams()->with(['members', 'todos.assignee'])->get();
        $todos = $teams->flatMap->todos;
        $mine = $todos->where('assigned_to', $user->id);

        return view('livewire.teams', [
            'teams' => $teams,
            'dateLabel' => UserDay::now($user)->locale('id')->translatedFormat('l, j F Y'),
            // Sama dengan kartu Tugas Rumah di Beranda (DaySummary::teamMine): milik saya, belum selesai.
            'mineOpen' => $mine->where('done', false)->count(),
            'mineTotal' => $mine->count(),
            'allDone' => $todos->where('done', true)->count(),
            'allTotal' => $todos->count(),
        ])->layout('components.layout', ['title' => 'Tugas Rumah']);
    }
}
