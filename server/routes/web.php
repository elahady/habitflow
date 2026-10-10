<?php

use App\Http\Controllers\Auth\GoogleAuthController;
use Illuminate\Support\Facades\Route;

Route::get('/login/google', [GoogleAuthController::class, 'redirect'])->name('login.google');
Route::get('/login/google/callback', [GoogleAuthController::class, 'callback']);
Route::post('/logout', [GoogleAuthController::class, 'logout'])->name('logout');

Route::middleware('auth')->group(function () {
    Route::get('/', \App\Livewire\Dashboard::class)->name('dashboard');
});

Route::get('/login', fn () => view('auth.login'))->name('login');
