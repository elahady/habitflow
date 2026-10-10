<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Todo;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * CRUD to-do harian per user (tahap 28). Maksimal 5 per tanggal dan carry-over
 * (to-do belum selesai dari tanggal lampau pindah ke hari ini) ditegakkan di sini,
 * sama seperti aturan domain Android (lihat docs/concept.md tahap 3).
 */
class TodoController extends Controller
{
    private const MAX_PER_DAY = 5;

    private function authorizedTodo(Request $request, int $todoId): Todo
    {
        return $request->user()->todos()->findOrFail($todoId);
    }

    public function index(Request $request): JsonResponse
    {
        $date = Carbon::parse($request->string('date', Carbon::today()->toDateString()))->toDateString();
        $today = Carbon::today()->toDateString();

        if ($date === $today) {
            $request->user()->todos()
                ->where('done', false)
                ->whereDate('date', '<', $today)
                ->update(['date' => $today]);
        }

        $todos = $request->user()->todos()->whereDate('date', $date)->orderBy('id')->get();

        return response()->json(['todos' => $todos]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'date' => ['required', 'date'],
        ]);

        $count = $request->user()->todos()->whereDate('date', $validated['date'])->count();
        if ($count >= self::MAX_PER_DAY) {
            return response()->json(['message' => 'Sudah 5 to-do untuk tanggal ini.'], 422);
        }

        $todo = $request->user()->todos()->create($validated);

        return response()->json(['todo' => $todo], 201);
    }

    public function update(Request $request, int $todo): JsonResponse
    {
        $validated = $request->validate([
            'title' => ['sometimes', 'string', 'max:255'],
            'date' => ['sometimes', 'date'],
            'done' => ['sometimes', 'boolean'],
            'updated_at' => ['required_with:title', 'date'],
        ]);

        $model = $this->authorizedTodo($request, $todo);

        if (array_key_exists('title', $validated) && Carbon::parse($validated['updated_at'])->ne($model->updated_at)) {
            return response()->json(['message' => 'Berubah di tempat lain.', 'todo' => $model], 409);
        }

        $model->update(array_diff_key($validated, ['updated_at' => null]));

        return response()->json(['todo' => $model->fresh()]);
    }

    public function destroy(Request $request, int $todo): JsonResponse
    {
        $this->authorizedTodo($request, $todo)->delete();

        return response()->json(status: 204);
    }
}
