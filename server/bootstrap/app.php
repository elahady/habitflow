<?php

use App\Http\Middleware\AuthenticateUserToken;
use App\Http\Middleware\EnsureSessionNotRevoked;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Laravel\Passport\Http\Middleware\CheckToken;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        apiPrefix: 'api',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->alias([
            'user-token' => AuthenticateUserToken::class,
            'session-not-revoked' => EnsureSessionNotRevoked::class,
            // Token OAuth (MCP) harus membawa cakupan yang disebut, mis. `scopes:mcp:use`.
            'scopes' => CheckToken::class,
        ]);

        // Traffic masuk lewat Cloudflare Tunnel (cloudflared di VM yang sama) - tanpa ini,
        // Laravel kira semua request HTTP biasa dan generate URL http:// bukan https://.
        $middleware->trustProxies(at: '*');
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // Semua jalur API (dan MCP/OAuth, yang dipanggil mesin) selalu dijawab dengan JSON, termasuk saat galat:
        // tanpa ini, token yang salah dijawab redirect ke halaman login, bukan 401.
        $exceptions->shouldRenderJsonWhen(fn ($request) => $request->is('api/*', 'mcp', 'oauth/token', 'oauth/register'));
    })->create();
