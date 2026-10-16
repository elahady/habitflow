<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class WorkDay extends Model
{
    // `date` sengaja tanpa cast: dibiarkan string Y-m-d supaya pencarian per tanggal tepat.
    protected $fillable = ['user_id', 'date', 'eod_note', 'scrum_done_at', 'eod_done_at', 'eod_done_count', 'eod_total_count'];

    protected function casts(): array
    {
        return [
            'scrum_done_at' => 'datetime',
            'eod_done_at' => 'datetime',
            'eod_done_count' => 'integer',
            'eod_total_count' => 'integer',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
