package com.qaguard.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 轻量定时复查：系统自带 AlarmManager + 非精确闹钟，
 * 不引入 WorkManager（省掉 SQLite 与任务调度栈），每 12 小时一次，几乎零耗电。
 */
object DailyGuard {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RecheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_DAY,
            AlarmManager.INTERVAL_HALF_DAY,
            pi
        )
    }
}
