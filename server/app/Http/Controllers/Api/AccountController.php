<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Rules\StrongPassword;
use App\Services\AccountService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** Akun yang sedang masuk (tahap 31B): data diri, dan ganti kata sandi. Hapus akun menyusul di 31D. */
class AccountController extends Controller
{
    public function __construct(private AccountService $accounts) {}

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

        $data = $request->validate([
            'current_password' => [$user->password !== null ? 'required' : 'nullable', 'string'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
        ]);

        [$status, $wait] = $this->accounts->changePassword(
            $user,
            $data['current_password'] ?? null,
            $data['password'],
            keepTokenId: $request->attributes->get('user_token')->id,
        );

        return match ($status) {
            AccountService::PASSWORD_THROTTLED => response()->json([
                'message' => 'Terlalu banyak percobaan. Coba lagi dalam '.$wait.' detik.',
                'code' => 'too_many_attempts',
            ], 429, ['Retry-After' => $wait]),
            AccountService::PASSWORD_WRONG => response()->json([
                'message' => 'Kata sandi saat ini salah.',
                'code' => 'invalid_credentials',
            ], 422),
            default => response()->json(null, 204),
        };
    }
}
