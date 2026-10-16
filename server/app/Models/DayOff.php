<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DayOff extends Model
{
    protected $table = 'days_off';

    // `date` sengaja tanpa cast: dibiarkan string Y-m-d supaya pencarian per tanggal tepat.
    protected $fillable = ['user_id', 'date'];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
