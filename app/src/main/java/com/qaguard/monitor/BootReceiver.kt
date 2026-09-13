package com.qaguard.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机即复查：厂商 OTA 常把引擎装回或重置，这里兜底。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DailyGuard.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                GuardWorker.runOnce(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
