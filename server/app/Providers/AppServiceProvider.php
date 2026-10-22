<?php

namespace App\Providers;

use App\Models\UserToken;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\ServiceProvider;
use Laravel\Mcp\Server\Registrar;
use Laravel\Passport\Passport;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // Hanya alur authorization code + PKCE untuk MCP; alur kode perangkat (/oauth/device*) tidak dipakai.
        Passport::$deviceCodeGrantEnabled = false;
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // OAuth untuk MCP (Passport): layar persetujuan sendiri, token akses pendek, refresh token 30 hari.
        Passport::authorizationView('mcp.authorize');
        Passport::tokensExpireIn(now()->addHour());
        Passport::refreshTokensExpireIn(now()->addDays(30));
        // Klien yang tidak mengirim parameter `scope` tetap mendapat token yang berlaku di /mcp.
        Passport::setDefaultScope(Registrar::OAUTH_SCOPE);

        // Rute OAuth didaftarkan oleh provider Passport dan MCP, jadi middleware tambahan ditempel setelah semua provider
        // selesai boot. Sesi yang sudah diputus ("Keluar dari semua perangkat") tidak boleh menyetujui klien MCP baru,
        // dan pendaftaran klien (tanpa login, memang begitu alurnya) dibatasi supaya tabel oauth_clients tidak dibanjiri.
        $this->app->booted(function () {
            $extra = [
                'passport.authorizations.authorize' => 'session-not-revoked',
                'passport.authorizations.approve' => 'session-not-revoked',
                'passport.authorizations.deny' => 'session-not-revoked',
                'mcp.oauth.register' => 'throttle:10,1',
            ];

            foreach (Route::getRoutes()->getRoutes() as $route) {
                if (isset($extra[$route->getName()])) {
                    $route->middleware($extra[$route->getName()]);
                }
            }
        });

        // 60 permintaan per menit per token (atau per alamat IP kalau tidak ada token). Kuncinya diambil dari header,
        // bukan dari atribut yang diisi middleware `user-token`: Laravel menaruh throttle lebih awal dari middleware
        // itu, jadi saat limiter dihitung atributnya belum ada. Token palsu tetap dibatasi oleh `user-token` per IP.
        RateLimiter::for('habitflow', function (Request $request) {
            $plain = $request->bearerToken();

            return Limit::perMinute(60)->by($plain ? 'token:'.UserToken::hash($plain) : 'ip:'.$request->ip());
        });
    }
}
