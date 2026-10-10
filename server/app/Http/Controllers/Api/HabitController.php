<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Habit;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * CRUD habit per user (tahap 28). Centang per tanggal lewat endpoint entries di bawah,
 * bukan lewat update() - supaya aksi "centang" tetap idempotent tanpa deteksi konflik,
 * sedangkan ubah nama tetap dicek konfliknya (pola sama seperti rencana follow-up kerja).
 */
class HabitController extends Controller
{
    private function authorizedHabit(Request $request, int $habitId): Habit
    {
        return $request->user()->habits()->findOrFail($habitId);
    }

    public function index(Request $request): JsonResponse
    {
        $habits = $request->user()->habits()
            ->orderByDesc('is_mandatory')
            ->orderBy('sort_order')
            ->orderBy('id')
            ->get();

        if ($request->filled('date')) {
            $date = Carbon::parse($request->string('date'));
            $doneIds = $request->user()->habits()
                ->whereHas('entries', fn ($q) => $q->whereDate('date', $date))
                ->pluck('id');
            $habits->each(fn (Habit $habit) => $habit->setAttribute('done', $doneIds->contains($habit->id)));
        }

        return response()->json(['habits' => $habits]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'is_mandatory' => ['sometimes', 'boolean'],
        ]);

        $nextOrder = (int) ($request->user()->habits()->max('sort_order') ?? 0) + 1;

        $habit = $request->user()->habits()->create([
            ...$validated,
            'sort_order' => $nextOrder,
        ]);

        return response()->json(['habit' => $habit], 201);
    }

    public function update(Request $request, int $habit): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['sometimes', 'string', 'max:255'],
            'sort_order' => ['sometimes', 'integer', 'min:0'],
            'is_mandatory' => ['sometimes', 'boolean'],
            'updated_at' => ['required_with:name', 'date'],
        ]);

        $model = $this->authorizedHabit($request, $habit);

        if (array_key_exists('name', $validated) && Carbon::parse($validated['updated_at'])->ne($model->updated_at)) {
            return response()->json(['message' => 'Berubah di tempat lain.', 'habit' => $model], 409);
        }

        $model->update(array_diff_key($validated, ['updated_at' => null]));

        return response()->json(['habit' => $model->fresh()]);
    }

    public function destroy(Request $request, int $habit): JsonResponse
    {
        $model = $this->authorizedHabit($request, $habit);

        if ($model->is_mandatory) {
            return response()->json(['message' => 'Habit wajib tidak bisa dihapus.'], 422);
        }

        $model->delete();

        return response()->json(status: 204);
    }

    /** Tandai/batal centang habit untuk satu tanggal. Idempotent: panggilan ulang hasilnya sama. */
    public function toggleEntry(Request $request, int $habit): JsonResponse
    {
        $validated = $request->validate(['date' => ['required', 'date']]);
        $model = $this->authorizedHabit($request, $habit);
        $date = Carbon::parse($validated['date'])->toDateString();

        $entry = $model->entries()->whereDate('date', $date)->first();

        if ($entry) {
            $entry->delete();

            return response()->json(['done' => false]);
        }

        $model->entries()->create(['date' => $date]);

        return response()->json(['done' => true]);
    }
}
