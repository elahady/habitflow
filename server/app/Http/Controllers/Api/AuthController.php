<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\EmailCode;
use App\Models\User;
use App\Models\UserToken;
use App\Rules\StrongPassword;
use App\Services\EmailCodeService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;

/**
 * Akun email dan kata sandi untuk app Android (tahap 31B, kontrak di docs/concept.md 31A).
 * Pesan galat bahasa Indonesia dengan kode di field `code`. Jawaban tidak membedakan email
 * terdaftar atau tidak, kecuali register (yang memang harus bilang email sudah dipakai).
 */
class AuthController extends Controller
{
    private const LOGIN_IP_MAX = 5;

    private const LOGIN_EMAIL_MAX = 10;

    public function __construct(private EmailCodeService $codes) {}

    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'string', 'email', 'max:255'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
            'privacy_policy_version' => ['required', 'string', 'in:'.config('habitflow.privacy_policy_version')],
        ]);
        $email = $this->normalizeEmail($data['email']);

        $user = User::where('email', $email)->first();

        if ($user?->hasVerifiedEmail()) {
            return $this->error('email_taken', 'Email ini sudah terdaftar. Silakan masuk.', 422);
        }

        // Email ada tapi belum terverifikasi: pendaftar terbaru yang memegang email itu berhak,
        // jadi nama dan kata sandi diperbarui lalu kode dikirim ulang (kode lama hangus).
        $user ??= new User(['email' => $email]);
        $user->fill(['name' => $data['name'], 'password' => $data['password']]);
        $user->forceFill([
            'privacy_consented_at' => now(),
            'privacy_policy_version' => $data['privacy_policy_version'],
        ])->save();

        if ($this->codes->allowSend($email, 'verify', 5)) {
            $this->codes->issue($user, EmailCode::PURPOSE_VERIFY);
        }

        return response()->json(['status' => 'verification_required', 'email' => $email], 201);
    }

    public function verifyEmail(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'code' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:100'],
        ]);
        $email = $this->normalizeEmail($data['email']);

        if ($tooMany = $this->tooManyCodeAttempts('verify', $request, $email)) {
            return $tooMany;
        }

        $user = User::where('email', $email)->first();
        $result = $user && ! $user->hasVerifiedEmail()
            ? $this->codes->verify($user, EmailCode::PURPOSE_VERIFY, $data['code'])
            : EmailCodeService::INVALID;

        if ($result !== EmailCodeService::OK) {
            return $this->codeError($result);
        }

        $user->forceFill(['email_verified_at' => now()])->save();

        return $this->issueSession($user, $data['device_name']);
    }

    public function resendVerification(Request $request): JsonResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email']]);
        $email = $this->normalizeEmail($data['email']);

        $user = User::where('email', $email)->first();
        if ($user && ! $user->hasVerifiedEmail() && $this->codes->allowSend($email, 'verify', 5)) {
            $this->codes->issue($user, EmailCode::PURPOSE_VERIFY);
        }

        return response()->json(['status' => 'accepted'], 202);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'password' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:100'],
        ]);
        $email = $this->normalizeEmail($data['email']);

        $ipKey = 'login-fail:ip:'.$request->ip();
        $emailKey = 'login-fail:email:'.$email;
        if (RateLimiter::tooManyAttempts($ipKey, self::LOGIN_IP_MAX) || RateLimiter::tooManyAttempts($emailKey, self::LOGIN_EMAIL_MAX)) {
            return $this->tooMany(max(RateLimiter::availableIn($ipKey), RateLimiter::availableIn($emailKey)));
        }

        $user = User::where('email', $email)->first();

        // Email tak dikenal atau akun tanpa kata sandi (hanya Google): tetap hitung hash supaya
        // waktu jawab tidak membocorkan apakah emailnya terdaftar.
        $passwordOk = $user?->password
            ? Hash::check($data['password'], $user->password)
            : $this->burnHashTime($data['password']);

        if (! $passwordOk) {
            RateLimiter::hit($ipKey, 60);
            RateLimiter::hit($emailKey, 900);

            return $this->error('invalid_credentials', 'Email atau kata sandi salah.', 422);
        }

        if (! $user->hasVerifiedEmail()) {
            return $this->error('email_not_verified', 'Email belum diverifikasi.', 403);
        }

        RateLimiter::clear($emailKey);

        return $this->issueSession($user, $data['device_name']);
    }

    public function forgotPassword(Request $request): JsonResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email']]);
        $email = $this->normalizeEmail($data['email']);

        $user = User::where('email', $email)->first();
        if ($user?->hasVerifiedEmail() && $this->codes->allowSend($email, 'reset', 3, withCooldown: false)) {
            $this->codes->issue($user, EmailCode::PURPOSE_RESET);
        }

        return response()->json(['status' => 'accepted'], 202);
    }

    public function resetPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'code' => ['required', 'string'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
        ]);
        $email = $this->normalizeEmail($data['email']);

        if ($tooMany = $this->tooManyCodeAttempts('reset', $request, $email)) {
            return $tooMany;
        }

        $user = User::where('email', $email)->first();
        $result = $user?->hasVerifiedEmail()
            ? $this->codes->verify($user, EmailCode::PURPOSE_RESET, $data['code'])
            : EmailCodeService::INVALID;

        if ($result !== EmailCodeService::OK) {
            return $this->codeError($result);
        }

        $user->forceFill(['password' => $data['password']])->save();
        $user->revokeTokens();
        $user->rotateRememberToken();

        return response()->json(null, 204);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->attributes->get('user_token')->delete();

        return response()->json(null, 204);
    }

    public function logoutAll(Request $request): JsonResponse
    {
        $request->user()->revokeTokens();
        $request->user()->rotateRememberToken();

        return response()->json(null, 204);
    }

    private function issueSession(User $user, string $deviceName): JsonResponse
    {
        [, $plain] = UserToken::issue($user, $deviceName);

        return response()->json(['token' => $plain, 'account' => $user->toAccount()]);
    }

    /** Selalu false; hanya menghabiskan waktu hash yang sama dengan pemeriksaan kata sandi sungguhan. */
    private function burnHashTime(string $password): bool
    {
        Hash::check($password, Hash::make('pemerata-waktu'));

        return false;
    }

    /** Batas percobaan kode per email dan per IP supaya tebak-tebak 6 angka tidak praktis. */
    private function tooManyCodeAttempts(string $scope, Request $request, string $email): ?JsonResponse
    {
        $emailKey = "code-try:$scope:email:$email";
        $ipKey = "code-try:$scope:ip:".$request->ip();

        if (RateLimiter::tooManyAttempts($emailKey, 10) || RateLimiter::tooManyAttempts($ipKey, 30)) {
            return $this->tooMany(max(RateLimiter::availableIn($emailKey), RateLimiter::availableIn($ipKey)));
        }

        RateLimiter::hit($emailKey, 900);
        RateLimiter::hit($ipKey, 900);

        return null;
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

    private function normalizeEmail(string $email): string
    {
        return mb_strtolower(trim($email));
    }
}
