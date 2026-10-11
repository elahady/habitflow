<?php

namespace App\Services;

use App\Models\User;

/**
 * Aturan penautan Google (tahap 31B), dipakai bersama oleh API Android dan web supaya tidak ada
 * jalur yang lolos dari celah pengambilalihan akun:
 *
 * - google_sub sudah dikenal: itu akunnya.
 * - Ada akun dengan email yang sama dan **sudah terverifikasi**: google_sub ditautkan ke akun itu.
 * - Ada akun dengan email yang sama tapi **belum terverifikasi**: penyerang bisa saja mendaftar
 *   duluan dengan email korban. Login Google (yang membuktikan pemilik email) mengambil alih:
 *   kata sandi dikosongkan, semua token dicabut, sesi "ingat saya" diputus.
 * - Tidak ada: akun baru, terverifikasi.
 *
 * Pemanggil wajib sudah memastikan klaim `email_verified` dari Google benar.
 */
class GoogleAccountLinker
{
    public function resolve(string $sub, string $email, string $name, ?string $avatar, ?string $privacyVersion = null): User
    {
        $email = mb_strtolower(trim($email));

        $user = User::where('google_sub', $sub)->first();
        if ($user) {
            $user->forceFill(['avatar' => $avatar ?? $user->avatar])->save();

            return $user;
        }

        $user = User::where('email', $email)->first();

        if ($user && ! $user->hasVerifiedEmail()) {
            $user->revokeTokens();
            $user->forceFill([
                'password' => null,
                'google_sub' => $sub,
                'email_verified_at' => now(),
                'avatar' => $avatar,
            ])->save();
            $user->revokeWebSessions();

            return $user;
        }

        if ($user) {
            $user->forceFill(['google_sub' => $sub, 'avatar' => $avatar ?? $user->avatar])->save();

            return $user;
        }

        return User::forceCreate([
            'google_sub' => $sub,
            'email' => $email,
            'name' => $name !== '' ? $name : $email,
            'avatar' => $avatar,
            'email_verified_at' => now(),
            'privacy_consented_at' => $privacyVersion ? now() : null,
            'privacy_policy_version' => $privacyVersion,
        ]);
    }
}
