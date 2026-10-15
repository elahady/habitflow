<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Ringkasan harian dari Claude (tahap 32): satu baris per tanggal, ditulis oleh skill
 * `habitflow-morning-routine` dan dibaca app/web sebagai kartu read-only. POST adalah upsert per
 * tanggal, jadi aman dipanggil ulang di hari yang sama. Kolom yang tidak dikirim tidak disentuh
 * (skill yang hanya mengirim `email_digest` tidak menghapus `meeting_note`), dan kolom yang
 * dikirim `null` dikosongkan. Tanpa deteksi konflik 409: tidak ada penyunting manual di app.
 */
class DailyBriefController extends Controller
{
    private const MAX_LIST = 31;

    public function index(Request $request): JsonResponse
    {
        $request->validate([
            'date' => ['sometimes', 'date_format:Y-m-d'],
            'from' => ['sometimes', 'date_format:Y-m-d'],
        ]);

        $query = $request->user()->dailyBriefs()->orderByDesc('date');

        if ($request->filled('date')) {
            $query->where('date', $request->string('date'));
        }
        if ($request->filled('from')) {
            $query->where('date', '>=', $request->string('from'));
        }

        return response()->json(['daily_briefs' => $query->limit(self::MAX_LIST)->get()]);
    }

    public function upsert(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'date' => ['required', 'date_format:Y-m-d'],
            'email_digest' => ['sometimes', 'nullable', 'string', 'max:20000'],
            'meeting_note' => ['sometimes', 'nullable', 'string', 'max:20000'],
        ]);

        $brief = $request->user()->dailyBriefs()->updateOrCreate(
            ['date' => $validated['date']],
            array_diff_key($validated, ['date' => null]),
        );

        return response()->json(['daily_brief' => $brief->fresh()], $brief->wasRecentlyCreated ? 201 : 200);
    }
}
