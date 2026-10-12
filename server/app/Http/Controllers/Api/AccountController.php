<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Rules\StrongPassword;
use App\Services\AccountDeletionService;
use App\Services\AccountService;
use App\Services\GoogleIdTokenVerifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** Akun yang sedang masuk (tahap 31B): data diri, ganti kata sandi, dan hapus akun (31D). */
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

    /**
     * Hapus akun dan semua datanya, langsung dan permanen (tahap 31D). Konfirmasi ulang wajib:
     * kata sandi untuk akun yang punya, atau id_token Google untuk akun tanpa kata sandi.
     */
    public function destroy(Request $request, GoogleIdTokenVerifier $verifier, AccountDeletionService $deleter): JsonResponse
    {
        $user = $request->user();

        if ($user->password !== null) {
            $data = $request->validate(['password' => ['required', 'string']]);

            [$status, $wait] = $this->accounts->confirmPasswordForDeletion($user, $data['password']);

            if ($status === AccountService::PASSWORD_THROTTLED) {
                return response()->json([
                    'message' => 'Terlalu banyak percobaan. Coba lagi dalam '.$wait.' detik.',
                    'code' => 'too_many_attempts',
                ], 429, ['Retry-After' => $wait]);
            }
            if ($status === AccountService::PASSWORD_WRONG) {
                return response()->json(['message' => 'Kata sandi salah.', 'code' => 'invalid_credentials'], 422);
            }
        } else {
            $data = $request->validate(['id_token' => ['required', 'string']]);
            $claims = $verifier->claims($data['id_token']);

            if ($user->google_sub === null || $claims['sub'] !== $user->google_sub) {
                return response()->json(['message' => 'Akun Google tidak cocok.', 'code' => 'invalid_credentials'], 422);
            }
        }

        $deleter->delete($user, AccountDeletionService::SOURCE_APP);

        return response()->json(null, 204);
    }
}
