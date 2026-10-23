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
use Laravel\Mcp\Server\Tools\Annotations\IsIdempotent;

/** Mengubah hanya field yang dikirim. Sengaja tidak ada hapus, supaya Claude tidak bisa menghilangkan follow-up. */
#[Description('Ubah follow-up yang sudah ada berdasarkan id (dari list-follow-ups). Hanya field yang dikirim yang berubah. Menandai selesai: status DONE; membuka lagi: status ACTIVE. Tidak bisa menghapus.')]
#[IsIdempotent]
class UpdateFollowUp extends Tool
{
    public function handle(Request $request): Response
    {
        $validated = $request->validate([
            'id' => ['required', 'integer'],
            'title' => ['sometimes', 'required', 'string', 'max:255'],
            'status' => ['sometimes', Rule::in(FollowUp::STATUSES)],
            'date' => ['sometimes', 'nullable', 'date_format:Y-m-d'],
            'picked_date' => ['sometimes', 'nullable', 'date_format:Y-m-d'],
            'done_on' => ['sometimes', 'nullable', 'date_format:Y-m-d'],
            'person' => ['sometimes', 'nullable', 'string', 'max:255'],
            'note' => ['sometimes', 'nullable', 'string', 'max:5000'],
        ]);

        $user = $request->user();
        $item = $user->followUps()->find($validated['id']);
        if (! $item) {
            return Response::error('Follow-up dengan id '.$validated['id'].' tidak ditemukan.');
        }

        $changes = array_intersect_key($validated, array_flip(['title', 'status', 'date', 'picked_date', 'person', 'note']));

        // Sama dengan Livewire `FollowUps::apply`: DONE mengisi `done_at` (yang lama dipertahankan), selain DONE mengosongkannya.
        $status = $validated['status'] ?? $item->status;
        if ($status === 'DONE') {
            $changes['done_at'] = FollowUpPayload::doneAt($item, $validated['done_on'] ?? null, UserDay::timezone($user));
        } elseif (array_key_exists('status', $validated) || $item->done_at) {
            $changes['done_at'] = null;
        }

        $item->update($changes);

        return Response::json(['updated' => true, 'follow_up' => FollowUpPayload::of($item->fresh())]);
    }

    /** @return array<string, Type> */
    public function schema(JsonSchema $schema): array
    {
        return [
            'id' => $schema->integer()->description('Id follow-up.')->required(),
            'title' => $schema->string()->description('Judul baru.'),
            'status' => $schema->string()->enum(FollowUp::STATUSES)->description('Status baru: INBOX, ACTIVE, WAITING, atau DONE.'),
            'date' => $schema->string()->description('Tanggal jatuh tempo YYYY-MM-DD; kirim null untuk mengosongkan.'),
            'picked_date' => $schema->string()->description('Tanggal dikerjakan YYYY-MM-DD; kirim null untuk mengosongkan.'),
            'done_on' => $schema->string()->description('Untuk status DONE: tanggal selesai YYYY-MM-DD (zona pengguna).'),
            'person' => $schema->string()->description('Orang terkait; kirim null untuk mengosongkan.'),
            'note' => $schema->string()->description('Catatan; kirim null untuk mengosongkan.'),
        ];
    }
}
