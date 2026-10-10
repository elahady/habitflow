<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Facades\Auth;
use Laravel\Socialite\Facades\Socialite;

/**
 * Hubungkan akun Google ke user yang sudah login (revisi 10 Oktober 2026 - Google bukan
 * lagi cara login langsung di web, cuma fitur tambahan setelah akun ada). Dulu
 * GoogleAuthController ini yang login+buat user langsung dari Google; sekarang hanya
 * menempelkan google_sub ke user yang sedang aktif.
 */
class GoogleConnectController extends Controller
{
    public function redirect(): RedirectResponse
    {
        return Socialite::driver('google')->redirect();
    }

    public function callback(): RedirectResponse
    {
        $googleUser = Socialite::driver('google')->user();

        $user = Auth::user();
        $user->forceFill([
            'google_sub' => $googleUser->getId(),
            'avatar' => $googleUser->getAvatar(),
        ])->save();

        return redirect()->route('dashboard')->with('status', 'Akun Google terhubung.');
    }

    public function destroy(): RedirectResponse
    {
        Auth::user()->forceFill(['google_sub' => null])->save();

        return redirect()->route('dashboard')->with('status', 'Akun Google diputus.');
    }
}
