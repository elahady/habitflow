<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Langkah satu tanggal dari Health Connect di HP (tahap 30). `date` dibiarkan string Y-m-d seperti `prayer_days`. */
class StepDay extends Model
{
    protected $fillable = ['user_id', 'date', 'steps'];

    protected function casts(): array
    {
        return ['steps' => 'integer'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
