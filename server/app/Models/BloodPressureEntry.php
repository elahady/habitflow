<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Satu catatan tensi dari Android (tahap 30). Nadi dan catatan boleh kosong. */
class BloodPressureEntry extends Model
{
    protected $fillable = ['user_id', 'time_millis', 'systolic', 'diastolic', 'pulse', 'note'];

    protected function casts(): array
    {
        return ['time_millis' => 'integer', 'systolic' => 'integer', 'diastolic' => 'integer', 'pulse' => 'integer'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
