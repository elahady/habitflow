<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\BloodPressureEntry;
use App\Models\HealthFocus;
use App\Models\StepDay;
use App\Models\WeightEntry;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Data kesehatan dari Android (tahap 30 langkah 3). Satu arah: HP menulis, web hanya membaca, jadi tanpa deteksi konflik
 * 409. Idempotent: berat dan tensi dikunci `user_id` + `time_millis`, langkah per tanggal (hari yang sama ditimpa karena
 * langkah hari ini terus bertambah), dan fokus satu baris per pengguna. Mengirim ulang tidak menggandakan apa pun.
 *
 * Berat dan tensi hanya bertambah di HP, jadi server tidak pernah menghapus baris dari kiriman yang lebih pendek.
 * Aturan fokus tidak ada di sini: server menyimpan hasil dan teks yang dihitung `domain/health/Focus.kt` di Android.
 */
class HealthController extends Controller
{
    private const MAX_ENTRIES = 500;

    private const MAX_STEP_DAYS = 14;

    /** Batas masuk akal, sama dengan `domain/health` di Android (berat 20-300 kg, tensi 50-300 / 30-200, nadi 20-250). */
    private const MIN_TIME_MILLIS = 946684800000; // 2000-01-01

    private const MAX_TIME_MILLIS = 4102444800000; // 2100-01-01

    public function upsert(Request $request): JsonResponse
    {
        $data = $request->validate([
            'timezone' => ['required', 'timezone:all'],
            'profile' => ['sometimes', 'nullable', 'array'],
            'profile.height_cm' => ['nullable', 'numeric', 'between:100,250'],
            'profile.target_kg' => ['nullable', 'numeric', 'between:20,300'],
            'profile.steps_target' => ['nullable', 'integer', 'between:1000,100000'],

            'weights' => ['sometimes', 'array', 'max:'.self::MAX_ENTRIES],
            'weights.*.time_millis' => ['required', 'integer', 'between:'.self::MIN_TIME_MILLIS.','.self::MAX_TIME_MILLIS, 'distinct'],
            'weights.*.kg' => ['required', 'numeric', 'between:20,300'],

            'blood_pressures' => ['sometimes', 'array', 'max:'.self::MAX_ENTRIES],
            'blood_pressures.*.time_millis' => ['required', 'integer', 'between:'.self::MIN_TIME_MILLIS.','.self::MAX_TIME_MILLIS, 'distinct'],
            'blood_pressures.*.systolic' => ['required', 'integer', 'between:50,300'],
            'blood_pressures.*.diastolic' => ['required', 'integer', 'between:30,200'],
            'blood_pressures.*.pulse' => ['nullable', 'integer', 'between:20,250'],
            'blood_pressures.*.note' => ['nullable', 'string', 'max:500'],

            'step_days' => ['sometimes', 'array', 'max:'.self::MAX_STEP_DAYS],
            'step_days.*.date' => ['required', 'date_format:Y-m-d', 'distinct'],
            'step_days.*.steps' => ['required', 'integer', 'between:0,200000'],

            'focus' => ['sometimes', 'nullable', 'array'],
            'focus.rule' => ['required_with:focus', 'string', 'regex:/^[A-Z_]{1,40}$/'],
            'focus.walk_minutes' => ['nullable', 'integer', 'between:1,255'],
            'focus.title' => ['required_with:focus', 'string', 'max:200'],
            'focus.sentence' => ['required_with:focus', 'string', 'max:600'],
            'focus.note' => ['nullable', 'string', 'max:600'],
            'focus.reasons' => ['nullable', 'array', 'max:6'],
            'focus.reasons.*.label' => ['required', 'string', 'max:40'],
            'focus.reasons.*.value' => ['required', 'string', 'max:120'],
            'focus.computed_at' => ['required_with:focus', 'date'],
        ]);

        $user = $request->user();
        $now = now();

        DB::transaction(function () use ($data, $user, $now) {
            if (! empty($data['weights'])) {
                WeightEntry::upsert(
                    collect($data['weights'])->map(fn (array $w) => [
                        'user_id' => $user->id, 'time_millis' => $w['time_millis'], 'kg' => round((float) $w['kg'], 1),
                        'created_at' => $now, 'updated_at' => $now,
                    ])->all(),
                    ['user_id', 'time_millis'],
                    ['kg', 'updated_at'],
                );
            }

            if (! empty($data['blood_pressures'])) {
                BloodPressureEntry::upsert(
                    collect($data['blood_pressures'])->map(fn (array $b) => [
                        'user_id' => $user->id, 'time_millis' => $b['time_millis'],
                        'systolic' => $b['systolic'], 'diastolic' => $b['diastolic'],
                        'pulse' => $b['pulse'] ?? null, 'note' => $b['note'] ?? null,
                        'created_at' => $now, 'updated_at' => $now,
                    ])->all(),
                    ['user_id', 'time_millis'],
                    ['systolic', 'diastolic', 'pulse', 'note', 'updated_at'],
                );
            }

            if (! empty($data['step_days'])) {
                StepDay::upsert(
                    collect($data['step_days'])->map(fn (array $d) => [
                        'user_id' => $user->id, 'date' => $d['date'], 'steps' => $d['steps'],
                        'created_at' => $now, 'updated_at' => $now,
                    ])->all(),
                    ['user_id', 'date'],
                    ['steps', 'updated_at'],
                );
            }

            // Baris profil dan fokus: kolom yang tidak dikirim tidak disentuh, `null` yang dikirim mengosongkan.
            $row = ['timezone' => $data['timezone']];
            if (array_key_exists('profile', $data) && is_array($data['profile'])) {
                foreach (['height_cm', 'target_kg', 'steps_target'] as $key) {
                    if (array_key_exists($key, $data['profile'])) {
                        $row[$key] = $data['profile'][$key];
                    }
                }
            }
            if (! empty($data['focus'])) {
                $focus = $data['focus'];
                $row += [
                    'rule' => $focus['rule'],
                    'walk_minutes' => $focus['walk_minutes'] ?? null,
                    'title' => $focus['title'],
                    'sentence' => $focus['sentence'],
                    'note' => $focus['note'] ?? null,
                    'reasons' => $focus['reasons'] ?? [],
                    // Eloquent menyimpan jam dinding apa adanya tanpa mengonversi zona, jadi tanda waktu ber-offset
                    // (mis. +07:00) harus dijadikan UTC dulu atau tersimpan salah beberapa jam.
                    'computed_at' => Carbon::parse($focus['computed_at'])->utc(),
                ];
            }
            HealthFocus::updateOrCreate(['user_id' => $user->id], $row);
        });

        return response()->json([
            'weights' => count($data['weights'] ?? []),
            'blood_pressures' => count($data['blood_pressures'] ?? []),
            'step_days' => count($data['step_days'] ?? []),
            'focus' => ! empty($data['focus']),
        ]);
    }
}
