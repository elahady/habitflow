package com.roziqrizal.habitflow.session

import android.content.Context
import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.LocalDataReset

/** [LocalDataReset] untuk app: tulis penanda hapus ([PendingWipe]) lalu mulai ulang proses ([ProcessRestarter]). */
class AndroidLocalDataReset(private val context: Context) : LocalDataReset {
    override fun scheduleWipe(thenSignIn: Account?) = AppDataWipe.pendingWipe(context).schedule(thenSignIn)

    override fun restartProcess() = ProcessRestarter.restart(context)
}
