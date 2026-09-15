package com.qaguard.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * v0.4 借鉴 Brevent 的自愈机制：
 * 机主经 `adb shell pm grant` 授予 WRITE_SECURE_SETTINGS 后，
 * 开机与定时复查时恢复无线调试开关（并移除闲置自动断开），
 * 使 Shizuku 通道在重启后可自愈，老人无需任何操作。
 *
 * 安全边界：只恢复无线调试（局域网内使用），绝不打开 USB adb——
 * 老人设备保持物理调试口关闭更安全。连接仍受 ADB 密钥配对保护。
 */
object AdbSelfHeal {

    private const val PERM = Manifest.permission.WRITE_SECURE_SETTINGS

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** 恢复无线调试设置；返回是否实际执行了恢复。 */
    fun run(context: Context): Boolean {
        if (!granted(context)) return false
        return try {
            val resolver = context.contentResolver
            Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)
            // 0 = 移除无线调试的闲置自动断开时间（Brevent 同款做法）
            Settings.Global.putInt(resolver, "adb_allowed_connection_time", 0)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
