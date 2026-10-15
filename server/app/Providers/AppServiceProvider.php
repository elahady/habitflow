<?php

namespace App\Providers;

use App\Models\UserToken;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        //
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // 60 permintaan per menit per token (atau per alamat IP kalau tidak ada token). Kuncinya diambil dari header,
        // bukan dari atribut yang diisi middleware `user-token`: Laravel menaruh throttle lebih awal dari middleware
        // itu, jadi saat limiter dihitung atributnya belum ada. Token palsu tetap dibatasi oleh `user-token` per IP.
        RateLimiter::for('habitflow', function (Request $request) {
            $plain = $request->bearerToken();

            return Limit::perMinute(60)->by($plain ? 'token:'.UserToken::hash($plain) : 'ip:'.$request->ip());
        });
    }
}
