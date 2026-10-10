<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;

/**
 * Login web lewat email/password (revisi 10 Oktober 2026). Google jadi fitur "connect"
 * opsional setelah akun ada (google_sub nullable) - bukan cara login langsung lagi.
 * API Android tetap Google-only, tidak berubah. Beda dari ApiToken (token per-HP tanpa
 * akun, tahap 19B), yang tetap jalan berdampingan.
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
}
