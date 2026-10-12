<?php

namespace App\Http\Controllers;

use App\Services\AccountDeletionService;
use App\Services\AccountService;
use App\Services\EmailCodeService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

/**
 * Hapus akun lewat kode email (tahap 31D). Halaman publik: bisa dipakai tanpa login, termasuk oleh
 * orang yang sudah mencopot app dan lupa kata sandinya (URL /hapus-akun dipakai di Play Console).
 * Kode 6 angka ke email terverifikasi membuktikan pemilik akun. Pengguna yang masuk dan punya
 * kata sandi memakai konfirmasi kata sandi di halaman Akun (AccountPageController::destroy).
 */
class DeleteAccountController extends Controller
{
    public function __construct(private AccountService $accounts, private AccountDeletionService $deleter) {}

    public function sendCode(Request $request): RedirectResponse
    {
        $data = $request->validate(['email' => ['required', 'string', 'email']]);

        $this->accounts->sendDeletionCode($data['email']);

        // Jawaban sama apakah email terdaftar atau tidak.
        return redirect()->route('delete-account.confirm')
            ->with('deletion_email', $this->accounts->normalizeEmail($data['email']))
            ->with('status', 'Kalau email itu terdaftar, kode 6 angka sudah dikirim. Berlaku 10 menit.');
    }

    public function confirm(): View
    {
        return view('legal.delete-account-confirm', ['email' => session('deletion_email', old('email'))]);
    }

    public function destroy(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'email' => ['required', 'string', 'email'],
            'code' => ['required', 'string'],
        ]);
        $email = $this->accounts->normalizeEmail($data['email']);

        if ($wait = $this->accounts->codeAttemptWait('delete', $request->ip(), $email)) {
            throw ValidationException::withMessages(['code' => 'Terlalu banyak percobaan. Coba lagi dalam '.$wait.' detik.']);
        }

        [$result, $user] = $this->accounts->verifyDeletionCode($email, trim($data['code']));

        if ($result !== EmailCodeService::OK) {
            return back()->withInput(['email' => $email])
                ->with('deletion_email', $email)
                ->withErrors(['code' => $result === EmailCodeService::EXPIRED
                    ? 'Kode sudah kedaluwarsa. Minta kode baru.'
                    : 'Kode salah.']);
        }

        $this->deleter->delete($user, AccountDeletionService::SOURCE_WEB_CODE);

        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login')->with('status', 'Akun dan semua datamu sudah dihapus.');
    }
}
