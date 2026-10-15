<?php

namespace App\Livewire;

use App\Models\FollowUp;
use App\Support\EodSummary;
use App\Support\UserDay;
use App\Support\WorkRecap;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;
use Livewire\Attributes\Url;
use Livewire\Component;

/**
 * Halaman Kerja web: rekap harian (tahap 33 langkah 3, mockup `KerjaWeb.dc.html`) di atas mesin follow-up
 * tahap 27: catat cepat ke Inbox, rapikan, ubah, selesaikan, hapus. Mengubah lewat form dicek konfliknya seperti
 * PUT /api/v1/follow-ups: `editingStamp` adalah `updated_at` saat form dibuka, dan kalau di server sudah berbeda,
 * perubahan tidak ditimpa diam-diam - user disuruh memilih (timpa dengan punyaku, atau buang dan pakai yang terbaru).
 * Catatan EOD (`work_days`) memakai pola yang sama lewat `eodStamp`. Catatan meeting disimpan di `daily_briefs`
 * (kolom yang tidak pernah ditulis skill rekap), jadi tidak bisa bentrok.
 */
class FollowUps extends Component
{
    private const HISTORY_STEP = 10;

    public string $newTitle = '';

    public ?int $editingId = null;

    public ?string $editingStamp = null;

    public array $form = [];

    /** Versi server saat bentrok terdeteksi; null kalau tidak ada bentrok. */
    public ?array $conflict = null;

    /** Tanggal rekap terpilih (`Y-m-d`); kosong atau tidak sah berarti hari ini. Ikut di URL supaya bisa dibagikan. */
    #[Url(as: 'tanggal')]
    public ?string $date = null;

    #[Url(as: 'filter')]
    public string $filter = 'all';

    public int $historyLimit = self::HISTORY_STEP;

    public string $eodNote = '';

    /** `updated_at` baris `work_days` saat dimuat; null kalau belum ada barisnya. */
    public ?string $eodStamp = null;

    /** Catatan di server saat bentrok EOD terdeteksi; null kalau tidak ada bentrok. */
    public ?string $eodConflict = null;

    public string $meetingNote = '';

    public string $pickTomorrowId = '';

    private function followUp(int $id): ?FollowUp
    {
        return auth()->user()->followUps()->find($id);
    }

    private function today(): string
    {
        return UserDay::today(auth()->user());
    }

    /** Tanggal terpilih yang sudah dinormalkan: tidak sah atau di masa depan jatuh ke hari ini. */
    private function selectedDate(): string
    {
        $today = $this->today();
        $valid = is_string($this->date)
            && preg_match('/^\d{4}-\d{2}-\d{2}$/', $this->date)
            && checkdate((int) substr($this->date, 5, 2), (int) substr($this->date, 8, 2), (int) substr($this->date, 0, 4));

        return $valid && $this->date <= $today ? $this->date : $today;
    }

    public function mount(): void
    {
        $this->date = $this->selectedDate();
        $this->loadNotes();
    }

    public function selectDate(string $date): void
    {
        $this->date = $date;
        $this->date = $this->selectedDate();
        $this->filter = 'all';
        $this->cancel();
        $this->loadNotes();
    }

    public function setFilter(string $filter): void
    {
        $this->filter = array_key_exists($filter, WorkRecap::FILTERS) ? $filter : 'all';
    }

    public function loadOlder(): void
    {
        $this->historyLimit += self::HISTORY_STEP;
    }

    private function loadNotes(): void
    {
        $user = auth()->user();
        $workDay = $user->workDays()->where('date', $this->date)->first();
        $this->eodNote = $workDay?->eod_note ?? '';
        $this->eodStamp = $workDay?->updated_at?->toIso8601String();
        $this->eodConflict = null;
        $this->meetingNote = $user->dailyBriefs()->where('date', $this->date)->value('meeting_note') ?? '';
    }

    public function add(): void
    {
        $title = trim($this->newTitle);
        if ($title === '') {
            return;
        }

        $this->validate(['newTitle' => ['string', 'max:255']]);

        auth()->user()->followUps()->create(['title' => $title]);
        $this->newTitle = '';
    }

    public function edit(int $id): void
    {
        $item = $this->followUp($id);
        if (! $item) {
            return;
        }

        $this->editingId = $id;
        $this->editingStamp = $item->updated_at->toIso8601String();
        $this->form = $this->formFrom($item);
        $this->conflict = null;
        $this->resetValidation();
    }

    public function cancel(): void
    {
        $this->reset('editingId', 'editingStamp', 'form', 'conflict');
        $this->resetValidation();
    }

    public function save(): void
    {
        $this->validate([
            'form.title' => ['required', 'string', 'max:255'],
            'form.status' => ['required', Rule::in(FollowUp::STATUSES)],
            'form.date' => ['nullable', 'date'],
            'form.time' => ['nullable', 'date_format:H:i'],
            'form.person' => ['nullable', 'string', 'max:255'],
            'form.note' => ['nullable', 'string', 'max:5000'],
        ]);

        $item = $this->editingId ? $this->followUp($this->editingId) : null;
        if (! $item) {
            $this->cancel();

            return;
        }

        if (Carbon::parse($this->editingStamp)->ne($item->updated_at)) {
            $this->conflict = $this->formFrom($item) + ['updated_at' => $item->updated_at->format('d M Y H:i:s')];

            return;
        }

        $this->apply($item);
        $this->cancel();
    }

    /** Pilihan "timpa dengan punyaku" di dialog bentrok. */
    public function overwrite(): void
    {
        $item = $this->editingId ? $this->followUp($this->editingId) : null;
        if ($item) {
            $this->apply($item);
        }
        $this->cancel();
    }

