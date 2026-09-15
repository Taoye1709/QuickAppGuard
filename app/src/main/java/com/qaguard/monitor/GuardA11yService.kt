package com.qaguard.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.qaguard.Store
import com.qaguard.detect.Detector
import com.qaguard.detect.EngineDatabase
import com.qaguard.model.EngineCategory

/**
 * 无障碍兜底：对没有完成授权的机器，拦下“从其他应用里突然弹出的快应用/广告窗口”。
 *
 * 两条纪律：
 *  1. 上下文白名单：保护对象是“骚扰式弹出”，不是形态本身。桌面图标、负一屏/助手、
 *     快应用中心内主动打开的场景一律放行；判断只依据窗口切换事件的前后包名，不读内容。
 *     已知取舍：紧跟桌面动作后弹出的窗口会被放行（桌面图标必须可点），
 *     主要拦截目标是应用内弹出的广告链。
 *  2. 「广告组件拦截」实验开关的语义权威在自己：开关关闭时，
 *     厂商广告组件的窗口在本服务中绝不拦截，只作前台上下文记录。
 */
class GuardA11yService : AccessibilityService() {

    private var lastActionAt = 0L
    private var quickAppWatchlist: Set<String> = emptySet()
    private var adComponentWatchlist: Set<String> = emptySet()
    private var trustedContexts: Set<String> = emptySet()
    private var lastForeground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 快应用引擎：非耦合的特征库条目 + hap:// 启发式命中
        val quickApps = HashSet<String>()
        EngineDatabase.entries
            .filter { it.category == EngineCategory.QUICK_APP && !it.coupled }
            .forEach { quickApps.add(it.packageName) }
        try {
            quickApps.addAll(
                Detector(packageManager, packageName).hapHandlers()
                    .filter { pkg ->
                        val entry = EngineDatabase.find(pkg)
                        entry == null || (entry.category == EngineCategory.QUICK_APP && !entry.coupled)
                    }
            )
        } catch (t: Throwable) {
            // 无障碍进程内查询失败时，退回仅特征库
        }
        quickApps.remove(packageName)
        quickAppWatchlist = quickApps

        // 广告组件：单列名单，是否拦截由实验开关在每次事件时决定（拆掉开关立即生效）
        adComponentWatchlist = EngineDatabase.entries
            .filter { it.category == EngineCategory.VENDOR_AD && !it.coupled }
            .map { it.packageName }
            .toSet()

        trustedContexts = buildTrustedContexts()
    }

    /**
     * 主动使用场景：桌面、负一屏/助手、快应用生态自身界面（快应用中心等）。
     * 注意：广告组件故意不列作可信上下文——免得其窗口拉起快应用时被误判为“主动使用”。
     */
    private fun buildTrustedContexts(): Set<String> {
        val set = HashSet<String>(quickAppWatchlist)
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(home, 0).forEach {
                it.activityInfo?.packageName?.let { p -> set.add(p) }
            }
        } catch (t: Throwable) {
            // 拿不到桌面包名时，仅依赖快应用生态自身作为可信上下文
        }
        // 常见负一屏/助手包名；未覆盖的品牌若误拦会有提示，可在应用内关闭本兜底
        set.add("com.miui.personalassistant")
        set.add("com.coloros.assistantscreen")
        set.remove(packageName)
        return set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Store.a11yEnabled) return

        val pkg = event.packageName?.toString() ?: return

        val isEngine = pkg in quickAppWatchlist
        // 广告组件窗口：实验开关关闭时绝不拦截，仅作前台上下文记录
        val isAdComponent = pkg in adComponentWatchlist && Store.vendorAdGuard
        if (!isEngine && !isAdComponent) {
            lastForeground = pkg
            return
        }

        // 上下文白名单：前一个前台是桌面/负一屏/快应用生态界面 → 视为用户主动使用
        val prev = lastForeground
        if (prev != null && prev in trustedContexts) {
            lastForeground = pkg
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < 3000) return
        lastActionAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        // 拦截后清空上下文，防止同一广告链的下一个窗口被误判为“主动使用”
        lastForeground = null
        Store.blockedCount = Store.blockedCount + 1
        Toast.makeText(this, "净屏守护：已关闭广告弹窗", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        // 无需处理
    }
}
