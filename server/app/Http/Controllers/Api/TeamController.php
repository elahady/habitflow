<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Team;
use App\Models\TeamInvite;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * API tim untuk app Android (tahap 26) - lewat middleware user-token (login Google),
 * beda dari token per-HP tahap 19B.
 */
class TeamController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $teams = $request->user()->teams()->with('members:id,name,avatar')->get(
            ['teams.id', 'teams.name']
        );

        return response()->json(['teams' => $teams]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate(['name' => ['required', 'string', 'max:255']]);

        $team = Team::create(['name' => $validated['name'], 'created_by' => $request->user()->id]);
        $team->members()->attach($request->user()->id);

        return response()->json(['team' => $team], 201);
    }

    public function createInvite(Request $request): JsonResponse
    {
        $validated = $request->validate(['team_id' => ['required', 'integer']]);

        $team = $request->user()->teams()->findOrFail($validated['team_id']);
        $invite = TeamInvite::issue($team, $request->user());

        return response()->json([
            'token' => $invite->token,
            'url' => route('invites.show', $invite->token),
            'expires_at' => $invite->expires_at,
        ], 201);
    }

    public function acceptInvite(Request $request, string $token): JsonResponse
    {
        $invite = TeamInvite::where('token', $token)->firstOrFail();

        if (! $invite->isValid()) {
            return response()->json(['message' => 'Link undangan sudah tidak berlaku.'], 410);
        }

        $invite->team->members()->syncWithoutDetaching([$request->user()->id]);
        $invite->forceFill(['used_at' => now(), 'used_by' => $request->user()->id])->save();

        return response()->json(['team' => $invite->team]);
    }
}
