<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Satu catatan berat dari Android (tahap 30). `time_millis` adalah waktu catat di HP, epoch milidetik. */
class WeightEntry extends Model
{
    protected $fillable = ['user_id', 'time_millis', 'kg'];

    protected function casts(): array
    {
        return ['time_millis' => 'integer', 'kg' => 'float'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
