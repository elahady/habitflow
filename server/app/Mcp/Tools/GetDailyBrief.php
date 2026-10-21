<?php

namespace App\Mcp\Tools;

use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\JsonSchema\Types\Type;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Tool;
use Laravel\Mcp\Server\Tools\Annotations\IsReadOnly;

#[Description('Baca ringkasan harian yang tersimpan di Habitflow untuk satu tanggal (berguna untuk memeriksa apa yang sudah dikirim hari ini).')]
#[IsReadOnly]
class GetDailyBrief extends Tool
{
    public function handle(Request $request): Response
    {
        $validated = $request->validate(['date' => ['required', 'date_format:Y-m-d']]);

        $brief = $request->user()->dailyBriefs()->where('date', $validated['date'])->first();

        if (! $brief) {
            return Response::text('Belum ada ringkasan untuk tanggal '.$validated['date'].'.');
        }

        return Response::text((string) ($brief->email_digest ?? '(ringkasan email kosong)'));
    }

    /** @return array<string, Type> */
    public function schema(JsonSchema $schema): array
    {
        return [
            'date' => $schema->string()->description('Tanggal, format YYYY-MM-DD.')->required(),
        ];
    }
}
