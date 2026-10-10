<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;

class TeamInvite extends Model
{
    protected $fillable = ['team_id', 'token', 'created_by', 'expires_at', 'used_at', 'used_by'];

    protected function casts(): array
    {
        return ['expires_at' => 'datetime', 'used_at' => 'datetime'];
    }

    public function team(): BelongsTo
    {
        return $this->belongsTo(Team::class);
    }

    public function creator(): BelongsTo
    {
        return $this->belongsTo(User::class, 'created_by');
    }

    public function isValid(): bool
    {
        return $this->used_at === null && $this->expires_at->isFuture();
    }

    public static function issue(Team $team, User $creator): self
    {
        return static::create([
            'team_id' => $team->id,
            'token' => Str::random(48),
            'created_by' => $creator->id,
            'expires_at' => Carbon::now()->addHours(48),
        ]);
    }
}
