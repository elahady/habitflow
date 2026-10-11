<?php

namespace App\Services;

use App\Models\EmailCode;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;

/**
 * Aturan akun email dan kata sandi (tahap 31B), dipakai bersama oleh API Android dan halaman
 * web (31C) supaya batas laju, kode, dan pencabutan sesi tidak berbeda antar jalur.
 */
class AccountService
{
    public const LOGIN_OK = 'ok';

    public const LOGIN_INVALID = 'invalid';

    public const LOGIN_UNVERIFIED = 'unverified';

    public const LOGIN_THROTTLED = 'throttled';

    private const LOGIN_IP_MAX = 5;

    private const LOGIN_EMAIL_MAX = 10;

    public function __construct(private EmailCodeService $codes) {}

    public function normalizeEmail(string $email): string
    {
        return mb_strtolower(trim($email));
    }

    /**
     * Daftar akun email. Mengembalikan null kalau emailnya sudah dipakai akun terverifikasi.
     * Email yang ada tapi belum terverifikasi dipegang pendaftar terbaru: nama dan kata sandi
     * ditimpa lalu kode dikirim ulang (kode lama hangus).
     */
    public function register(string $name, string $email, string $password, string $privacyVersion): ?User
    {
        $email = $this->normalizeEmail($email);
        $user = User::where('email', $email)->first();

        if ($user?->hasVerifiedEmail()) {
            return null;
        }

        $user ??= new User(['email' => $email]);
        $user->fill(['name' => $name, 'password' => $password]);
        $user->forceFill([
            'privacy_consented_at' => now(),
            'privacy_policy_version' => $privacyVersion,
        ])->save();

        $this->sendVerification($user);

        return $user;
    }

    /** Kirim kode verifikasi kalau akunnya ada, belum terverifikasi, dan batas kirim belum tercapai. */
    public function sendVerification(?User $user): void
    {
        if ($user && ! $user->hasVerifiedEmail() && $this->codes->allowSend($user->email, 'verify', 5)) {
            $this->codes->issue($user, EmailCode::PURPOSE_VERIFY);
        }
    }

    /** Lupa kata sandi: kode hanya untuk akun yang emailnya terverifikasi. Diam kalau tidak. */
    public function sendPasswordReset(string $email): void
    {
        $user = User::where('email', $this->normalizeEmail($email))->first();

        if ($user?->hasVerifiedEmail() && $this->codes->allowSend($user->email, 'reset', 3, withCooldown: false)) {
            $this->codes->issue($user, EmailCode::PURPOSE_RESET);
        }
    }

    /**
     * Periksa kode verifikasi email. Mengembalikan [hasil EmailCodeService::*, user atau null];
     * kalau OK, email sudah ditandai terverifikasi dan kodenya hangus.
     */
    public function verifyEmail(string $email, string $code): array
    {
        $user = User::where('email', $this->normalizeEmail($email))->first();

        if (! $user || $user->hasVerifiedEmail()) {
            return [EmailCodeService::INVALID, null];
        }

        $result = $this->codes->verify($user, EmailCode::PURPOSE_VERIFY, $code);
        if ($result !== EmailCodeService::OK) {
            return [$result, null];
        }

        $user->forceFill(['email_verified_at' => now()])->save();

        return [EmailCodeService::OK, $user];
    }

    /**
     * Atur ulang kata sandi dengan kode. Kalau OK: kata sandi berganti, semua token API dan
     * sesi web dicabut. Pemanggil sudah memvalidasi kata sandi baru (sebelum kode diperiksa,
     * supaya sandi lemah tidak menghanguskan kode).
     */
    public function resetPassword(string $email, string $code, string $password): string
    {
        $user = User::where('email', $this->normalizeEmail($email))->first();

        if (! $user?->hasVerifiedEmail()) {
            return EmailCodeService::INVALID;
        }

        $result = $this->codes->verify($user, EmailCode::PURPOSE_RESET, $code);
        if ($result !== EmailCodeService::OK) {
            return $result;
        }

        $user->forceFill(['password' => $password])->save();
        $user->revokeTokens();
        $user->revokeWebSessions();

        return EmailCodeService::OK;
    }

