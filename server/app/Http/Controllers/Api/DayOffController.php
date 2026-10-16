<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Tanggal libur dari Android (tahap 29 langkah 5). Satu arah: HP menggabungkan libur manual dan libur nasional yang
 * tidak dibatalkan, lalu mengirim tanggal yang libur dalam satu jendela. Server menggantikan isi jendela itu dan tidak
 * menyentuh tanggal di luarnya, jadi riwayat libur lama tetap ada. Idempotent, tanpa deteksi konflik.
 */
class DayOffController extends Controller
{
    /** Panjang jendela terbesar dalam hari; Android mengirim 14. */
    private const MAX_WINDOW_DAYS = 62;

    public function index(Request $request): JsonResponse
    {
        $query = $request->user()->daysOff()->orderBy('date');

        if ($request->filled('from')) {
            $query->whereDate('date', '>=', $request->date('from'));
        }

        return response()->json(['days_off' => $query->pluck('date')->values()]);
    }

    public function replace(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'from' => ['required', 'date_format:Y-m-d'],
            'to' => ['required', 'date_format:Y-m-d', 'after_or_equal:from'],
            'dates' => ['present', 'array', 'max:'.self::MAX_WINDOW_DAYS],
            'dates.*' => ['date_format:Y-m-d', 'distinct', 'after_or_equal:from', 'before_or_equal:to'],
        ]);

        $from = Carbon::parse($validated['from']);
        $to = Carbon::parse($validated['to']);
        if ($from->diffInDays($to) >= self::MAX_WINDOW_DAYS) {
            return response()->json(['message' => 'Jendela terlalu panjang (paling banyak '.self::MAX_WINDOW_DAYS.' hari).'], 422);
        }

        $user = $request->user();
        DB::transaction(function () use ($user, $validated) {
            $user->daysOff()->whereBetween('date', [$validated['from'], $validated['to']])->delete();
            foreach ($validated['dates'] as $date) {
                $user->daysOff()->create(['date' => $date]);
            }
        });

        return response()->json([
            'days_off' => $user->daysOff()->whereBetween('date', [$validated['from'], $validated['to']])->orderBy('date')->pluck('date')->values(),
        ]);
    }
}
