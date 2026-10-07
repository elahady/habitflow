<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

class ApiToken extends Model
{
    protected $fillable = ['name', 'token_hash', 'last_used_at'];

    protected function casts(): array
    {
        return ['last_used_at' => 'datetime'];
    }

    public function snapshots(): HasMany
    {
        return $this->hasMany(Snapshot::class);
    }

    public static function hash(string $plain): string
    {
        return hash('sha256', $plain);
    }

    /** Buat token baru. Mengembalikan [model, token asli]; token asli tidak disimpan di mana pun. */
    public static function issue(string $name): array
    {
        $plain = Str::random(48);
        $model = static::create(['name' => $name, 'token_hash' => static::hash($plain)]);

        return [$model, $plain];
    }
}
