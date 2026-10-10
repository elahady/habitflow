<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Team;
use App\Models\TeamTodo;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * API to-do tim untuk app Android (tahap 26). Polling biasa (bukan WebSocket) - app
 * fetch ulang daftar ini secara berkala, sesuai keputusan "realtime-ish lewat polling".
 */
class TeamTodoController extends Controller
{
    private function authorizedTeam(Request $request, int $teamId): Team
    {
        return $request->user()->teams()->findOrFail($teamId);
    }

    private function authorizedTodo(Request $request, int $todoId): TeamTodo
    {
        return TeamTodo::whereIn('team_id', $request->user()->teams()->pluck('teams.id'))->findOrFail($todoId);
    }

    public function index(Request $request, int $team): JsonResponse
    {
        $todos = $this->authorizedTeam($request, $team)->todos()->with('assignee:id,name,avatar')->get();

        return response()->json(['todos' => $todos]);
    }

    public function store(Request $request, int $team): JsonResponse
    {
        $validated = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'assigned_to' => ['nullable', 'integer'],
        ]);

        $todo = $this->authorizedTeam($request, $team)->todos()->create([
            ...$validated,
            'created_by' => $request->user()->id,
        ]);

        return response()->json(['todo' => $todo], 201);
    }

    public function update(Request $request, int $todo): JsonResponse
    {
        $validated = $request->validate([
            'title' => ['sometimes', 'string', 'max:255'],
            'assigned_to' => ['sometimes', 'nullable', 'integer'],
            'done' => ['sometimes', 'boolean'],
        ]);

        $model = $this->authorizedTodo($request, $todo);

        if (array_key_exists('done', $validated)) {
            $validated['done_by'] = $validated['done'] ? $request->user()->id : null;
            $validated['done_at'] = $validated['done'] ? now() : null;
        }

        $model->update($validated);

        return response()->json(['todo' => $model->fresh('assignee')]);
    }

    public function destroy(Request $request, int $todo): JsonResponse
    {
        $this->authorizedTodo($request, $todo)->delete();

        return response()->json(status: 204);
    }
}
