<?php

use App\Http\Controllers\Api\GoogleAuthController;
use App\Http\Controllers\Api\TeamController;
use App\Http\Controllers\Api\TeamTodoController;
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

// To-Do Tim (tahap 26) - butuh login Google dulu (UserToken), beda dari token 19B di atas.
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::get('teams', [TeamController::class, 'index']);
    Route::post('teams', [TeamController::class, 'store']);
    Route::post('teams/invites', [TeamController::class, 'createInvite']);
    Route::post('teams/invites/{token}/accept', [TeamController::class, 'acceptInvite']);

    Route::get('teams/{team}/todos', [TeamTodoController::class, 'index']);
    Route::post('teams/{team}/todos', [TeamTodoController::class, 'store']);
    Route::put('teams/todos/{todo}', [TeamTodoController::class, 'update']);
    Route::delete('teams/todos/{todo}', [TeamTodoController::class, 'destroy']);
});
