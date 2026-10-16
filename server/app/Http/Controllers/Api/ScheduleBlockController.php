<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\ScheduleBlock;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

/**
 * CRUD blok jadwal harian per user (tahap 29). Pola sama seperti FollowUpController: PUT wajib
 * menyertakan `updated_at` terakhir yang dilihat client, beda dari server dibalas 409.
 * Tautan habit dikirim sebagai `habit_ids` (id habit di server), dan selalu menggantikan
 * seluruh tautan blok itu. Blok tidak punya status "selesai" - hanya dibaca di web.
 */
class ScheduleBlockController extends Controller
{
    /** Batas blok dalam satu kiriman [replace]; jadwal awal 16 blok. */
    private const MAX_BLOCKS = 100;

    private function authorizedBlock(Request $request, int $id): ScheduleBlock
    {
        return $request->user()->scheduleBlocks()->findOrFail($id);
    }

    /** [$required] true untuk POST (nama dan waktu mulai wajib), false untuk PUT. */
    private function rules(bool $required): array
    {
        $req = $required ? ['required'] : ['sometimes', 'required'];

        return [
            'name' => [...$req, 'string', 'max:255'],
            'start_type' => [...$req, Rule::in([ScheduleBlock::START_FIXED, ScheduleBlock::START_PRAYER])],
            'start_value' => [...$req, 'integer', 'between:-1440,1440'],
            'prayer' => ['sometimes', 'nullable', Rule::in(ScheduleBlock::PRAYERS)],
            'duration_minutes' => ['sometimes', 'integer', 'between:0,1440'],
            'end_minute_of_day' => ['sometimes', 'nullable', 'integer', 'between:0,1439'],
            'active_days' => ['sometimes', 'integer', 'between:1,127'],
            'level' => ['sometimes', Rule::in(ScheduleBlock::LEVELS)],
            'sort_order' => ['sometimes', 'integer', 'min:0'],
            'work_action' => ['sometimes', 'nullable', Rule::in(ScheduleBlock::WORK_ACTIONS)],
            'work_reminders' => ['sometimes', 'boolean'],
            'habit_ids' => ['sometimes', 'array'],
            'habit_ids.*' => ['integer', 'distinct'],
        ];
    }

    /** Pesan galat kalau jam tetap tidak 0..1439 menit atau membawa prayer, atau blok sholat tanpa prayer; null kalau sah. */
    private function startError(array $data, ?ScheduleBlock $current = null): ?string
    {
        $type = (int) ($data['start_type'] ?? $current?->start_type);
        $value = (int) ($data['start_value'] ?? $current?->start_value);
        $prayer = array_key_exists('prayer', $data) ? $data['prayer'] : $current?->prayer;

        if ($type === ScheduleBlock::START_PRAYER && $prayer === null) {
            return 'Blok berpatokan sholat wajib punya prayer.';
        }
        if ($type === ScheduleBlock::START_FIXED && ($prayer !== null || $value < 0 || $value > 1439)) {
            return 'Jam tetap harus 0-1439 menit dan tanpa prayer.';
        }

        return null;
    }

    private function checkStart(array $data, ?ScheduleBlock $current = null): ?JsonResponse
    {
        $message = $this->startError($data, $current);

        return $message === null ? null : response()->json(['message' => $message], 422);
    }

    /** Habit yang ditautkan harus milik user ini. Mengembalikan pesan galat, atau null kalau sah. */
    private function checkHabits(Request $request, array $habitIds): ?JsonResponse
    {
        $owned = $request->user()->habits()->whereIn('id', $habitIds)->count();

        return $owned === count($habitIds)
            ? null
            : response()->json(['message' => 'Ada habit yang tidak ditemukan.'], 422);
    }

    private function serialize(ScheduleBlock $block): array
    {
        $data = $block->toArray();
        $data['habit_ids'] = $block->habits->pluck('id')->sort()->values()->all();
        unset($data['habits']);

        return $data;
    }

