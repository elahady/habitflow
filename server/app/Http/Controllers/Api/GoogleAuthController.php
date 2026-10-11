<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\UserToken;
use App\Services\GoogleAccountLinker;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;

/**
 * Login API dari app Android lewat Google Sign-In (tahap 25). App kirim ID token dari
 * Credential Manager, server verifikasi langsung ke Google (endpoint tokeninfo - cukup
 * untuk skala pemakaian app ini, tanpa perlu library verifikasi JWT tambahan), lalu balas
 * token API (beda dari token 19B).
 */
class GoogleAuthController extends Controller
{
    public function login(Request $request, GoogleAccountLinker $linker): JsonResponse
    {
        $request->validate([
            'id_token' => ['required', 'string'],
            'device_name' => ['nullable', 'string', 'max:100'],
            'privacy_policy_version' => ['nullable', 'string', 'max:32'],
        ]);

        $response = Http::get('https://oauth2.googleapis.com/tokeninfo', [
            'id_token' => $request->string('id_token'),
        ]);

        if (! $response->successful()) {
            throw ValidationException::withMessages(['id_token' => 'Token Google tidak sah.']);
        }

        $claims = $response->json();

        $expectedAudience = config('services.google.android_client_id');
        if (($claims['aud'] ?? null) !== $expectedAudience) {
            throw ValidationException::withMessages(['id_token' => 'Token Google bukan untuk app ini.']);
        }

        if (($claims['email_verified'] ?? 'false') !== 'true') {
            throw ValidationException::withMessages(['id_token' => 'Email Google belum diverifikasi.']);
        }

        // Aturan penautan (termasuk pengambilalihan akun email yang belum terverifikasi) ada di linker.
        $user = $linker->resolve(
            $claims['sub'],
            $claims['email'],
            $claims['name'] ?? $claims['email'],
            $claims['picture'] ?? null,
            $request->input('privacy_policy_version'),
        );

        [, $plain] = UserToken::issue($user, $request->input('device_name', 'android'));

        $account = $user->toAccount();

        // `user` dipertahankan untuk app yang sudah terpasang (membaca "user"); kontrak 31A memakai `account`.
        return response()->json(['token' => $plain, 'account' => $account, 'user' => $account]);
    }
}
