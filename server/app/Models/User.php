<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;

/**
 * Login web lewat email/password atau Google (tahap 25) - keduanya bisa dipakai langsung
 * maupun ditautkan belakangan, google_sub nullable karena tidak wajib. API Android tetap
 * Google-only, tidak berubah. Beda dari ApiToken (token per-HP tanpa akun, tahap 19B),
 * yang tetap jalan berdampingan.
 */
class User extends Authenticatable
{
    use HasFactory;

    protected $fillable = ['google_sub', 'email', 'name', 'avatar', 'password'];

    protected $hidden = ['password', 'remember_token'];

    protected function casts(): array
    {
        return ['password' => 'hashed'];
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
}
