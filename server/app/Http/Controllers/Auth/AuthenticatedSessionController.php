<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Services\AccountService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

class AuthenticatedSessionController extends Controller
{
    public function __construct(private AccountService $accounts) {}

    public function create(): View
    {
        return view('auth.login');
    }

    public function store(Request $request): RedirectResponse
    {
        $credentials = $request->validate([
            'email' => ['required', 'string', 'email'],
            'password' => ['required', 'string'],
        ]);

        [$status, $detail] = $this->accounts->checkLogin($credentials['email'], $credentials['password'], $request->ip());

        if ($status === AccountService::LOGIN_THROTTLED) {
            throw ValidationException::withMessages([
                'email' => 'Terlalu banyak percobaan. Coba lagi dalam '.$detail.' detik.',
            ]);
        }

        if ($status === AccountService::LOGIN_INVALID) {
            throw ValidationException::withMessages(['email' => 'Email atau kata sandi salah.']);
        }

        // Kata sandi benar tapi email belum diverifikasi: tidak masuk, kirim kode ke halaman verifikasi.
        if ($status === AccountService::LOGIN_UNVERIFIED) {
            $this->accounts->sendVerification($detail);

            return redirect()->route('verification.show')
                ->with('verification_email', $detail->email)
                ->with('status', 'Email kamu belum diverifikasi. Masukkan kode 6 angka yang kami kirim ke '.$detail->email.'.');
        }

        Auth::login($detail, remember: true);
        $request->session()->regenerate();

        return redirect()->intended(route('dashboard'));
    }

    public function destroy(Request $request): RedirectResponse
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect('/login');
    }
}
