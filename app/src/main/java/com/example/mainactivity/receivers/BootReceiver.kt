package com.example.mainactivity.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mainactivity.data.SessionManager
import com.example.mainactivity.workers.NotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SessionManager.get(context.applicationContext).notificationsEnabled.first()
                if (enabled) NotificationWorker.schedule(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
