<?php

namespace App\Livewire;

use App\Models\Team;
use App\Models\TeamInvite;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Kelola tim (tahap 26): buat tim, buat link undangan. Diakses dari Dashboard.
 */
class Teams extends Component
{
    public string $newTeamName = '';

    public ?string $inviteLink = null;

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
        $team = auth()->user()->teams()->findOrFail($teamId);

        $invite = TeamInvite::issue($team, auth()->user());

        $this->inviteLink = route('invites.show', $invite->token);
    }

    public function render(): View
    {
        return view('livewire.teams', [
            'teams' => auth()->user()->teams()->with('members')->get(),
        ])->layout('components.layout', ['title' => 'Tugas Rumah']);
    }
}
