package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.WorkRepository
import com.roziqrizal.habitflow.domain.work.EodAction
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.InboxChoice
import com.roziqrizal.habitflow.domain.work.WorkDay
import com.roziqrizal.habitflow.domain.work.WorkSection
import com.roziqrizal.habitflow.domain.work.allPeople
import com.roziqrizal.habitflow.domain.work.applyEod
import com.roziqrizal.habitflow.domain.work.applyInbox
import com.roziqrizal.habitflow.domain.work.eodItems
import com.roziqrizal.habitflow.domain.work.forPerson
import com.roziqrizal.habitflow.domain.work.groupedBySection
import com.roziqrizal.habitflow.domain.work.markDone
import com.roziqrizal.habitflow.domain.work.pickedForToday
import com.roziqrizal.habitflow.domain.work.scrumCandidates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WorkUiState(
    val today: LocalDate,
    /** Semua follow-up terbuka dan selesai, tanpa filter. Dipakai untuk saran nama. */
    val all: List<FollowUp> = emptyList(),
    /** Kelompok tab Kerja, sudah difilter per orang. */
    val sections: Map<WorkSection, List<FollowUp>> = emptyMap(),
    val people: List<String> = emptyList(),
    val selectedPerson: String? = null,
    /** Jumlah per kelompok tanpa filter, untuk kartu ringkasan di Hari ini. */
    val inboxCount: Int = 0,
    val overdueCount: Int = 0,
    val todayCount: Int = 0,
    val scrumCandidates: List<FollowUp> = emptyList(),
    val scrumLater: List<FollowUp> = emptyList(),
    val eodToday: List<FollowUp> = emptyList(),
    val inbox: List<FollowUp> = emptyList(),
    val workDay: WorkDay? = null,
)

/** Filter orang disimpan di ViewModel, jadi bertahan selama app hidup. */
class WorkViewModel(
    private val repo: WorkRepository,
    private val clock: DayClock,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val selectedPerson = MutableStateFlow<String?>(null)

    val state: StateFlow<WorkUiState> = combine(
        repo.observeFollowUps(),
        repo.observeWorkDays(),
        clock.date,
        selectedPerson,
    ) { items, days, today, person ->
        val unfiltered = items.groupedBySection(today)
        val people = allPeople(items)
        // Filter orang yang sudah tidak punya follow-up apa pun dilepas supaya daftar tidak kosong terus.
        val effectivePerson = person?.takeIf { p -> people.any { it.equals(p, ignoreCase = true) } }
        val sections = items.forPerson(effectivePerson).groupedBySection(today)
        val candidates = scrumCandidates(items, today)
        val candidateIds = candidates.map { it.id }.toSet()
        WorkUiState(
            today = today,
            all = items,
            sections = sections,
            people = people,
            selectedPerson = effectivePerson,
            inboxCount = unfiltered[WorkSection.INBOX].orEmpty().size,
            overdueCount = unfiltered[WorkSection.OVERDUE].orEmpty().size,
            todayCount = unfiltered[WorkSection.TODAY].orEmpty().size,
            scrumCandidates = candidates,
            scrumLater = (unfiltered[WorkSection.LATER].orEmpty() + unfiltered[WorkSection.WAITING].orEmpty())
                .filter { it.id !in candidateIds },
            eodToday = eodItems(items, today),
            inbox = unfiltered[WorkSection.INBOX].orEmpty(),
            workDay = days[today],
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkUiState(today = clock.date.value),
    )

    fun selectPerson(person: String?) {
        selectedPerson.value = person
    }

    /** Catat cepat ke Inbox. Judul kosong diabaikan. */
    fun quickAdd(title: String) {
        viewModelScope.launch { repo.quickAdd(title, nowMillis()) }
    }

    fun save(item: FollowUp) {
        viewModelScope.launch { repo.save(item, nowMillis()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun setDone(item: FollowUp) {
        viewModelScope.launch { repo.save(item.markDone(nowMillis()), nowMillis()) }
    }

    /** Pilih atau batalkan follow-up untuk dikerjakan hari ini (daily scrum). */
    fun pickForToday(item: FollowUp, picked: Boolean) {
        viewModelScope.launch { repo.save(item.pickedForToday(picked, clock.date.value), nowMillis()) }
    }

    fun completeScrum() {
        viewModelScope.launch { repo.completeScrum(clock.date.value, nowMillis()) }
    }

    /**
     * Simpan hasil EOD: status setiap follow-up hari ini ([actions]), merapikan Inbox ([inboxChoices]),
     * lalu catatan EOD. Item yang pilihannya tidak ada tetap seperti semula.
     */
    fun completeEod(
        today: List<FollowUp>,
        actions: Map<Long, EodAction>,
        inbox: List<FollowUp>,
        inboxChoices: Map<Long, InboxChoice>,
        note: String,
    ) {
        val date = clock.date.value
        viewModelScope.launch {
            val now = nowMillis()
            repo.saveAll(today.map { item -> actions[item.id]?.let { item.applyEod(it, date, now) } ?: item })
            inbox.forEach { item ->
                val choice = inboxChoices[item.id] ?: InboxChoice.Keep
                val result = item.applyInbox(choice)
                if (result == null) repo.delete(item.id) else if (result != item) repo.save(result, now)
            }
            repo.completeEod(date, note, now)
        }
    }
}
