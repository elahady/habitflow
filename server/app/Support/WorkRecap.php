<?php

namespace App\Support;

use App\Models\FollowUp;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;

/**
 * Rekap harian halaman Kerja web (tahap 33 langkah 3): follow-up dan angka ringkas untuk satu tanggal.
 *
 * Hari ini: angka ringkas dihitung dari yang dikerjakan hari ini (bagian Hari ini dan Lewat tanggal, sama
 * dengan daftar daily scrum di Android) dan yang selesai hari ini; daftar "Semua" memuat seluruh follow-up
 * terbuka, yang dikerjakan hari ini di atas, ditambah yang selesai hari ini. Tanggal lampau: hanya yang selesai pada
 * tanggal itu, karena server tidak menyimpan daftar hari itu sebelum EOD memindahkan item (Lanjut besok
 * mengubah `picked_date`, Pindah tanggal mengubah `date`). Angka "X dari Y" untuk tanggal lampau tidak
 * dibuat-buat: yang ditampilkan hanya jumlah selesai.
 */
class WorkRecap
{
    public const FILTERS = ['all' => 'Semua', 'inbox' => 'Inbox', 'active' => 'Aktif', 'waiting' => 'Menunggu', 'done' => 'Selesai'];

    /** Tanggal selesai [$item] di zona waktu [$timezone], atau null kalau belum selesai. */
    public static function doneDate(FollowUp $item, string $timezone): ?string
    {
        return $item->status === 'DONE' && $item->done_at
            ? $item->done_at->copy()->setTimezone($timezone)->toDateString()
            : null;
    }

    /**
     * @return array{
     *     date: string,
     *     isToday: bool,
     *     items: array<string, Collection<int, FollowUp>>,
     *     counts: array<string, int>,
     *     total: int,
     *     done: int,
     *     rate: int,
     *     waiting: int,
     *     tomorrow: Collection<int, FollowUp>,
     *     inbox: int,
     * }
     *   `items` berkunci nama filter. `total` hanya yang dikerjakan hari ini (tanpa Inbox); `rate` dalam persen.
     */
    public static function for(User $user, string $date, string $today): array
    {
        $timezone = UserDay::timezone($user);
        $all = $user->followUps()->orderByRaw('date is null')->orderBy('date')->orderBy('id')->get();
        $isToday = $date === $today;
        $tomorrowDate = Carbon::parse($today)->addDay()->toDateString();

        $doneOnDate = $all->filter(fn (FollowUp $f) => self::doneDate($f, $timezone) === $date)
            ->sortBy('done_at')->values();

        $open = $all->where('status', '!=', 'DONE');
        $inbox = $open->where('status', 'INBOX')->values();
        $active = $open->where('status', 'ACTIVE')->values();
        $waiting = $open->where('status', 'WAITING')->values();

        if ($isToday) {
            $workToday = $open->filter(fn (FollowUp $f) => in_array(
                FollowUpSections::for($f, $today),
                [FollowUpSections::TODAY, FollowUpSections::OVERDUE],
                true,
            ))->values();
            // "Semua": yang dikerjakan hari ini dulu, lalu sisa yang terbuka (Nanti dan Menunggu), Inbox, dan yang selesai hari ini.
            // Yang tanpa tanggal atau jatuh tempo nanti tidak boleh hilang dari daftar hanya karena bukan pekerjaan hari ini.
            $workIds = $workToday->pluck('id')->all();
            $rest = $open->where('status', '!=', 'INBOX')->reject(fn (FollowUp $f) => in_array($f->id, $workIds, true));
            $everything = $workToday->concat($rest)->concat($inbox)->concat($doneOnDate)->values();
            $total = $workToday->count() + $doneOnDate->count();
        } else {
            $workToday = collect();
            $everything = $doneOnDate;
            $active = $waiting = $inbox = collect();
            $total = $doneOnDate->count();
        }

        $items = ['all' => $everything, 'inbox' => $inbox, 'active' => $active, 'waiting' => $waiting, 'done' => $doneOnDate];
        $tomorrow = $isToday
            ? $open->filter(fn (FollowUp $f) => $f->picked_date?->toDateString() === $tomorrowDate)->values()
            : collect();

        return [
            'date' => $date,
            'isToday' => $isToday,
            'items' => $items,
            'counts' => array_map(fn (Collection $c) => $c->count(), $items),
            'total' => $total,
            'done' => $doneOnDate->count(),
            'rate' => $total > 0 ? (int) round($doneOnDate->count() / $total * 100) : 0,
            'waiting' => $workToday->where('status', 'WAITING')->count(),
            'tomorrow' => $tomorrow,
            'inbox' => $isToday ? $inbox->count() : 0,
        ];
    }

    /**
     * Tanggal untuk daftar riwayat, terbaru dulu: hari ini selalu ada, lalu tanggal yang punya catatan atau follow-up selesai.
     *
     * @return list<array{date: string, done: int, eodAt: ?Carbon}>
     */
    public static function history(User $user, string $today, int $limit): array
    {
        $timezone = UserDay::timezone($user);
        $doneByDate = $user->followUps()->where('status', 'DONE')->whereNotNull('done_at')->get()
            ->countBy(fn (FollowUp $f) => self::doneDate($f, $timezone))->all();
        $workDays = $user->workDays()->get()->keyBy('date');

        $dates = array_unique([$today, ...array_keys($doneByDate), ...$workDays->keys()->all()]);
        $dates = array_values(array_filter($dates, fn ($d) => $d <= $today));
        rsort($dates);

        return array_map(fn (string $d) => [
            'date' => $d,
            'done' => $doneByDate[$d] ?? 0,
            'eodAt' => $workDays->get($d)?->eod_done_at?->copy()->setTimezone($timezone),
        ], array_slice($dates, 0, $limit));
    }

    /** Berapa tanggal riwayat yang ada seluruhnya (untuk tombol "Muat lebih lama"). */
    public static function historyCount(User $user, string $today): int
    {
        return count(self::history($user, $today, PHP_INT_MAX));
    }
}
