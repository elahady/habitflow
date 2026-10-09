<?php

namespace App\Http\Controllers;

use App\Models\ApiToken;
use App\Models\Snapshot;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Validator;

/**
 * Menyimpan dan mengembalikan snapshot data HabitFlow. Server tidak menafsirkan isinya; yang diperiksa
 * hanya bahwa isinya JSON dengan `schemaVersion` bilangan bulat dan `data` objek. Isi disimpan terenkripsi
 * (kunci APP_KEY) dan hanya [KEEP] snapshot terakhir per token yang disimpan.
 */
class SnapshotController extends Controller
{
    public const MAX_BYTES = 5 * 1024 * 1024;

    public const KEEP = 14;

    public function store(Request $request): JsonResponse
    {
        /** @var ApiToken $token */
        $token = $request->attributes->get('api_token');
        $body = $request->getContent();

        if (strlen($body) > self::MAX_BYTES) {
            return response()->json(['message' => 'Snapshot terlalu besar (maksimal 5 MB).'], 413);
        }

        $payload = json_decode($body, true);
        if (! is_array($payload)) {
            return response()->json(['message' => 'Isi bukan JSON.', 'errors' => ['body' => ['Bukan JSON.']]], 422);
        }

        $validator = Validator::make($payload, [
            'schemaVersion' => ['required', 'integer', 'min:1'],
            'data' => ['required', 'array'],
            'deviceId' => ['nullable', 'string', 'max:100'],
            'createdAt' => ['nullable', 'integer', 'min:0'],
        ]);
        // `data` harus objek, bukan daftar berindeks angka.
        $validator->after(function ($validator) use ($payload) {
            if (is_array($payload['data'] ?? null) && array_is_list($payload['data'])) {
                $validator->errors()->add('data', 'data harus berupa objek.');
            }
        });
        if ($validator->fails()) {
            return response()->json(['message' => 'Snapshot tidak sah.', 'errors' => $validator->errors()], 422);
        }

        // Hash isi saja (versi skema dan data): createdAt dan deviceId berubah di setiap kirim, jadi kalau ikut
        // dihitung, "isi tidak berubah" tidak akan pernah terdeteksi.
        $sha = hash('sha256', json_encode([$payload['schemaVersion'], $payload['data']]));
        $latest = $token->snapshots()->latest('id')->first();
        if ($latest && $latest->sha256 === $sha) {
            return response()->json($this->summary($latest, unchanged: true));
        }

        $snapshot = $token->snapshots()->create([
            'device_id' => $payload['deviceId'] ?? null,
            'schema_version' => $payload['schemaVersion'],
            'bytes' => strlen($body),
            'sha256' => $sha,
            'path' => '',
            'client_created_at' => $payload['createdAt'] ?? null,
        ]);
        $path = sprintf('snapshots/%d/%s-%d.enc', $token->id, now()->format('Ymd-His'), $snapshot->id);
        Storage::disk('local')->put($path, Crypt::encryptString($body));
        $snapshot->update(['path' => $path]);

        $this->prune($token);

        return response()->json($this->summary($snapshot, unchanged: false));
    }

    public function latest(Request $request): JsonResponse|Response
    {
        /** @var ApiToken $token */
        $token = $request->attributes->get('api_token');
        $snapshot = $token->snapshots()->latest('id')->first();

        if (! $snapshot || ! Storage::disk('local')->exists($snapshot->path)) {
            return response()->json(['message' => 'Belum ada snapshot.'], 404);
        }

        $plain = Crypt::decryptString(Storage::disk('local')->get($snapshot->path));

        return response($plain, 200, [
            'Content-Type' => 'application/json',
            'X-Received-At' => (string) ($snapshot->created_at->getTimestamp() * 1000),
        ]);
    }

    private function summary(Snapshot $snapshot, bool $unchanged): array
    {
        return [
            'id' => $snapshot->id,
            'receivedAt' => $snapshot->created_at->getTimestamp() * 1000,
            'bytes' => (int) $snapshot->bytes,
            'unchanged' => $unchanged,
        ];
    }

    /** Hapus snapshot yang lebih lama dari [KEEP] terakhir, beserta berkasnya. */
    private function prune(ApiToken $token): void
    {
        $old = $token->snapshots()->latest('id')->skip(self::KEEP)->take(PHP_INT_MAX)->get();
        foreach ($old as $snapshot) {
            Storage::disk('local')->delete($snapshot->path);
            $snapshot->delete();
        }
    }
}
