<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Satu baris per pengguna (tahap 30): zona waktu, tinggi dan target berat, serta fokus kesehatan terakhir yang dihitung
 * Android beserta teksnya. Web hanya menampilkan; aturan fokus hanya ada di Android (`domain/health/Focus.kt`).
 */
class HealthFocus extends Model
{
    protected $table = 'health_focus';

    protected $fillable = [
        'user_id', 'timezone', 'height_cm', 'target_kg',
        'rule', 'walk_minutes', 'title', 'sentence', 'note', 'reasons', 'computed_at',
    ];

    protected function casts(): array
    {
        return [
            'height_cm' => 'float',
            'target_kg' => 'float',
            'walk_minutes' => 'integer',
            'reasons' => 'array',
            'computed_at' => 'datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
