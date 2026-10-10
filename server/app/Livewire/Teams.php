<?php

namespace App\Livewire;

use App\Models\Team;
use App\Models\TeamInvite;
use App\Models\TeamTodo;
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
        return view('livewire.teams', [
            'teams' => auth()->user()->teams()->with(['members', 'todos.assignee'])->get(),
        ])->layout('components.layout', ['title' => 'Tugas Rumah']);
    }
}
