<?php

namespace App\Http\Controllers;

use App\Models\TeamInvite;
use Illuminate\Http\RedirectResponse;
use Illuminate\View\View;

class InviteController extends Controller
{
    public function show(string $token): View
    {
        $invite = TeamInvite::where('token', $token)->with('team')->firstOrFail();

        return view('invite', ['invite' => $invite, 'valid' => $invite->isValid()]);
    }

    public function accept(string $token): RedirectResponse
    {
        $invite = TeamInvite::where('token', $token)->firstOrFail();

        if (! $invite->isValid()) {
            return redirect()->route('dashboard')->with('status', 'Link undangan sudah tidak berlaku.');
        }

        $invite->team->members()->syncWithoutDetaching([auth()->id()]);
        $invite->forceFill(['used_at' => now(), 'used_by' => auth()->id()])->save();

        return redirect()->route('teams')->with('status', 'Berhasil gabung tim "'.$invite->team->name.'".');
    }
}
