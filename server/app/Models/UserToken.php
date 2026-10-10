<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Str;

class UserToken extends Model
{
    protected $fillable = ['user_id', 'name', 'token_hash', 'last_used_at'];

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

    /** Buat token baru untuk user. Mengembalikan [model, token asli]; token asli tidak disimpan di mana pun. */
    public static function issue(User $user, string $name): array
    {
        $plain = Str::random(48);
        $model = static::create([
            'user_id' => $user->id,
            'name' => $name,
            'token_hash' => static::hash($plain),
        ]);

        return [$model, $plain];
    }
}
