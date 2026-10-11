<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Rules\StrongPassword;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;

/** Akun yang sedang masuk (tahap 31B): data diri, dan ganti kata sandi. Hapus akun menyusul di 31D. */
class AccountController extends Controller
{
    private const PASSWORD_MAX_FAILURES = 5;

    public function show(Request $request): JsonResponse
    {
        $user = $request->user();

        return response()->json([
            'account' => $user->toAccount(),
            'has_password' => $user->password !== null,
            'google_linked' => $user->google_sub !== null,
            'email_verified' => $user->hasVerifiedEmail(),
        ]);
    }

    public function updatePassword(Request $request): JsonResponse
    {
        $user = $request->user();
        $hasPassword = $user->password !== null;

        $data = $request->validate([
            'current_password' => [$hasPassword ? 'required' : 'nullable', 'string'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
        ]);

        if ($hasPassword) {
            $key = 'password-change-fail:'.$user->id;
            if (RateLimiter::tooManyAttempts($key, self::PASSWORD_MAX_FAILURES)) {
                return response()->json([
                    'message' => 'Terlalu banyak percobaan. Coba lagi dalam '.RateLimiter::availableIn($key).' detik.',
                    'code' => 'too_many_attempts',
                ], 429, ['Retry-After' => RateLimiter::availableIn($key)]);
            }

            if (! Hash::check($data['current_password'], $user->password)) {
                RateLimiter::hit($key, 900);

                return response()->json([
                    'message' => 'Kata sandi saat ini salah.',
                    'code' => 'invalid_credentials',
                ], 422);
            }
            RateLimiter::clear($key);
        }

        $user->forceFill(['password' => $data['password']])->save();
        $user->revokeTokens(exceptTokenId: $request->attributes->get('user_token')->id);
        $user->rotateRememberToken();

        return response()->json(null, 204);
    }
}
