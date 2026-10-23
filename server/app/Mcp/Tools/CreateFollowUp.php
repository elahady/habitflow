<?php

namespace App\Mcp\Tools;

use App\Mcp\Support\FollowUpPayload;
use App\Models\FollowUp;
use App\Support\UserDay;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\JsonSchema\Types\Type;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Tool;

/**
 * Menulis log aktivitas dan rencana besok sebagai follow-up (pengganti jurnal kerja Notion):
 * pekerjaan yang sudah dikerjakan = `DONE` (+ `done_on`), rencana besok = `ACTIVE` dengan `picked_date` besok,
 * sama dengan "Lanjut besok" di EOD. Follow-up yang sama (judul sama) tidak dibuat dua kali, jadi skill aman dijalankan ulang.
 */
#[Description('Buat follow-up baru di Habitflow. Log aktivitas yang sudah dikerjakan: status DONE (opsional `done_on` untuk tanggal selesainya). Rencana besok: status ACTIVE dengan `picked_date` besok. Kalau follow-up berjudul sama sudah ada (belum selesai, atau selesai di tanggal yang sama), tidak dibuat lagi dan data yang ada dikembalikan.')]
class CreateFollowUp extends Tool
{
    public function handle(Request $request): Response
    {
        $validated = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'status' => ['nullable', Rule::in(FollowUp::STATUSES)],
            'date' => ['nullable', 'date_format:Y-m-d'],
            'picked_date' => ['nullable', 'date_format:Y-m-d'],
            'done_on' => ['nullable', 'date_format:Y-m-d'],
            'person' => ['nullable', 'string', 'max:255'],
            'note' => ['nullable', 'string', 'max:5000'],
        ]);

        $user = $request->user();
        $status = $validated['status'] ?? 'ACTIVE';
        $timezone = UserDay::timezone($user);
        $doneAt = $status === 'DONE' ? FollowUpPayload::doneAt(null, $validated['done_on'] ?? null, $timezone) : null;

        $existing = $this->duplicate($user->followUps(), $validated['title'], $status, $doneAt, $timezone);
        if ($existing) {
            return Response::json(['created' => false, 'reason' => 'Follow-up berjudul sama sudah ada.', 'follow_up' => FollowUpPayload::of($existing)]);
        }

        $item = $user->followUps()->create([
            'title' => $validated['title'],
            'status' => $status,
            'date' => $validated['date'] ?? null,
            'picked_date' => $validated['picked_date'] ?? null,
            'person' => $validated['person'] ?? null,
            'note' => $validated['note'] ?? null,
            'done_at' => $doneAt,
        ])->fresh();

        return Response::json(['created' => true, 'follow_up' => FollowUpPayload::of($item)]);
    }

    /** Judul sama tanpa membedakan huruf besar-kecil: yang selesai dicocokkan per tanggal selesai, yang belum selesai dengan yang belum selesai. */
    private function duplicate(HasMany $relation, string $title, string $status, ?Carbon $doneAt, string $timezone): ?FollowUp
    {
        $candidates = $relation->whereRaw('lower(title) = ?', [mb_strtolower($title)])->get();

        return $candidates->first(function (FollowUp $f) use ($status, $doneAt, $timezone) {
            if ($status !== 'DONE') {
                return $f->status !== 'DONE';
            }

            return $f->status === 'DONE' && $f->done_at
                && $f->done_at->copy()->setTimezone($timezone)->toDateString() === $doneAt->copy()->setTimezone($timezone)->toDateString();
        });
    }

    /** @return array<string, Type> */
    public function schema(JsonSchema $schema): array
    {
        return [
            'title' => $schema->string()->description('Judul singkat, maksimal 255 karakter. Contoh: "[DB/Web] Skrip grant menu Master Customer dijalankan di MGTsby".')->required(),
            'status' => $schema->string()->enum(FollowUp::STATUSES)->description('Bawaan ACTIVE. DONE untuk yang sudah dikerjakan, INBOX untuk yang belum dipilah.'),
            'date' => $schema->string()->description('Tanggal jatuh tempo YYYY-MM-DD, opsional.'),
            'picked_date' => $schema->string()->description('Tanggal dikerjakan YYYY-MM-DD. Isi tanggal besok untuk "Rencana besok".'),
            'done_on' => $schema->string()->description('Hanya untuk status DONE: tanggal selesai YYYY-MM-DD (zona pengguna). Kosong = sekarang.'),
            'person' => $schema->string()->description('Orang terkait, opsional.'),
            'note' => $schema->string()->description('Catatan teks polos, maksimal 5.000 karakter.'),
        ];
    }
}
