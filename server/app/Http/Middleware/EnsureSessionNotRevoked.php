<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Symfony\Component\HttpFoundation\Response;

/**
 * Menolak sesi web yang dibuat sebelum pemutusan terakhir (users.session_epoch naik, tahap 31C).
 * Sesi tanpa catatan (baru, atau masuk lewat cookie "ingat saya") dicap dengan angka sekarang.
 */
class EnsureSessionNotRevoked
{
    private const KEY = 'session_epoch';

    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user();

        if ($user) {
            $stamped = $request->session()->get(self::KEY);

            if ($stamped !== null && (int) $stamped !== (int) $user->session_epoch) {
                Auth::logout();
                $request->session()->invalidate();
                $request->session()->regenerateToken();

                return redirect()->route('login')->with('status', 'Sesi berakhir, silakan masuk lagi.');
            }

            $request->session()->put(self::KEY, $user->session_epoch);
        }

        return $next($request);
    }
}
