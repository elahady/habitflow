<?php

namespace App\Http\Controllers;

use App\Rules\StrongPassword;
use App\Services\AccountDeletionService;
use App\Services\AccountService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

/** Halaman Akun di web (tahap 31C): kata sandi, Keluar dari semua perangkat, dan hapus akun (31D). */
class AccountPageController extends Controller
{
    public function __construct(private AccountService $accounts) {}

    public function show(Request $request): View
    {
        return view('account', ['user' => $request->user()]);
    }

    public function updatePassword(Request $request): RedirectResponse
    {
        $user = $request->user();

        $data = $request->validate([
            'current_password' => [$user->password !== null ? 'required' : 'nullable', 'string'],
            'password' => ['required', 'string', 'confirmed', new StrongPassword],
        ]);

        [$status, $wait] = $this->accounts->changePassword($user, $data['current_password'] ?? null, $data['password']);

        if ($status === AccountService::PASSWORD_THROTTLED) {
            throw ValidationException::withMessages(['current_password' => 'Terlalu banyak percobaan. Coba lagi dalam '.$wait.' detik.']);
        }
        if ($status === AccountService::PASSWORD_WRONG) {
            throw ValidationException::withMessages(['current_password' => 'Kata sandi saat ini salah.']);
        }

        // Semua sesi lain sudah dicabut; sesi ini tetap hidup, jadi dicap ulang dengan angka baru.
        $request->session()->put('session_epoch', $user->session_epoch);
        $request->session()->regenerate();

        return redirect()->route('account')->with('status', 'Kata sandi diganti. Perangkat lain dikeluarkan.');
    }

    public function logoutEverywhere(Request $request): RedirectResponse
    {
        $user = $request->user();
        $user->revokeTokens();
        $user->revokeWebSessions();

        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login')->with('status', 'Kamu sudah keluar dari semua perangkat.');
    }

    /** Hapus akun dengan konfirmasi kata sandi. Akun tanpa kata sandi memakai kode email (DeleteAccountController). */
    public function destroy(Request $request, AccountDeletionService $deleter): RedirectResponse
    {
        $user = $request->user();
        $data = $request->validate(['delete_password' => ['required', 'string']], [], ['delete_password' => 'kata sandi']);

        [$status, $wait] = $this->accounts->confirmPasswordForDeletion($user, $data['delete_password']);

        if ($status === AccountService::PASSWORD_THROTTLED) {
            throw ValidationException::withMessages(['delete_password' => 'Terlalu banyak percobaan. Coba lagi dalam '.$wait.' detik.']);
        }
        if ($status === AccountService::PASSWORD_WRONG) {
            throw ValidationException::withMessages(['delete_password' => 'Kata sandi salah.']);
        }

        $deleter->delete($user, AccountDeletionService::SOURCE_WEB);

        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login')->with('status', 'Akun dan semua datamu sudah dihapus.');
    }
}
