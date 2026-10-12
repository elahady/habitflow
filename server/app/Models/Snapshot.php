<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Snapshot extends Model
{
    protected $fillable = [
        'api_token_id', 'user_id', 'device_id', 'schema_version', 'bytes', 'sha256', 'path', 'client_created_at',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /** Token per-HP tahap 19B yang dulu memilikinya. Kosong untuk snapshot yang dibuat sejak 31E. */
    public function token(): BelongsTo
    {
        return $this->belongsTo(ApiToken::class, 'api_token_id');
    }
}
