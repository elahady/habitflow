<?php

namespace App\Mcp\Servers;

use App\Mcp\Tools\CreateFollowUp;
use App\Mcp\Tools\GetDailyBrief;
use App\Mcp\Tools\ListFollowUps;
use App\Mcp\Tools\SaveDailyBrief;
use App\Mcp\Tools\UpdateFollowUp;
use Laravel\Mcp\Server;
use Laravel\Mcp\Server\Attributes\Instructions;
use Laravel\Mcp\Server\Attributes\Name;
use Laravel\Mcp\Server\Attributes\Version;

/**
 * MCP Habitflow: Claude membaca dan menulis data akun pemilik token OAuth. Ringkasan harian (pengganti
 * `POST /api/v1/daily-briefs` untuk skill habitflow-morning-routine) dan follow-up (log aktivitas + rencana besok,
 * tahap 35); tool lain menyusul satu per satu.
 */
#[Name('Habitflow')]
#[Version('1.0.0')]
#[Instructions('Habitflow adalah asisten harian pribadi. Tool di sini membaca dan menulis data akun pengguna yang sedang login, bukan data orang lain.')]
class HabitflowServer extends Server
{
    protected array $tools = [
        SaveDailyBrief::class,
        GetDailyBrief::class,
        ListFollowUps::class,
        CreateFollowUp::class,
        UpdateFollowUp::class,
    ];
}
