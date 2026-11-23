package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.EasyPlanningDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    val dao = EasyPlanningDatabase.getInstance(appContext).dao()
                    val series = dao.getAllSeriesOnce()
                    val overrides = dao.getAllOverridesOnce()
                    ShiftReminderScheduler(appContext).rescheduleAll(series, overrides)
                }.onFailure { err ->
                    Log.w("BootRescheduleReceiver", "Failed to reschedule shift reminders on boot", err)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
