<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class FollowUp extends Model
{
    public const STATUSES = ['INBOX', 'ACTIVE', 'WAITING', 'DONE'];

    protected $fillable = [
        'user_id', 'title', 'status', 'date', 'time', 'person', 'note', 'done_at',
        'picked_date', 'event_id', 'event_date', 'event_title',
    ];

    protected function casts(): array
    {
        return [
            'date' => 'date:Y-m-d',
            'picked_date' => 'date:Y-m-d',
            'event_date' => 'date:Y-m-d',
            'done_at' => 'datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
