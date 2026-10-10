<?php

namespace App\Http\Middleware;

use App\Models\UserToken;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Symfony\Component\HttpFoundation\Response;

/**
 * Login dengan token API per-user (didapat lewat POST /api/v1/auth/google setelah Google
 * Sign-In, tahap 25) di header `Authorization: Bearer <token>`. Beda dari AuthenticateToken
 * (token per-HP tanpa akun, tahap 19B) - dipakai untuk endpoint yang butuh identitas user
 * (follow-up kerja, to-do tim).
 */
class AuthenticateUserToken
{
    private const MAX_FAILURES = 10;

    private const FAILURE_WINDOW_SECONDS = 60;

    public function handle(Request $request, Closure $next): Response
    {
        $failureKey = 'user-token-failure:'.$request->ip();

        if (RateLimiter::tooManyAttempts($failureKey, self::MAX_FAILURES)) {
            return response()->json(['message' => 'Terlalu banyak percobaan.'], 429, [
                'Retry-After' => RateLimiter::availableIn($failureKey),
            ]);
        }

        $plain = $request->bearerToken();
        $token = $plain ? UserToken::where('token_hash', UserToken::hash($plain))->first() : null;

        if (! $token) {
            RateLimiter::hit($failureKey, self::FAILURE_WINDOW_SECONDS);

            return response()->json(['message' => 'Token tidak sah.'], 401);
        }

        if (! $token->last_used_at || $token->last_used_at->lt(now()->subMinute())) {
            $token->forceFill(['last_used_at' => now()])->save();
        }

        $request->setUserResolver(fn () => $token->user);

        return $next($request);
    }
}
