<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Facades\Auth;
use Laravel\Socialite\Facades\Socialite;

/**
 * Satu jalur Google OAuth untuk dua kebutuhan (revisi kedua, 10 Oktober 2026), karena
 * Google Cloud Console cuma punya satu redirect URI terdaftar:
 *
 * - **Guest** (dari tombol Google di halaman Login/Register): masuk kalau google_sub atau
 *   email sudah match user yang ada (supaya tidak dobel akun kalau sebelumnya daftar pakai
 *   password dengan email sama), atau daftar otomatis kalau belum ada sama sekali.
 * - **Sudah login** (dari tombol "Hubungkan Google" di Dashboard): tempelkan google_sub ke
 *   user yang sedang aktif saja, tidak ganti sesi.
 */
class GoogleAuthController extends Controller
{
    public function redirect(): RedirectResponse
    {
        return Socialite::driver('google')->redirect();
    }

    public function callback(): RedirectResponse
    {
        $googleUser = Socialite::driver('google')->user();

        if (Auth::check()) {
            Auth::user()->forceFill([
                'google_sub' => $googleUser->getId(),
                'avatar' => $googleUser->getAvatar(),
            ])->save();

            return redirect()->route('dashboard')->with('status', 'Akun Google terhubung.');
        }

        $user = User::where('google_sub', $googleUser->getId())->first()
            ?? User::where('email', $googleUser->getEmail())->first();

        if ($user) {
            $user->forceFill([
                'google_sub' => $googleUser->getId(),
                'avatar' => $googleUser->getAvatar(),
            ])->save();
        } else {
            $user = User::create([
                'google_sub' => $googleUser->getId(),
                'email' => $googleUser->getEmail(),
                'name' => $googleUser->getName(),
                'avatar' => $googleUser->getAvatar(),
            ]);
        }

        Auth::login($user, remember: true);

        return redirect()->intended(route('dashboard'));
    }

    public function destroy(): RedirectResponse
    {
        Auth::user()->forceFill(['google_sub' => null])->save();

        return redirect()->route('dashboard')->with('status', 'Akun Google diputus.');
    }
}
