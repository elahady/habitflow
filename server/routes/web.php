<?php

use App\Http\Controllers\Auth\AuthenticatedSessionController;
use App\Http\Controllers\Auth\GoogleAuthController;
use App\Http\Controllers\Auth\RegisteredUserController;
use Illuminate\Support\Facades\Route;

// Dipakai guest (tombol Google di Login/Register) maupun user yang sudah login
// (tombol "Hubungkan Google" di Dashboard) - lihat GoogleAuthController.
Route::get('/auth/google', [GoogleAuthController::class, 'redirect'])->name('auth.google');
Route::get('/auth/google/callback', [GoogleAuthController::class, 'callback']);

Route::middleware('guest')->group(function () {
    Route::get('/login', [AuthenticatedSessionController::class, 'create'])->name('login');
    Route::post('/login', [AuthenticatedSessionController::class, 'store']);
    Route::get('/register', [RegisteredUserController::class, 'create'])->name('register');
    Route::post('/register', [RegisteredUserController::class, 'store']);
});

Route::middleware('auth')->group(function () {
    Route::post('/logout', [AuthenticatedSessionController::class, 'destroy'])->name('logout');
    Route::delete('/auth/google', [GoogleAuthController::class, 'destroy'])->name('auth.google.destroy');

    Route::get('/', \App\Livewire\Dashboard::class)->name('dashboard');
    Route::get('/tugas-rumah', \App\Livewire\Teams::class)->name('teams');
    Route::get('/habit', \App\Livewire\HabitToday::class)->name('habit');

    Route::get('/invite/{token}', [\App\Http\Controllers\InviteController::class, 'show'])->name('invites.show');
    Route::post('/invite/{token}/accept', [\App\Http\Controllers\InviteController::class, 'accept'])->name('invites.accept');
});
