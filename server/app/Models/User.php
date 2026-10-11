<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Support\Str;

/**
 * Login lewat email/kata sandi atau Google, di web dan di API Android (tahap 25, diperluas
 * tahap 31). google_sub nullable karena tidak wajib. Akun email baru belum terverifikasi
 * (email_verified_at kosong) sampai kode 6 angka dari email dipakai; akun Google dianggap
 * terverifikasi. Kolom verifikasi dan persetujuan sengaja tidak mass-assignable: hanya
 * diisi lewat forceFill di alur yang memang berwenang. Beda dari ApiToken (token per-HP
 * tanpa akun, tahap 19B), yang dipensiunkan di 31E.
 */
class User extends Authenticatable
{
    use HasFactory;

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
    }

    /** Putuskan sesi web "ingat saya" juga: cookie lama tidak cocok lagi dengan remember_token baru. */
    public function rotateRememberToken(): void
    {
        $this->forceFill(['remember_token' => Str::random(60)])->save();
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

    public function scheduleBlocks(): HasMany
    {
        return $this->hasMany(ScheduleBlock::class);
    }
}
