<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\User;
use App\Models\UserToken;
use App\Rules\StrongPassword;
use App\Services\AccountService;
use App\Services\EmailCodeService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Akun email dan kata sandi untuk app Android (tahap 31B, kontrak di docs/concept.md 31A).
 * Pesan galat bahasa Indonesia dengan kode di field `code`. Jawaban tidak membedakan email
 * terdaftar atau tidak, kecuali register (yang memang harus bilang email sudah dipakai).
 * Aturannya ada di AccountService, dipakai bersama halaman web.
 */
class AuthController extends Controller
{
    public function __construct(private AccountService $accounts) {}

    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'string', 'email', 'max:255'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
            'privacy_policy_version' => ['required', 'string', 'in:'.config('habitflow.privacy_policy_version')],
        ]);

        $user = $this->accounts->register($data['name'], $data['email'], $data['password'], $data['privacy_policy_version']);

        if (! $user) {
            return $this->error('email_taken', 'Email ini sudah terdaftar. Silakan masuk.', 422);
        }

        return response()->json(['status' => 'verification_required', 'email' => $user->email], 201);
    }

    public function verifyEmail(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'code' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:100'],
        ]);
        $email = $this->accounts->normalizeEmail($data['email']);

        if ($wait = $this->accounts->codeAttemptWait('verify', $request->ip(), $email)) {
            return $this->tooMany($wait);
        }

        [$result, $user] = $this->accounts->verifyEmail($email, $data['code']);

        if ($result !== EmailCodeService::OK) {
            return $this->codeError($result);
        }

        return $this->issueSession($user, $data['device_name']);
    }

    public function resendVerification(Request $request): JsonResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email']]);

        $this->accounts->sendVerification(User::where('email', $this->accounts->normalizeEmail($data['email']))->first());

        return response()->json(['status' => 'accepted'], 202);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'password' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:100'],
        ]);

        [$status, $detail] = $this->accounts->checkLogin($data['email'], $data['password'], $request->ip());

        return match ($status) {
            AccountService::LOGIN_THROTTLED => $this->tooMany($detail),
            AccountService::LOGIN_INVALID => $this->error('invalid_credentials', 'Email atau kata sandi salah.', 422),
            AccountService::LOGIN_UNVERIFIED => $this->error('email_not_verified', 'Email belum diverifikasi.', 403),
            default => $this->issueSession($detail, $data['device_name']),
        };
    }

    public function forgotPassword(Request $request): JsonResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email']]);

        $this->accounts->sendPasswordReset($data['email']);

        return response()->json(['status' => 'accepted'], 202);
    }

    public function resetPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'code' => ['required', 'string'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
        ]);
        $email = $this->accounts->normalizeEmail($data['email']);

        if ($wait = $this->accounts->codeAttemptWait('reset', $request->ip(), $email)) {
            return $this->tooMany($wait);
        }

        $result = $this->accounts->resetPassword($email, $data['code'], $data['password']);

        return $result === EmailCodeService::OK
            ? response()->json(null, 204)
            : $this->codeError($result);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->attributes->get('user_token')->delete();

        return response()->json(null, 204);
    }

    public function logoutAll(Request $request): JsonResponse
    {
        $request->user()->revokeTokens();
        $request->user()->revokeWebSessions();

        return response()->json(null, 204);
    }

    private function issueSession(User $user, string $deviceName): JsonResponse
    {
        [, $plain] = UserToken::issue($user, $deviceName);

        return response()->json(['token' => $plain, 'account' => $user->toAccount()]);
    }

    private function codeError(string $code): JsonResponse
    {
        return $this->error($code, $code === EmailCodeService::EXPIRED
            ? 'Kode sudah kedaluwarsa. Minta kode baru.'
            : 'Kode salah.', 422);
    }

    private function tooMany(int $retryAfter): JsonResponse
    {
        return response()->json([
            'message' => 'Terlalu banyak percobaan. Coba lagi dalam '.$retryAfter.' detik.',
            'code' => 'too_many_attempts',
        ], 429, ['Retry-After' => $retryAfter]);
    }

    private function error(string $code, string $message, int $status): JsonResponse
    {
        return response()->json(['message' => $message, 'code' => $code], $status);
    }
}
