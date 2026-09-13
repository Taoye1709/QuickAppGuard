package com.qaguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.view.accessibility.AccessibilityManager
import com.qaguard.admin.GuardAdminReceiver

/** 查询本应用的无障碍服务是否已在系统中开启。 */
object A11yUtil {

    fun enabled(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    } catch (t: Throwable) {
        false
    }
}

/**
 * “未知来源安装”锁定（Device Owner 限定）：
 * 把“广告一点就自动下载安装”的整条链条掐断。
 * 注意：不拦截 adb install，子女远程装应用不受影响。
 */
object InstallLock {

    fun set(context: Context, locked: Boolean): kotlin.Result<Unit> = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, GuardAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            kotlin.Result.failure(IllegalStateException("需要 Device Owner 权限"))
        } else {
            dpm.setUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, locked)
            kotlin.Result.success(Unit)
        }
    } catch (t: Throwable) {
        kotlin.Result.failure(t)
    }

    fun isEnabled(context: Context): Boolean = try {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        um.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    } catch (t: Throwable) {
        false
    }
}
