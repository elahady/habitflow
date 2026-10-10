<?php

use App\Http\Controllers\Api\GoogleAuthController;
use App\Http\Controllers\SnapshotController;
use Illuminate\Support\Facades\Route;

// Kontrak API tahap 19B (token per-HP tanpa akun) ada di docs/concept.md.
Route::prefix('v1')->middleware(['token', 'throttle:habitflow'])->group(function () {
    Route::get('ping', fn () => response()->json(['ok' => true]));
    Route::put('snapshot', [SnapshotController::class, 'store']);
    Route::get('snapshot/latest', [SnapshotController::class, 'latest']);
});

// Login Google (tahap 25) - tukar ID token Google dengan token API per-user.
Route::prefix('v1')->middleware('throttle:habitflow')->group(function () {
    Route::post('auth/google', [GoogleAuthController::class, 'login']);
});
