<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;

/**
 * Login cuma lewat Google Sign-In (tahap 25) - tanpa password. Beda dari ApiToken
 * (token per-HP tanpa akun, tahap 19B), yang tetap jalan berdampingan.
 */
class User extends Authenticatable
{
    use HasFactory;

    protected $fillable = ['google_sub', 'email', 'name', 'avatar'];

    public function tokens(): HasMany
    {
        return $this->hasMany(UserToken::class);
    }
}
