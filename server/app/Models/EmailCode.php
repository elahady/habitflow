<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Kode 6 angka lewat email (tahap 31B). Yang disimpan hanya hash-nya, tidak pernah kodenya. */
class EmailCode extends Model
{
    public const PURPOSE_VERIFY = 'verify';

    public const PURPOSE_RESET = 'reset';

    protected $fillable = ['user_id', 'purpose', 'code_hash', 'expires_at', 'attempts', 'consumed_at'];

    protected function casts(): array
    {
        return ['expires_at' => 'datetime', 'consumed_at' => 'datetime'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
