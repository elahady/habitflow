<?php

namespace App\Http\Middleware;

use App\Models\ApiToken;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Symfony\Component\HttpFoundation\Response;

/**
 * Login dengan token pribadi di header `Authorization: Bearer <token>`. Percobaan yang salah dibatasi
 * per alamat IP supaya token tidak bisa ditebak dengan mencoba berulang.
 */
class AuthenticateToken
{
    private const MAX_FAILURES = 10;

    private const FAILURE_WINDOW_SECONDS = 60;

    public function handle(Request $request, Closure $next): Response
    {
        $failureKey = 'token-failure:'.$request->ip();

        if (RateLimiter::tooManyAttempts($failureKey, self::MAX_FAILURES)) {
            return response()->json(['message' => 'Terlalu banyak percobaan.'], 429, [
                'Retry-After' => RateLimiter::availableIn($failureKey),
            ]);
        }

        $plain = $request->bearerToken();
        $token = $plain ? ApiToken::where('token_hash', ApiToken::hash($plain))->first() : null;

        if (! $token) {
            RateLimiter::hit($failureKey, self::FAILURE_WINDOW_SECONDS);

            return response()->json(['message' => 'Token tidak sah.'], 401);
        }

        // Cukup perbarui sekali per menit supaya tidak menulis database di setiap permintaan.
        if (! $token->last_used_at || $token->last_used_at->lt(now()->subMinute())) {
            $token->forceFill(['last_used_at' => now()])->save();
        }

        $request->attributes->set('api_token', $token);

        return $next($request);
    }
}
