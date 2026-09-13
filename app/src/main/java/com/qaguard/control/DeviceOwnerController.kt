package com.qaguard.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.qaguard.admin.GuardAdminReceiver

/**
 * Device Owner 通道（推荐）：
 * 通过 ADB 一次性激活后，用 DevicePolicyManager 隐藏/恢复应用。
 * 优点：重启不失效、可同时锁定“未知来源安装”、无需常驻任何服务。
 */
class DeviceOwnerController(private val context: Context) : EngineController {

    override val modeName = "Device Owner"

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val admin = ComponentName(context, GuardAdminReceiver::class.java)

    override fun available(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    override fun reasonUnavailable(): String = "尚未激活 Device Owner，请先完成激活向导"

    override fun deactivate(pkg: String): kotlin.Result<Unit> {
        if (!available()) return kotlin.Result.failure(IllegalStateException(reasonUnavailable()))
        return try {
            val ok = dpm.setApplicationHidden(admin, pkg, true)
            if (ok) kotlin.Result.success(Unit) else suspendFallback(pkg)
        } catch (t: Throwable) {
            suspendFallback(pkg)
        }
    }

    /** 隐藏被系统拒绝时退级为挂起（部分关键组件不允许 hide 但允许 suspend）。 */
    private fun suspendFallback(pkg: String): kotlin.Result<Unit> = try {
        val failed = dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
        if (failed.isEmpty()) {
            kotlin.Result.success(Unit)
        } else {
            kotlin.Result.failure(IllegalStateException("系统拒绝隐藏/挂起 $pkg（可能为关键组件）"))
        }
    } catch (t: Throwable) {
        kotlin.Result.failure(IllegalStateException("系统拒绝隐藏 $pkg（可能为关键组件）", t))
    }

    override fun restore(pkg: String): kotlin.Result<Unit> {
        if (!available()) return kotlin.Result.failure(IllegalStateException(reasonUnavailable()))
        return try {
            dpm.setApplicationHidden(admin, pkg, false)
            try {
                dpm.setPackagesSuspended(admin, arrayOf(pkg), false)
            } catch (t: Throwable) {
                // 老版本系统无此 API 时忽略：未挂起即无需还原
            }
            kotlin.Result.success(Unit)
        } catch (t: Throwable) {
            kotlin.Result.failure(t)
        }
    }

    override fun isDeactivated(pkg: String): Boolean = try {
        if (!available()) {
            false
        } else {
            dpm.isApplicationHidden(admin, pkg) || context.packageManager.isPackageSuspended(pkg)
        }
    } catch (t: Throwable) {
        false
    }
}