    /**
     * Batas percobaan kode per email (10 per 15 menit) dan per IP (30 per 15 menit) supaya
     * tebak-tebak 6 angka tidak praktis. Mengembalikan detik tunggu kalau terkunci, atau null.
     * Setiap panggilan yang lolos dihitung sebagai satu percobaan.
     */
    public function codeAttemptWait(string $scope, string $ip, string $email): ?int
    {
        $emailKey = "code-try:$scope:email:$email";
        $ipKey = "code-try:$scope:ip:$ip";

        if (RateLimiter::tooManyAttempts($emailKey, 10) || RateLimiter::tooManyAttempts($ipKey, 30)) {
            return max(RateLimiter::availableIn($emailKey), RateLimiter::availableIn($ipKey));
        }

        RateLimiter::hit($emailKey, 900);
        RateLimiter::hit($ipKey, 900);

        return null;
    }

    /**
     * Cek email dan kata sandi dengan batas gagal (5 per menit per IP, 10 per 15 menit per email).
     * Mengembalikan [status LOGIN_*, user atau detik tunggu atau null]. Tidak masuk (tidak membuat
     * token atau sesi): itu urusan pemanggil.
     */
    public function checkLogin(string $email, string $password, string $ip): array
    {
        $email = $this->normalizeEmail($email);
        $ipKey = 'login-fail:ip:'.$ip;
        $emailKey = 'login-fail:email:'.$email;

        if (RateLimiter::tooManyAttempts($ipKey, self::LOGIN_IP_MAX) || RateLimiter::tooManyAttempts($emailKey, self::LOGIN_EMAIL_MAX)) {
            return [self::LOGIN_THROTTLED, max(RateLimiter::availableIn($ipKey), RateLimiter::availableIn($emailKey))];
        }

        $user = User::where('email', $email)->first();

        // Email tak dikenal atau akun tanpa kata sandi (hanya Google): tetap hitung hash supaya
        // waktu jawab tidak membocorkan apakah emailnya terdaftar.
        $passwordOk = $user?->password
            ? Hash::check($password, $user->password)
            : Hash::check($password, Hash::make('pemerata-waktu')) && false;

        if (! $passwordOk) {
            RateLimiter::hit($ipKey, 60);
            RateLimiter::hit($emailKey, 900);

            return [self::LOGIN_INVALID, null];
        }

        if (! $user->hasVerifiedEmail()) {
            return [self::LOGIN_UNVERIFIED, $user];
        }

        RateLimiter::clear($emailKey);

        return [self::LOGIN_OK, $user];
    }

    public const PASSWORD_OK = 'ok';

    public const PASSWORD_WRONG = 'wrong';

    public const PASSWORD_THROTTLED = 'throttled';

    /**
     * Ganti kata sandi akun yang sedang masuk. Akun Google tanpa sandi ($user->password kosong)
     * tidak perlu kata sandi lama. Salah 5 kali dikunci 15 menit. Kalau OK, token API dicabut
     * kecuali $keepTokenId, dan semua sesi web dicabut (pemanggil web mencap ulang sesinya sendiri).
     * Mengembalikan [status PASSWORD_*, detik tunggu atau null].
     */
    public function changePassword(User $user, ?string $current, string $new, ?int $keepTokenId = null): array
    {
        if ($user->password !== null) {
            $key = 'password-change-fail:'.$user->id;
            if (RateLimiter::tooManyAttempts($key, 5)) {
                return [self::PASSWORD_THROTTLED, RateLimiter::availableIn($key)];
            }

            if ($current === null || ! Hash::check($current, $user->password)) {
                RateLimiter::hit($key, 900);

                return [self::PASSWORD_WRONG, null];
            }
            RateLimiter::clear($key);
        }

        $user->forceFill(['password' => $new])->save();
        $user->revokeTokens(exceptTokenId: $keepTokenId);
        $user->revokeWebSessions();

        return [self::PASSWORD_OK, null];
    }
}
