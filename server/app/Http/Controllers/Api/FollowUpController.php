<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\FollowUp;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;

/**
 * CRUD follow-up kerja per user (tahap 27). Beda dari snapshot 19B: sumber kebenarannya di server,
 * dan PUT wajib menyertakan `updated_at` yang terakhir dilihat client - kalau beda dari server
 * (ada perubahan lain masuk duluan) dibalas 409 berisi versi terbaru, bukan ditimpa diam-diam.
 */
class FollowUpController extends Controller
{
    private function authorizedFollowUp(Request $request, int $id): FollowUp
    {
        return $request->user()->followUps()->findOrFail($id);
    }

    /** Aturan field yang bisa diisi; [$required] true untuk POST (judul wajib), false untuk PUT. */
    private function rules(bool $required): array
    {
        $title = $required ? ['required'] : ['sometimes', 'required'];

        return [
            'title' => [...$title, 'string', 'max:255'],
            'status' => ['sometimes', Rule::in(FollowUp::STATUSES)],
            'date' => ['sometimes', 'nullable', 'date'],
            'time' => ['sometimes', 'nullable', 'date_format:H:i'],
            'person' => ['sometimes', 'nullable', 'string', 'max:255'],
            'note' => ['sometimes', 'nullable', 'string', 'max:5000'],
            'done_at' => ['sometimes', 'nullable', 'date'],
            'picked_date' => ['sometimes', 'nullable', 'date'],
            'event_id' => ['sometimes', 'nullable', 'integer'],
            'event_date' => ['sometimes', 'nullable', 'date'],
            'event_title' => ['sometimes', 'nullable', 'string', 'max:255'],
        ];
    }

    public function index(Request $request): JsonResponse
    {
        $query = $request->user()->followUps();

        if ($request->filled('status')) {
            $query->where('status', $request->string('status'));
        }

        return response()->json(['follow_ups' => $query->orderBy('id')->get()]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate($this->rules(required: true));

        $followUp = $request->user()->followUps()->create($validated)->fresh();

        return response()->json(['follow_up' => $followUp], 201);
    }

    public function update(Request $request, int $followUp): JsonResponse
    {
        $validated = $request->validate($this->rules(required: false) + [
            'updated_at' => ['required', 'date'],
        ]);

        $model = $this->authorizedFollowUp($request, $followUp);

        if (Carbon::parse($validated['updated_at'])->ne($model->updated_at)) {
            return response()->json(['message' => 'Berubah di tempat lain.', 'follow_up' => $model], 409);
        }

        $model->update(array_diff_key($validated, ['updated_at' => null]));

        return response()->json(['follow_up' => $model->fresh()]);
    }

    public function destroy(Request $request, int $followUp): JsonResponse
    {
        $this->authorizedFollowUp($request, $followUp)->delete();

        return response()->json(status: 204);
    }
}
