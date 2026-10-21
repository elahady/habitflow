<?php

use App\Mcp\Servers\HabitflowServer;
use Laravel\Mcp\Facades\Mcp;

// MCP Habitflow. Login lewat OAuth (Passport): Claude mendaftarkan diri di /oauth/register, pengguna menyetujui di
// /oauth/authorize (lihat resources/views/mcp/authorize.blade.php). Token OAuth hanya berlaku di sini, bukan di /api/v1.
Mcp::oauthRoutes();

Mcp::web('/mcp', HabitflowServer::class)
    ->middleware(['auth:api', 'scopes:mcp:use', 'throttle:habitflow']);
