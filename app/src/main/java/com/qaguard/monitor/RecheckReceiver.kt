package com.qaguard.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
