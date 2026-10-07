<?php

namespace App\Providers;

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
        // 60 permintaan per menit per token (atau per alamat IP kalau belum ada token yang sah).
        RateLimiter::for('habitflow', function (Request $request) {
            $token = $request->attributes->get('api_token');

            return Limit::perMinute(60)->by($token ? 'token:'.$token->id : 'ip:'.$request->ip());
        });
    }
}
