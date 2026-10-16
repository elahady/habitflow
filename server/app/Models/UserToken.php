<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class UserToken extends Model
{
    /** Hanya boleh menulis ringkasan harian (`POST /daily-briefs`) dan mengecek sambungan (`GET /ping`). */
    public const SCOPE_DAILY_BRIEFS = 'daily-briefs';

    public const SCOPES = [self::SCOPE_DAILY_BRIEFS];

    protected $fillable = ['user_id', 'name', 'scope', 'token_hash', 'last_used_at'];

    protected function casts(): array
    {
        return ['last_used_at' => 'datetime'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public static function hash(string $plain): string
    {
        return hash('sha256', $plain);
    }

    /**
     * Apakah token ini boleh memanggil [request]. Token tanpa `scope` (login biasa) boleh semuanya. Token berscope hanya
     * boleh rute yang disebut di sini; rute baru otomatis tertutup untuknya, dan scope yang tidak dikenal menolak semuanya.
     */
    public function permits(Request $request): bool
    {
        return match ($this->scope) {
            null => true,
            self::SCOPE_DAILY_BRIEFS => ($request->isMethod('GET') && $request->is('api/v1/ping'))
                || ($request->isMethod('POST') && $request->is('api/v1/daily-briefs')),
            default => false,
        };
    }

    /** Buat token baru untuk user. Mengembalikan [model, token asli]; token asli tidak disimpan di mana pun. */
    public static function issue(User $user, string $name, ?string $scope = null): array
    {
        $plain = Str::random(48);
        $model = static::create([
            'user_id' => $user->id,
            'name' => $name,
            'scope' => $scope,
            'token_hash' => static::hash($plain),
        ]);

        return [$model, $plain];
    }
}
