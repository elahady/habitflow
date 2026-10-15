<?php

namespace App\Livewire;

use App\Models\Habit;
use App\Models\Todo;
use App\Support\HabitStats;
use App\Support\UserDay;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Halaman web untuk habit + to-do harian (tahap 28), kartu ringkas, streak, dan heatmap (tahap 33 langkah 2,
 * lihat [HabitStats]). Satu halaman: checklist habit hari ini + to-do, kontribusi, dan kelola habit.
 */
class HabitToday extends Component
{
    public string $newHabitName = '';

    public string $newTodoTitle = '';

    public array $editingName = [];

    private function today(): string
    {
        return UserDay::today(auth()->user());
    }

    private function habit(int $habitId): Habit
    {
        return auth()->user()->habits()->findOrFail($habitId);
    }

    public function mount(): void
    {
        // Carry-over: to-do belum selesai dari tanggal lampau pindah ke hari ini (tahap 3c).
        auth()->user()->todos()
            ->where('done', false)
            ->whereDate('date', '<', $this->today())
            ->update(['date' => $this->today()]);
    }

    public function addHabit(): void
    {
        $this->validate(['newHabitName' => ['required', 'string', 'max:255']]);

        $nextOrder = (int) (auth()->user()->habits()->max('sort_order') ?? 0) + 1;

        auth()->user()->habits()->create([
            'name' => $this->newHabitName,
            'sort_order' => $nextOrder,
        ]);

        $this->newHabitName = '';
    }

    public function startEditing(int $habitId): void
    {
        // Nama dibaca di server, bukan dikirim dari view: nama berisi tanda kutip merusak ekspresi wire:click.
        $this->editingName[$habitId] = $this->habit($habitId)->name;
    }

    public function saveName(int $habitId): void
    {
        $name = trim($this->editingName[$habitId] ?? '');
        if ($name !== '') {
            $this->habit($habitId)->update(['name' => $name]);
        }
        unset($this->editingName[$habitId]);
    }

    public function toggleMandatory(int $habitId): void
    {
        $habit = $this->habit($habitId);
        $habit->update(['is_mandatory' => ! $habit->is_mandatory]);
    }

    public function deleteHabit(int $habitId): void
    {
        $habit = $this->habit($habitId);
        if (! $habit->is_mandatory) {
            $habit->delete();
        }
    }

    public function toggleHabit(int $habitId): void
    {
        $habit = $this->habit($habitId);
        $entry = $habit->entries()->whereDate('date', $this->today())->first();

        $entry ? $entry->delete() : $habit->entries()->create(['date' => $this->today()]);
    }

    public function addTodo(): void
    {
        $title = trim($this->newTodoTitle);
        if ($title === '') {
            return;
        }

        $count = auth()->user()->todos()->whereDate('date', $this->today())->count();
        if ($count >= 5) {
            return;
        }

        auth()->user()->todos()->create(['title' => $title, 'date' => $this->today()]);
        $this->newTodoTitle = '';
    }

    public function toggleTodo(int $todoId): void
    {
        $todo = auth()->user()->todos()->findOrFail($todoId);
        $todo->update(['done' => ! $todo->done]);
    }

    public function deleteTodo(int $todoId): void
    {
        auth()->user()->todos()->findOrFail($todoId)->delete();
    }

    public function render(): View
    {
        $habits = auth()->user()->habits()
            ->orderByDesc('is_mandatory')
            ->orderBy('sort_order')
            ->orderBy('id')
            ->with(['entries' => fn ($q) => $q->whereDate('date', $this->today())])
            ->get();

        $todos = auth()->user()->todos()->whereDate('date', $this->today())->orderBy('id')->get();

        $user = auth()->user();
        $today = $this->today();

        return view('livewire.habit-today', [
            'habits' => $habits,
            'todos' => $todos,
            'today' => $today,
            'dateLabel' => UserDay::now($user)->locale('id')->translatedFormat('l, j F Y'),
            'doneHabits' => $habits->filter(fn ($habit) => $habit->entries->isNotEmpty())->count(),
            'stats' => HabitStats::for($user, $today),
            'miniStart' => HabitStats::heatmapStart($today, HabitStats::MINI_WEEKS),
        ])->layout('components.layout', ['title' => 'Habit']);
    }
}
