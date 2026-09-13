package com.qaguard

import android.content.Context
import android.content.SharedPreferences

/** 仅存于本机的少量状态（无任何出网行为）。 */
object Store {

    private var prefs: SharedPreferences? = null

    fun initOnce(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences("guard", Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences = prefs ?: error("Store 未初始化")

    var lastCheckAt: Long
        get() = p().getLong("last_check_at", 0L)
        set(v) = p().edit().putLong("last_check_at", v).apply()

    var blockedCount: Int
        get() = p().getInt("blocked_count", 0)
        set(v) = p().edit().putInt("blocked_count", v).apply()

    /** 自动守护：发现引擎即停用（默认开）。 */
    var autoProtect: Boolean
        get() = p().getBoolean("auto_protect", true)
        set(v) = p().edit().putBoolean("auto_protect", v).apply()

    /** 无障碍弹窗拦截总开关。 */
    var a11yEnabled: Boolean
        get() = p().getBoolean("a11y_enabled", true)
        set(v) = p().edit().putBoolean("a11y_enabled", v).apply()

    /** 首次保护生效后的知情说明已展示（只弹一次）。 */
    var informedShown: Boolean
        get() = p().getBoolean("informed_shown", false)
        set(v) = p().edit().putBoolean("informed_shown", v).apply()

    /** 广告组件拦截（实验性）：厂商广告投放组件默认不拦截，用户显式打开后才纳入自动处置。 */
    var vendorAdGuard: Boolean
        get() = p().getBoolean("vendor_ad_guard", false)
        set(v) = p().edit().putBoolean("vendor_ad_guard", v).apply()
}
