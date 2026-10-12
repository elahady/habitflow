<?php

use App\Http\Controllers\Api\AccountController;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\FollowUpController;
use App\Http\Controllers\Api\GoogleAuthController;
use App\Http\Controllers\Api\HabitController;
use App\Http\Controllers\Api\PrayerDayController;
use App\Http\Controllers\Api\ScheduleBlockController;
use App\Http\Controllers\Api\TeamController;
use App\Http\Controllers\Api\TeamTodoController;
use App\Http\Controllers\Api\TodoController;
use App\Http\Controllers\SnapshotController;
use Illuminate\Support\Facades\Route;

// Kontrak API tahap 19B (token per-HP tanpa akun) ada di docs/concept.md.
Route::prefix('v1')->middleware(['token', 'throttle:habitflow'])->group(function () {
    Route::get('ping', fn () => response()->json(['ok' => true]));
    Route::put('snapshot', [SnapshotController::class, 'store']);
    Route::get('snapshot/latest', [SnapshotController::class, 'latest']);
});

// Login Google (tahap 25) - tukar ID token Google dengan token API per-user.
// Akun email dan kata sandi (tahap 31B): daftar, verifikasi kode, masuk, lupa dan atur ulang kata sandi.
// Batas laju khusus tiap jalur ada di AuthController dan EmailCodeService.
Route::prefix('v1')->middleware('throttle:habitflow')->group(function () {
    Route::post('auth/google', [GoogleAuthController::class, 'login']);

    Route::post('auth/register', [AuthController::class, 'register']);
    Route::post('auth/verify-email', [AuthController::class, 'verifyEmail']);
    Route::post('auth/resend-verification', [AuthController::class, 'resendVerification']);
    Route::post('auth/login', [AuthController::class, 'login']);
    Route::post('auth/forgot-password', [AuthController::class, 'forgotPassword']);
    Route::post('auth/reset-password', [AuthController::class, 'resetPassword']);
});

// Sesi dan akun yang sedang masuk (tahap 31B).
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::post('auth/logout', [AuthController::class, 'logout']);
    Route::post('auth/logout-all', [AuthController::class, 'logoutAll']);
    Route::get('me', [AccountController::class, 'show']);
    Route::put('account/password', [AccountController::class, 'updatePassword']);
    Route::delete('account', [AccountController::class, 'destroy']);
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

// Habit + to-do harian (tahap 28) - per user, lewat akun (bukan token 19B). Android masih
// pakai Room sebagai sumber utama; endpoint ini untuk web dan sinkron Android menyusul.
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::get('habits', [HabitController::class, 'index']);
    Route::post('habits', [HabitController::class, 'store']);
    Route::put('habits/{habit}', [HabitController::class, 'update']);
    Route::delete('habits/{habit}', [HabitController::class, 'destroy']);
    Route::post('habits/{habit}/entries', [HabitController::class, 'toggleEntry']);

    Route::get('todos', [TodoController::class, 'index']);
    Route::post('todos', [TodoController::class, 'store']);
    Route::put('todos/{todo}', [TodoController::class, 'update']);
    Route::delete('todos/{todo}', [TodoController::class, 'destroy']);
});

// Follow-up kerja (tahap 27) - per user, sumber kebenaran di server, PUT dicek konfliknya (409).
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::get('follow-ups', [FollowUpController::class, 'index']);
    Route::post('follow-ups', [FollowUpController::class, 'store']);
    Route::put('follow-ups/{followUp}', [FollowUpController::class, 'update']);
    Route::delete('follow-ups/{followUp}', [FollowUpController::class, 'destroy']);
});

// Jadwal harian (tahap 29) - per user, PUT dicek konfliknya (409). Hanya blok; waktu sholat
// tidak dihitung di server (mekanisme untuk web belum diputuskan).
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::get('schedule-blocks', [ScheduleBlockController::class, 'index']);
    Route::post('schedule-blocks', [ScheduleBlockController::class, 'store']);
    Route::put('schedule-blocks/{scheduleBlock}', [ScheduleBlockController::class, 'update']);
    Route::delete('schedule-blocks/{scheduleBlock}', [ScheduleBlockController::class, 'destroy']);
});

// Cache waktu sholat harian dari Android (tahap 29, opsi a) - server hanya menyimpan, tidak menghitung.
Route::prefix('v1')->middleware(['user-token', 'throttle:habitflow'])->group(function () {
    Route::get('prayer-days', [PrayerDayController::class, 'index']);
    Route::put('prayer-days', [PrayerDayController::class, 'upsert']);
});
