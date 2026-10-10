<?php

use App\Http\Controllers\Auth\AuthenticatedSessionController;
use App\Http\Controllers\Auth\GoogleConnectController;
use App\Http\Controllers\Auth\RegisteredUserController;
use Illuminate\Support\Facades\Route;

Route::middleware('guest')->group(function () {
    Route::get('/login', [AuthenticatedSessionController::class, 'create'])->name('login');
    Route::post('/login', [AuthenticatedSessionController::class, 'store']);
    Route::get('/register', [RegisteredUserController::class, 'create'])->name('register');
    Route::post('/register', [RegisteredUserController::class, 'store']);
});

Route::middleware('auth')->group(function () {
    Route::post('/logout', [AuthenticatedSessionController::class, 'destroy'])->name('logout');

    Route::get('/connect/google', [GoogleConnectController::class, 'redirect'])->name('connect.google');
    Route::get('/connect/google/callback', [GoogleConnectController::class, 'callback']);
    Route::delete('/connect/google', [GoogleConnectController::class, 'destroy'])->name('connect.google.destroy');

    Route::get('/', \App\Livewire\Dashboard::class)->name('dashboard');
});
