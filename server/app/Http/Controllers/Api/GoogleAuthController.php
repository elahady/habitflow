<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\User;
use App\Models\UserToken;
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
    public function login(Request $request): JsonResponse
    {
        $request->validate(['id_token' => ['required', 'string']]);

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

        $user = User::updateOrCreate(
            ['google_sub' => $claims['sub']],
            [
                'email' => $claims['email'],
                'name' => $claims['name'] ?? $claims['email'],
                'avatar' => $claims['picture'] ?? null,
            ]
        );

        [, $plain] = UserToken::issue($user, 'android');

        return response()->json([
            'token' => $plain,
            'user' => [
                'id' => $user->id,
                'email' => $user->email,
                'name' => $user->name,
                'avatar' => $user->avatar,
            ],
        ]);
    }
}
