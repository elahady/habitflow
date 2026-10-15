<?php

namespace App\Livewire;

use App\Models\FollowUp;
use App\Support\UserDay;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;
use Livewire\Component;

/**
 * Halaman web follow-up kerja (tahap 27 langkah 2): catat cepat ke Inbox, rapikan, ubah, selesaikan,
 * hapus. Mengubah lewat form dicek konfliknya seperti PUT /api/v1/follow-ups: `editingStamp` adalah
 * `updated_at` saat form dibuka, dan kalau di server sudah berbeda, perubahan tidak ditimpa diam-diam -
 * user disuruh memilih (timpa dengan punyaku, atau buang dan pakai yang terbaru).
 */
class FollowUps extends Component
{
    public string $newTitle = '';

    public ?int $editingId = null;

    public ?string $editingStamp = null;

    public array $form = [];

    /** Versi server saat bentrok terdeteksi; null kalau tidak ada bentrok. */
    public ?array $conflict = null;

    private function followUp(int $id): ?FollowUp
    {
        return auth()->user()->followUps()->find($id);
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
        $all = auth()->user()->followUps()
            ->orderByRaw('date is null')
            ->orderBy('date')
            ->orderBy('id')
            ->get();

        return view('livewire.follow-ups', [
            'today' => UserDay::today(auth()->user()),
            'sections' => [
                'INBOX' => ['Inbox', $all->where('status', 'INBOX')],
                'ACTIVE' => ['Aktif', $all->where('status', 'ACTIVE')],
                'WAITING' => ['Menunggu', $all->where('status', 'WAITING')],
                'DONE' => ['Selesai', $all->where('status', 'DONE')->sortByDesc('done_at')->take(20)],
            ],
        ])->layout('components.layout', ['title' => 'Follow-up Kerja']);
    }
}
