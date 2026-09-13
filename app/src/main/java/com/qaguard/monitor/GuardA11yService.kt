package com.qaguard.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.qaguard.Store
import com.qaguard.detect.Detector
import com.qaguard.detect.EngineDatabase

/**
 * 无障碍兜底：对没有完成授权的机器，拦下“从其他应用里突然弹出的快应用窗口”。
 *
 * 上下文白名单：保护对象是“骚扰式弹出”，不是快应用这个形态本身。
 * 桌面图标、负一屏/助手、快应用中心内主动打开的场景一律放行；
 * 判断只依据窗口切换事件的前后包名，不读取窗口内容。
 */
class GuardA11yService : AccessibilityService() {

    private var lastActionAt = 0L
    private var watchlist: Set<String> = emptySet()
    private var trustedContexts: Set<String> = emptySet()
    private var lastForeground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        watchlist = buildWatchlist()
        trustedContexts = buildTrustedContexts()
    }

    private fun buildWatchlist(): Set<String> {
        val pkgs = HashSet<String>()
        // 红线：耦合型组件（华为/荣耀应用市场）绝不纳入拦截，
        // 否则无障碍会把应用商店的每一次窗口切换都按回桌面
        EngineDatabase.entries.filter { !it.coupled }.forEach { pkgs.add(it.packageName) }
        try {
            pkgs.addAll(
                Detector(packageManager, packageName).hapHandlers()
                    .filter { EngineDatabase.find(it)?.coupled != true }
            )
        } catch (t: Throwable) {
            // 无障碍进程内查询失败时，退回仅特征库
        }
        pkgs.remove(packageName)
        return pkgs
    }

    /** 主动使用场景：桌面、负一屏/助手、以及快应用生态自身界面（快应用中心等）。 */
    private fun buildTrustedContexts(): Set<String> {
        val set = HashSet<String>(watchlist)
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

        // 非快应用窗口：只更新前台上下文
        if (pkg !in watchlist) {
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
