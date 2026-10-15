package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.UnauthorizedListener
import com.roziqrizal.habitflow.domain.work.DailyBrief
import com.roziqrizal.habitflow.domain.work.pickDailyBrief
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Sumber ringkasan harian di server; dipisah supaya [DailyBriefRepository] bisa diuji tanpa jaringan. */
fun interface DailyBriefSource {
    suspend fun listDailyBriefs(): HabitApiResult<List<DailyBrief>>
}

/**
 * Menarik ringkasan harian dari server ke [DailyBriefStore] (tahap 32). Satu arah: tidak ada yang dikirim dari HP.
 * Kegagalan jaringan atau server membiarkan cache apa adanya, jadi kartu tidak berkedip kosong saat sinyal jelek.
 */
class DailyBriefRepository(
    private val accountSettings: AccountSettings,
    private val store: DailyBriefStore,
    unauthorized: UnauthorizedListener,
    private val sourceFor: (token: String) -> DailyBriefSource = { HabitSyncClient(it, unauthorized = unauthorized) },
) {
    /** Ringkasan yang tersimpan di HP, untuk diamati UI. */
    val briefs: StateFlow<List<DailyBrief>> get() = store.briefs

    /**
     * [today] dipakai untuk memilih ringkasan yang paling baru yang tidak bertanggal di depan. Mengembalikan hasil
     * panggilan server supaya pemanggil bisa membedakan gagal dari "tidak ada ringkasan".
     */
    suspend fun refresh(today: LocalDate): HabitApiResult<Unit> {
        val token = accountSettings.account.value?.token ?: return HabitApiResult.Unauthorized
        return when (val result = sourceFor(token).listDailyBriefs()) {
            is HabitApiResult.Success -> {
                store.save(pickDailyBrief(result.value, today).brief)
                HabitApiResult.Success(Unit)
            }
            is HabitApiResult.Unauthorized -> HabitApiResult.Unauthorized
            is HabitApiResult.Rejected -> HabitApiResult.Rejected(result.detail)
            is HabitApiResult.NotFound -> HabitApiResult.NotFound
            is HabitApiResult.Conflict -> HabitApiResult.ServerError(409)
            is HabitApiResult.ServerError -> result
            is HabitApiResult.NetworkError -> result
        }
    }
}
