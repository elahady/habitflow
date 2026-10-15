<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DailyBrief extends Model
{
    // `date` sengaja tanpa cast: dibiarkan string Y-m-d supaya pencarian upsert per tanggal tepat.
    protected $fillable = ['user_id', 'date', 'email_digest', 'meeting_note'];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
