<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class ScheduleBlock extends Model
{
    public const START_FIXED = 0;

    public const START_PRAYER = 1;

    public const PRAYERS = ['SUBUH', 'DZUHUR', 'ASHAR', 'MAGHRIB', 'ISYA'];

    public const LEVELS = ['INFO', 'REMINDER', 'ALARM'];

    public const WORK_ACTIONS = ['SCRUM', 'EOD'];

    protected $fillable = [
        'user_id', 'name', 'start_type', 'start_value', 'prayer', 'duration_minutes',
        'end_minute_of_day', 'active_days', 'level', 'sort_order', 'work_action', 'work_reminders',
    ];

    protected function casts(): array
    {
        return [
            'start_type' => 'integer',
            'start_value' => 'integer',
            'duration_minutes' => 'integer',
            'end_minute_of_day' => 'integer',
            'active_days' => 'integer',
            'sort_order' => 'integer',
            'work_reminders' => 'boolean',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function habits(): BelongsToMany
    {
        return $this->belongsToMany(Habit::class, 'schedule_block_habits');
    }
}
