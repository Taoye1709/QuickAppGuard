package com.qaguard.detect

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.qaguard.model.DetectedEngine

/**
 * 快应用框架检测器。三级策略，逐级兜底：
 *  1. 特征库精确匹配（<queries> 按包名声明，无需任何宽权限）
 *  2. hap:// scheme 启发式：响应快应用 deeplink 协议的包几乎必然是引擎
 *  3. 已安装包名深度扫描（依赖 QUERY_ALL_PACKAGES，正常安装即授予）：
 *     按 hybrid/quickapp/qapp/hapjs 关键词匹配，捕获 1、2 都漏掉的变体。
 *     该权限与"不上架商店、家人侧载"的定位绑定；若未来要上架必须删除此级
 *     （见 README「已知限制」），届时 1、2 级仍可工作，只是识别面收窄。
 */
class Detector(
    private val pm: PackageManager,
    private val selfPackage: String = ""
) {

    private val suspicious = Regex("hybrid|quickapp|qapp|hapjs")

    fun scan(): List<DetectedEngine> {
        val found = LinkedHashMap<String, DetectedEngine>()

        for (entry in EngineDatabase.entries) {
            if (isInstalled(entry.packageName)) {
                found[entry.packageName] = DetectedEngine(entry, entry.packageName, byHeuristic = false)
            }
        }

        val extras = hapHandlers() + suspiciousByPackageName()
        for (pkg in extras) {
            if (!found.containsKey(pkg) && EngineDatabase.find(pkg) == null) {
                found[pkg] = DetectedEngine(null, pkg, byHeuristic = true)
            }
        }

        return found.values.toList()
    }

    private fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }

    /** 当前声明响应 hap:// 协议的包列表（含已停用的引擎）。 */
    fun hapHandlers(): List<String> = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("hap://app/com.demo/pages/index")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != selfPackage }
            .distinct()
    } catch (t: Throwable) {
        emptyList()
    }

    /** 包名关键词启发式。无 QUERY_ALL_PACKAGES 时只返回 <queries> 声明过的包，自然降级。 */
    private fun suspiciousByPackageName(): List<String> = try {
        pm.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it != selfPackage && suspicious.containsMatchIn(it) }
            .distinct()
    } catch (t: Throwable) {
        emptyList()
    }
}
