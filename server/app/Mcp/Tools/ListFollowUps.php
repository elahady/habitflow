<?php

namespace App\Mcp\Tools;

use App\Mcp\Support\FollowUpPayload;
use App\Models\FollowUp;
use App\Support\UserDay;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\JsonSchema\Types\Type;
use Illuminate\Validation\Rule;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Tool;
use Laravel\Mcp\Server\Tools\Annotations\IsReadOnly;

#[Description('Daftar follow-up kerja di Habitflow (id, judul, status, tanggal, catatan). Panggil ini untuk mencari id yang mau diubah dan untuk memeriksa yang sudah tercatat. Tanpa filter: semua yang belum selesai. `done_on` mengambil yang selesai pada satu tanggal.')]
#[IsReadOnly]
class ListFollowUps extends Tool
{
    public function handle(Request $request): Response
    {
        $validated = $request->validate([
            'status' => ['nullable', Rule::in(FollowUp::STATUSES)],
            'done_on' => ['nullable', 'date_format:Y-m-d'],
            'limit' => ['nullable', 'integer', 'min:1', 'max:200'],
        ]);

        $user = $request->user();
        $query = $user->followUps()->orderBy('id');

        if (! empty($validated['done_on'])) {
            // Dikelompokkan per tanggal di zona pengguna, sama dengan rekap di web (`WorkRecap::doneDate`).
            $timezone = UserDay::timezone($user);
            $items = $query->where('status', 'DONE')->whereNotNull('done_at')->get()
                ->filter(fn (FollowUp $f) => $f->done_at->copy()->setTimezone($timezone)->toDateString() === $validated['done_on'])
                ->values();
        } elseif (! empty($validated['status'])) {
            $items = $query->where('status', $validated['status'])->get();
        } else {
            $items = $query->where('status', '!=', 'DONE')->get();
        }

        $items = $items->take($validated['limit'] ?? 100)->map(fn (FollowUp $f) => FollowUpPayload::of($f))->all();

        return Response::json(['count' => count($items), 'follow_ups' => $items]);
    }

    /** @return array<string, Type> */
    public function schema(JsonSchema $schema): array
    {
        return [
            'status' => $schema->string()->enum(FollowUp::STATUSES)->description('Filter status: INBOX, ACTIVE, WAITING, atau DONE. Kosong = semua yang belum selesai.'),
            'done_on' => $schema->string()->description('Tanggal YYYY-MM-DD (zona pengguna): hanya yang selesai pada tanggal itu. Mengabaikan `status`.'),
            'limit' => $schema->integer()->description('Maksimal baris, bawaan 100, paling banyak 200.'),
        ];
    }
}
