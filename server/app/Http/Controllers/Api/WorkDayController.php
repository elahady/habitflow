<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Validator;

/**
 * Catatan harian kerja per user (tahap 33 langkah 3): catatan EOD dan kapan daily scrum dan EOD selesai.
 * Ditulis dari HP dan dari web, jadi mengikuti pola follow-up: PUT per tanggal adalah upsert, dan kalau baris
 * sudah ada wajib menyertakan `updated_at` yang terakhir dilihat client. Kalau beda dari server, dibalas 409
 * berisi versi terbaru, bukan ditimpa diam-diam. Baris yang belum ada dibuat tanpa `updated_at`.
 */
class WorkDayController extends Controller
{
    private const MAX_LIST = 60;

    public function index(Request $request): JsonResponse
    {
        $request->validate(['from' => ['sometimes', 'date_format:Y-m-d']]);

        $query = $request->user()->workDays()->orderByDesc('date');
        if ($request->filled('from')) {
            $query->where('date', '>=', $request->string('from'));
        }

        return response()->json(['work_days' => $query->limit(self::MAX_LIST)->get()]);
    }

    public function upsert(Request $request, string $date): JsonResponse
    {
        $validated = Validator::make(
            ['date' => $date] + $request->all(),
            [
                'date' => ['required', 'date_format:Y-m-d'],
                'eod_note' => ['sometimes', 'nullable', 'string', 'max:5000'],
                'scrum_done_at' => ['sometimes', 'nullable', 'date'],
                'eod_done_at' => ['sometimes', 'nullable', 'date'],
                'updated_at' => ['sometimes', 'nullable', 'date'],
            ],
        )->validate();

        $existing = $request->user()->workDays()->where('date', $date)->first();
        $fields = array_diff_key($validated, ['date' => null, 'updated_at' => null]);

        if (! $existing) {
            $created = $request->user()->workDays()->create(['date' => $date] + $fields)->fresh();

            return response()->json(['work_day' => $created], 201);
        }

        $seen = $validated['updated_at'] ?? null;
        if ($seen === null || Carbon::parse($seen)->ne($existing->updated_at)) {
            return response()->json(['message' => 'Berubah di tempat lain.', 'work_day' => $existing], 409);
        }

        $existing->update($fields);

        return response()->json(['work_day' => $existing->fresh()]);
    }
}
