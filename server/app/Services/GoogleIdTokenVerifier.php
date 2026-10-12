<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;

/**
 * Verifikasi ID token Google dari app Android lewat endpoint tokeninfo (tahap 25, dipisah di 31D
 * supaya login dan konfirmasi hapus akun memakai pemeriksaan yang sama persis).
 */
class GoogleIdTokenVerifier
{
    /** Mengembalikan klaim Google yang sudah terbukti sah, untuk app ini, dan emailnya terverifikasi. */
    public function claims(string $idToken): array
    {
        $response = Http::get('https://oauth2.googleapis.com/tokeninfo', ['id_token' => $idToken]);

        if (! $response->successful()) {
            throw ValidationException::withMessages(['id_token' => 'Token Google tidak sah.']);
        }

        $claims = $response->json();

        if (($claims['aud'] ?? null) !== config('services.google.android_client_id')) {
            throw ValidationException::withMessages(['id_token' => 'Token Google bukan untuk app ini.']);
        }

        if (($claims['email_verified'] ?? 'false') !== 'true') {
            throw ValidationException::withMessages(['id_token' => 'Email Google belum diverifikasi.']);
        }

        return $claims;
    }
}
