<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Rules\StrongPassword;
use App\Services\AccountService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

/**
 * Daftar web (tahap 31C): sama dengan API, akun baru belum terverifikasi dan kode 6 angka
 * dikirim ke email. Tidak langsung masuk; masuk setelah kode benar di halaman verifikasi.
 */
class RegisteredUserController extends Controller
{
    public function __construct(private AccountService $accounts) {}

    public function create(): View
    {
        return view('auth.register');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'string', 'email', 'max:255'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
            'privacy' => ['accepted'],
        ], [
            'privacy.accepted' => 'Kamu perlu menyetujui Kebijakan Privasi untuk mendaftar.',
        ]);

        $user = $this->accounts->register(
            $data['name'],
            $data['email'],
            $data['password'],
            config('habitflow.privacy_policy_version'),
        );

        if (! $user) {
            throw ValidationException::withMessages(['email' => 'Email ini sudah terdaftar. Silakan masuk.']);
        }

        return redirect()->route('verification.show')
            ->with('verification_email', $user->email)
            ->with('status', 'Kami mengirim kode 6 angka ke '.$user->email.'.');
    }
}
