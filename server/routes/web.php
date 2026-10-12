<?php

use App\Http\Controllers\AccountPageController;
use App\Http\Controllers\Auth\AuthenticatedSessionController;
use App\Http\Controllers\Auth\EmailVerificationController;
use App\Http\Controllers\Auth\GoogleAuthController;
use App\Http\Controllers\Auth\PasswordResetController;
use App\Http\Controllers\Auth\RegisteredUserController;
use App\Http\Controllers\DeleteAccountController;
use App\Http\Controllers\InviteController;
use App\Livewire\Dashboard;
use App\Livewire\FollowUps;
use App\Livewire\HabitToday;
use App\Livewire\Teams;
use Illuminate\Support\Facades\Route;

// Dipakai guest (tombol Google di Login/Register) maupun user yang sudah login
// (tombol "Hubungkan Google" di Dashboard) - lihat GoogleAuthController.
Route::get('/auth/google', [GoogleAuthController::class, 'redirect'])->name('auth.google');
Route::get('/auth/google/callback', [GoogleAuthController::class, 'callback']);

// Halaman publik (tahap 31C): bisa dibuka tanpa login, URL-nya dipakai di Play Console.
Route::view('/kebijakan-privasi', 'legal.privacy')->name('privacy');
Route::view('/hapus-akun', 'legal.delete-account')->name('delete-account');
Route::post('/hapus-akun/kode', [DeleteAccountController::class, 'sendCode'])->name('delete-account.code');
Route::get('/hapus-akun/konfirmasi', [DeleteAccountController::class, 'confirm'])->name('delete-account.confirm');
Route::post('/hapus-akun/konfirmasi', [DeleteAccountController::class, 'destroy'])->name('delete-account.destroy');

Route::middleware('guest')->group(function () {
    Route::get('/login', [AuthenticatedSessionController::class, 'create'])->name('login');
    Route::post('/login', [AuthenticatedSessionController::class, 'store']);
    Route::get('/register', [RegisteredUserController::class, 'create'])->name('register');
    Route::post('/register', [RegisteredUserController::class, 'store']);

    Route::get('/verifikasi-email', [EmailVerificationController::class, 'show'])->name('verification.show');
    Route::post('/verifikasi-email', [EmailVerificationController::class, 'verify'])->name('verification.verify');
    Route::post('/verifikasi-email/kirim-ulang', [EmailVerificationController::class, 'resend'])->name('verification.resend');

    Route::get('/lupa-kata-sandi', [PasswordResetController::class, 'forgot'])->name('password.forgot');
    Route::post('/lupa-kata-sandi', [PasswordResetController::class, 'sendCode']);
    Route::get('/reset-kata-sandi', [PasswordResetController::class, 'reset'])->name('password.reset');
    Route::post('/reset-kata-sandi', [PasswordResetController::class, 'update'])->name('password.update');
});

Route::middleware(['auth', 'session-not-revoked'])->group(function () {
    Route::post('/logout', [AuthenticatedSessionController::class, 'destroy'])->name('logout');
    Route::delete('/auth/google', [GoogleAuthController::class, 'destroy'])->name('auth.google.destroy');

    Route::get('/akun', [AccountPageController::class, 'show'])->name('account');
    Route::put('/akun/kata-sandi', [AccountPageController::class, 'updatePassword'])->name('account.password');
    Route::post('/akun/keluar-semua', [AccountPageController::class, 'logoutEverywhere'])->name('account.logout-all');
    Route::delete('/akun', [AccountPageController::class, 'destroy'])->name('account.destroy');

    Route::get('/', Dashboard::class)->name('dashboard');
    Route::get('/tugas-rumah', Teams::class)->name('teams');
    Route::get('/habit', HabitToday::class)->name('habit');
    Route::get('/follow-up', FollowUps::class)->name('follow-ups');

    Route::get('/invite/{token}', [InviteController::class, 'show'])->name('invites.show');
    Route::post('/invite/{token}/accept', [InviteController::class, 'accept'])->name('invites.accept');
});
