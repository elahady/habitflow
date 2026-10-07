<?php

use App\Http\Controllers\SnapshotController;
use Illuminate\Support\Facades\Route;

// Kontrak API ada di docs/concept.md (tahap 19B) di repo HabitFlow.
Route::prefix('v1')->middleware(['token', 'throttle:habitflow'])->group(function () {
    Route::get('ping', fn () => response()->json(['ok' => true]));
    Route::put('snapshot', [SnapshotController::class, 'store']);
    Route::get('snapshot/latest', [SnapshotController::class, 'latest']);
});
