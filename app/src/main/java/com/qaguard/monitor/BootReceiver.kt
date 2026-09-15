package com.qaguard.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机即复查：厂商 OTA 常把引擎装回或重置，这里兜底；并自愈无线调试让 Shizuku 恢复。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DailyGuard.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                // 先恢复无线调试（Shizuku 才有机会自启），再做引擎复查
                AdbSelfHeal.run(context)
                GuardWorker.runOnce(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
