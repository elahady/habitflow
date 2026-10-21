<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Laravel\Passport\Contracts\OAuthenticatable;
use Laravel\Passport\HasApiTokens;
use Laravel\Passport\Token as OAuthToken;

/**
 * Login lewat email/kata sandi atau Google, di web dan di API Android (tahap 25, diperluas
 * tahap 31). google_sub nullable karena tidak wajib. Akun email baru belum terverifikasi
 * (email_verified_at kosong) sampai kode 6 angka dari email dipakai; akun Google dianggap
 * terverifikasi. Kolom verifikasi dan persetujuan sengaja tidak mass-assignable: hanya
 * diisi lewat forceFill di alur yang memang berwenang. Token per-HP tahap 19B
 * (api_tokens) dipensiunkan di 31E dan dihapus di 31L.
 */
class User extends Authenticatable implements OAuthenticatable
{
    // `tokens()` di bawah (UserToken, token REST) sengaja menimpa `tokens()` milik HasApiTokens Passport;
    // token OAuth untuk MCP dicabut lewat revokeOAuthTokens().
    use HasApiTokens, HasFactory;

    protected $fillable = ['google_sub', 'email', 'name', 'avatar', 'password'];

    protected $hidden = ['password', 'remember_token'];

    protected function casts(): array
    {
        return [
            'password' => 'hashed',
            'email_verified_at' => 'datetime',
            'privacy_consented_at' => 'datetime',
        ];
    }

    public function hasVerifiedEmail(): bool
    {
        return $this->email_verified_at !== null;
    }

    /** Bentuk `account` di jawaban API (kontrak 31A). */
    public function toAccount(): array
    {
        return ['id' => $this->id, 'name' => $this->name, 'email' => $this->email, 'avatar' => $this->avatar];
    }

    /** Cabut token API; $exceptTokenId dipertahankan (ganti kata sandi mempertahankan sesi yang dipakai). */
    public function revokeTokens(?int $exceptTokenId = null): void
    {
        $this->tokens()->when($exceptTokenId, fn ($q) => $q->where('id', '!=', $exceptTokenId))->delete();
        $this->revokeOAuthTokens();
    }

    /** Cabut semua token OAuth (akses MCP) beserta refresh token-nya. */
    public function revokeOAuthTokens(): void
    {
        $ids = OAuthToken::where('user_id', $this->getKey())->pluck('id');
        OAuthToken::whereIn('id', $ids)->update(['revoked' => true]);
        DB::table('oauth_refresh_tokens')->whereIn('access_token_id', $ids)->update(['revoked' => true]);
    }

    /**
     * Putuskan semua sesi browser: cookie "ingat saya" lama tidak cocok lagi dengan remember_token
     * baru, dan sesi yang sedang hidup ditolak oleh EnsureSessionNotRevoked karena session_epoch naik.
     */
    public function revokeWebSessions(): void
    {
        $this->forceFill([
            'remember_token' => Str::random(60),
            'session_epoch' => $this->session_epoch + 1,
        ])->save();
    }

    public function tokens(): HasMany
    {
        return $this->hasMany(UserToken::class);
    }

    public function teams(): BelongsToMany
    {
        return $this->belongsToMany(Team::class, 'team_members')->withTimestamps();
    }

    public function habits(): HasMany
    {
        return $this->hasMany(Habit::class);
    }

    public function todos(): HasMany
    {
        return $this->hasMany(Todo::class);
    }

    public function followUps(): HasMany
    {
        return $this->hasMany(FollowUp::class);
    }

    public function prayerDays(): HasMany
    {
        return $this->hasMany(PrayerDay::class);
    }

    public function daysOff(): HasMany
    {
        return $this->hasMany(DayOff::class);
    }

    public function workDays(): HasMany
    {
        return $this->hasMany(WorkDay::class);
    }

    public function dailyBriefs(): HasMany
    {
        return $this->hasMany(DailyBrief::class);
    }

    public function weightEntries(): HasMany
    {
        return $this->hasMany(WeightEntry::class);
    }

    public function bloodPressureEntries(): HasMany
    {
        return $this->hasMany(BloodPressureEntry::class);
    }

    public function stepDays(): HasMany
    {
        return $this->hasMany(StepDay::class);
    }

    public function healthFocus(): HasOne
    {
        return $this->hasOne(HealthFocus::class);
    }

    public function snapshots(): HasMany
    {
        return $this->hasMany(Snapshot::class);
    }

    public function scheduleBlocks(): HasMany
    {
        return $this->hasMany(ScheduleBlock::class);
    }
}
