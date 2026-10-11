<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PrayerDay;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Cache waktu sholat harian dari Android (tahap 29, opsi a). Satu arah: HP menghitung dengan
 * mesin Ephemeris-nya lalu mengirim beberapa hari sekaligus; server tidak pernah menghitung.
 * Mengirim ulang menimpa baris tanggal yang sama (idempotent), jadi tanpa deteksi konflik 409.
 */
class PrayerDayController extends Controller
{
    private const MAX_DAYS = 14;

    public function index(Request $request): JsonResponse
    {
        $query = $request->user()->prayerDays()->orderBy('date');

        if ($request->filled('from')) {
            $query->whereDate('date', '>=', $request->date('from'));
        }

        return response()->json(['prayer_days' => $query->get()]);
    }

    public function upsert(Request $request): JsonResponse
    {
        $rules = [
            'timezone' => ['required', 'timezone:all'],
            'days' => ['required', 'array', 'min:1', 'max:'.self::MAX_DAYS],
            'days.*.date' => ['required', 'date_format:Y-m-d', 'distinct'],
        ];
        foreach (PrayerDay::PRAYERS as $prayer) {
            $rules["days.*.$prayer"] = ['present', 'nullable', 'date_format:H:i'];
        }
        $validated = $request->validate($rules);

        $user = $request->user();
        $days = DB::transaction(fn () => collect($validated['days'])->map(
            fn (array $day) => $user->prayerDays()->updateOrCreate(
                ['date' => $day['date']],
                ['timezone' => $validated['timezone']] + array_diff_key($day, ['date' => null]),
            ),
        ));

        return response()->json(['prayer_days' => $days->map->fresh()->values()]);
    }
}
