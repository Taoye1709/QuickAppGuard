package com.qaguard.monitor

import android.content.Context
import com.qaguard.Store
import com.qaguard.control.Controllers
import com.qaguard.detect.Detector

/** 一次“扫描→停用”的完整守护动作，供开机、定时、手动三处复用。 */
object GuardWorker {

    fun runOnce(context: Context) {
        val ctx = context.applicationContext
        Store.initOnce(ctx)
        Store.lastCheckAt = System.currentTimeMillis()

        val detector = Detector(ctx.packageManager, ctx.packageName)

        // 处置面求稳：快应用只处理官方/多方确认过的引擎；
        // 广告组件只在用户显式打开实验开关后纳入
        val actionable = detector.scan()
            .filter { it.entry?.verified == true && it.entry.coupled != true }
            .toMutableList()
        if (Store.vendorAdGuard) actionable += detector.scanAdComponents()

        if (actionable.isEmpty()) return

        // 自动守护关闭时只记录，不动手
        if (!Store.autoProtect) return

        val controllers = Controllers.all(ctx)
        for (engine in actionable) {
            var handled = false
            for (controller in controllers) {
                if (controller.isDeactivated(engine.packageName)) {
                    handled = true
                    break
                }
            }
            if (handled) continue
            // 通道 1 失败（如系统判定为关键组件）则换通道 2 重试
            for (controller in controllers) {
                if (controller.deactivate(engine.packageName).isSuccess) break
            }
        }
    }
}