    public function index(Request $request): JsonResponse
    {
        $blocks = $request->user()->scheduleBlocks()
            ->with('habits:id')
            ->orderBy('sort_order')
            ->orderBy('id')
            ->get()
            ->map(fn (ScheduleBlock $block) => $this->serialize($block));

        return response()->json(['schedule_blocks' => $blocks]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate($this->rules(required: true));

        if ($error = $this->checkStart($validated)) {
            return $error;
        }
        $habitIds = $validated['habit_ids'] ?? [];
        if ($error = $this->checkHabits($request, $habitIds)) {
            return $error;
        }

        $block = $request->user()->scheduleBlocks()->create(array_diff_key($validated, ['habit_ids' => null]));
        $block->habits()->sync($habitIds);

        return response()->json(['schedule_block' => $this->serialize($block->fresh()->load('habits:id'))], 201);
    }

    public function update(Request $request, int $scheduleBlock): JsonResponse
    {
        $validated = $request->validate($this->rules(required: false) + [
            'updated_at' => ['required', 'date'],
        ]);

        $block = $this->authorizedBlock($request, $scheduleBlock)->load('habits:id');

        if (Carbon::parse($validated['updated_at'])->ne($block->updated_at)) {
            return response()->json([
                'message' => 'Berubah di tempat lain.',
                'schedule_block' => $this->serialize($block),
            ], 409);
        }

        if ($error = $this->checkStart($validated, $block)) {
            return $error;
        }
        if (array_key_exists('habit_ids', $validated) && ($error = $this->checkHabits($request, $validated['habit_ids']))) {
            return $error;
        }

        $block->update(array_diff_key($validated, ['updated_at' => null, 'habit_ids' => null]));
        if (array_key_exists('habit_ids', $validated)) {
            $block->habits()->sync($validated['habit_ids']);
            $block->touch();
        }

        return response()->json(['schedule_block' => $this->serialize($block->fresh()->load('habits:id'))]);
    }

    /**
     * Android mengirim seluruh jadwalnya sekaligus (tahap 29 langkah 4). Satu arah: web hanya membaca blok, jadi HP
     * satu-satunya yang mengubahnya dan tidak perlu deteksi konflik. Semua blok pengguna ini diganti dalam satu transaksi,
     * jadi mengirim ulang isi yang sama aman (id blok di server berubah, tapi tidak ada yang menyimpannya). Daftar kosong
     * mengosongkan jadwal. Satu blok yang tidak sah menggagalkan seluruh kiriman dan jadwal lama tetap utuh.
     */
    public function replace(Request $request): JsonResponse
    {
        $rules = ['blocks' => ['present', 'array', 'max:'.self::MAX_BLOCKS]];
        foreach ($this->rules(required: true) as $field => $fieldRules) {
            // `distinct` pada dua tanda bintang membandingkan lintas blok, padahal satu habit sah ditautkan ke banyak blok.
            $rules["blocks.*.$field"] = $field === 'habit_ids.*' ? ['integer'] : $fieldRules;
        }
        $validated = $request->validate($rules);
        $blocks = $validated['blocks'];

        foreach ($blocks as $block) {
            if ($message = $this->startError($block)) {
                return response()->json(['message' => $message], 422);
            }
        }
        $habitIds = collect($blocks)->pluck('habit_ids')->flatten()->unique()->values()->all();
        if ($error = $this->checkHabits($request, $habitIds)) {
            return $error;
        }

        $user = $request->user();
        DB::transaction(function () use ($user, $blocks) {
            $user->scheduleBlocks()->delete();
            foreach ($blocks as $block) {
                $created = $user->scheduleBlocks()->create(array_diff_key($block, ['habit_ids' => null]));
                $created->habits()->sync($block['habit_ids'] ?? []);
            }
        });

        $saved = $user->scheduleBlocks()->with('habits:id')->orderBy('sort_order')->orderBy('id')->get();

        return response()->json(['schedule_blocks' => $saved->map(fn (ScheduleBlock $block) => $this->serialize($block))]);
    }

    public function destroy(Request $request, int $scheduleBlock): JsonResponse
    {
        $this->authorizedBlock($request, $scheduleBlock)->delete();

        return response()->json(status: 204);
    }
}