    /** Pilihan "pakai yang terbaru": buang perubahan di form, yang di server dipertahankan. */
    public function useLatest(): void
    {
        $this->cancel();
    }

    public function markDone(int $id): void
    {
        $this->followUp($id)?->update(['status' => 'DONE', 'done_at' => now()]);
    }

    public function reopen(int $id): void
    {
        $this->followUp($id)?->update(['status' => 'ACTIVE', 'done_at' => null]);
    }

    public function delete(int $id): void
    {
        $this->followUp($id)?->delete();

        if ($this->editingId === $id) {
            $this->cancel();
        }
    }

    // --- Rencana besok: follow-up yang dipilih untuk besok (`picked_date`), sama dengan "Lanjut besok" di EOD Android ---

    private function tomorrow(): string
    {
        return Carbon::parse($this->today())->addDay()->toDateString();
    }

    /** Batalkan pilihan untuk besok. Status dan tanggal follow-up tidak diubah. */
    public function removeFromTomorrow(int $id): void
    {
        $this->followUp($id)?->update(['picked_date' => null]);
    }

    /** Pilih follow-up untuk besok. Item Inbox sekalian dirapikan jadi Aktif tanpa tanggal, seperti merapikan Inbox di EOD. */
    public function addToTomorrow(): void
    {
        $item = $this->pickTomorrowId !== '' ? $this->followUp((int) $this->pickTomorrowId) : null;
        $this->pickTomorrowId = '';
        if (! $item || $item->status === 'DONE') {
            return;
        }

        $item->update(['picked_date' => $this->tomorrow()] + ($item->status === 'INBOX' ? ['status' => 'ACTIVE'] : []));
    }

    // --- Catatan EOD dan catatan meeting ---

    public function saveEodNote(): void
    {
        $this->validate(['eodNote' => ['nullable', 'string', 'max:5000']]);
        $this->persistEodNote(force: false);
    }

    /** Pilihan "timpa dengan punyaku" saat catatan EOD berubah di HP. */
    public function overwriteEodNote(): void
    {
        $this->persistEodNote(force: true);
    }

    /** Pilihan "pakai yang terbaru": catatan di server dimuat, yang diketik dibuang. */
    public function useLatestEodNote(): void
    {
        $this->loadNotes();
    }

    private function persistEodNote(bool $force): void
    {
        $user = auth()->user();
        $note = trim($this->eodNote);
        $existing = $user->workDays()->where('date', $this->date)->first();

        if (! $existing) {
            if ($note !== '') {
                $user->workDays()->create(['date' => $this->date, 'eod_note' => $note]);
            }
        } else {
            $stale = $this->eodStamp === null || Carbon::parse($this->eodStamp)->ne($existing->updated_at);
            if ($stale && ! $force) {
                $this->eodConflict = $existing->eod_note ?? '';

                return;
            }
            $existing->update(['eod_note' => $note === '' ? null : $note]);
        }

        $this->loadNotes();
    }

    public function saveMeetingNote(): void
    {
        $this->validate(['meetingNote' => ['nullable', 'string', 'max:20000']]);
        $note = trim($this->meetingNote);
        $user = auth()->user();

        if ($note === '' && ! $user->dailyBriefs()->where('date', $this->date)->exists()) {
            return;
        }

        $user->dailyBriefs()->updateOrCreate(['date' => $this->date], ['meeting_note' => $note === '' ? null : $note]);
    }

    private function formFrom(FollowUp $item): array
    {
        return [
            'title' => $item->title,
            'status' => $item->status,
            'date' => $item->date?->format('Y-m-d') ?? '',
            'time' => $item->time ? substr($item->time, 0, 5) : '',
            'person' => $item->person ?? '',
            'note' => $item->note ?? '',
        ];
    }

    private function apply(FollowUp $item): void
    {
        $blankToNull = fn (string $key) => ($this->form[$key] ?? '') === '' ? null : $this->form[$key];
        $status = $this->form['status'];

        $item->update([
            'title' => trim($this->form['title']),
            'status' => $status,
            'date' => $blankToNull('date'),
            'time' => $blankToNull('time'),
            'person' => $blankToNull('person'),
            'note' => $blankToNull('note'),
            'done_at' => $status === 'DONE' ? ($item->done_at ?? now()) : null,
        ]);
    }

    public function render(): View
    {
        $user = auth()->user();
        $today = $this->today();
        $date = $this->selectedDate();
        $recap = WorkRecap::for($user, $date, $today);
        $filter = array_key_exists($this->filter, WorkRecap::FILTERS) ? $this->filter : 'all';
        $workDay = $user->workDays()->where('date', $date)->first();
        $timezone = UserDay::timezone($user);

        $shareText = EodSummary::build(
            $date,
            $recap['items']['done'],
            $recap['tomorrow'],
            $recap['items']['waiting'],
            $this->eodNote,
        );

        $history = WorkRecap::history($user, $today, $this->historyLimit);

        return view('livewire.follow-ups', [
            'today' => $today,
            'selected' => $date,
            'dateLabel' => Carbon::parse($date)->locale('id')->translatedFormat('l, j F Y'),
            'recap' => $recap,
            'filter' => $filter,
            'rows' => $recap['items'][$filter],
            'eodAt' => $workDay?->eod_done_at?->copy()->setTimezone($timezone),
            'history' => $history,
            'hasMoreHistory' => count($history) < WorkRecap::historyCount($user, $today),
            'pickable' => $recap['isToday']
                ? $user->followUps()->where('status', '!=', 'DONE')->orderBy('title')->get()
                    ->reject(fn (FollowUp $f) => $f->picked_date?->toDateString() === $this->tomorrow())
                : collect(),
            'shareText' => $shareText,
        ])->layout('components.layout', ['title' => 'Kerja', 'width' => '78rem']);
    }
}
