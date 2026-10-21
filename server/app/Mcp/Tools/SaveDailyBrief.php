<?php

namespace App\Mcp\Tools;

use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\JsonSchema\Types\Type;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Tool;
use Laravel\Mcp\Server\Tools\Annotations\IsIdempotent;

/**
 * Setara `POST /api/v1/daily-briefs` (tahap 32): upsert per tanggal, aman dipanggil ulang di hari yang sama.
 * Sengaja hanya `email_digest`; `meeting_note` tidak bisa ditulis dari sini, jadi isinya tidak tertimpa.
 */
#[Description('Simpan ringkasan email hari ini ke kartu "Ringkasan dari Claude" di Habitflow. Memanggil ulang untuk tanggal yang sama menimpa ringkasan sebelumnya. Teks polos tanpa markdown, jangan menyertakan kode verifikasi, kata sandi, atau tautan reset.')]
#[IsIdempotent]
class SaveDailyBrief extends Tool
{
    public function handle(Request $request): Response
    {
        $validated = $request->validate([
            'date' => ['required', 'date_format:Y-m-d'],
            'email_digest' => ['required', 'string', 'max:20000'],
        ]);

        $brief = $request->user()->dailyBriefs()->updateOrCreate(
            ['date' => $validated['date']],
            ['email_digest' => $validated['email_digest']],
        );

        return Response::text(
            ($brief->wasRecentlyCreated ? 'Ringkasan dibuat' : 'Ringkasan ditimpa').' untuk tanggal '.$validated['date'].'.'
        );
    }

    /** @return array<string, Type> */
    public function schema(JsonSchema $schema): array
    {
        return [
            'date' => $schema->string()->description('Tanggal ringkasan, format YYYY-MM-DD (zona Asia/Jakarta).')->required(),
            'email_digest' => $schema->string()->description('Teks ringkasan email belum dibaca, maksimal 20.000 karakter.')->required(),
        ];
    }
}
