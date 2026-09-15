package com.qaguard

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qaguard.control.ShizukuController

/**
 * 体检中心：把“不进自动处置管线、但能查能引导”的骚扰来源集中在这里。
 *
 * v0.2 覆盖：悬浮窗权限（“盖住屏幕”类广告的来源，含拨号界面被遮挡场景）+ 通知指引。
 * v0.4 升级（借鉴 Brevent 的 appops 思路）：Shizuku 可用时直接
 * `appops set <pkg> SYSTEM_ALERT_WINDOW deny` 一键拒绝，恢复走 default（回到系统默认，
 * 不给 targeting 29+ 的应用意外授权）。
 */
class CheckupActivity : AppCompatActivity() {

    private val shizuku by lazy { ShizukuController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkup)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.ll_overlay_list)
        container.removeAllViews()

        val holders = try {
            packageManager.getPackagesHoldingPermissions(
                arrayOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW), 0
            ).map { it.packageName }
        } catch (t: Throwable) {
            emptyList()
        }

        val launchers = try {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (t: Throwable) {
            emptySet<String>()
        }

        val offenders = holders
            .filter { it != packageName && it != "com.android.systemui" && it !in launchers }
            .sorted()

        val tvIntro = findViewById<TextView>(R.id.tv_overlay_intro)
        tvIntro.text = if (offenders.isEmpty()) {
            getString(R.string.checkup_overlay_clean)
        } else {
            getString(R.string.checkup_overlay_found)
        }

        for (pkg in offenders) {
            container.addView(buildRow(pkg))
        }
    }

    /** 悬浮窗的 appops 模式：允许/已拒绝/默认（拒绝 = 应用无法在其他界面之上弹窗）。 */
    private fun modeOf(pkg: String): Int = try {
        val uid = packageManager.getPackageUid(pkg, 0)
        val am = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        am.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
    } catch (t: Throwable) {
        AppOpsManager.MODE_DEFAULT
    }

    private fun modeLabel(mode: Int): String = when (mode) {
        AppOpsManager.MODE_ALLOWED -> "悬浮窗：允许"
        AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED -> "悬浮窗：已拒绝"
        else -> "悬浮窗：默认"
    }

    private fun buildRow(pkg: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val tv = TextView(this).apply {
            text = "$pkg（${modeLabel(modeOf(pkg))}）"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setupButton(this, pkg)
        }
        row.addView(tv)
        row.addView(btn)
        return row
    }

    private fun setupButton(btn: Button, pkg: String) {
        if (shizuku.available()) {
            // Shizuku 可用：一键 appops 拒绝 / 恢复默认
            val denied = modeOf(pkg) != AppOpsManager.MODE_ALLOWED &&
                modeOf(pkg) != AppOpsManager.MODE_DEFAULT
            btn.text = if (denied) "恢复默认" else "拒绝悬浮窗"
            val cmd = if (denied) {
                "appops set $pkg SYSTEM_ALERT_WINDOW default"
            } else {
                "appops set $pkg SYSTEM_ALERT_WINDOW deny"
            }
            btn.setOnClickListener {
                btn.isEnabled = false
                Thread {
                    val r = shizuku.exec(cmd)
                    runOnUiThread {
                        btn.isEnabled = true
                        if (r.isFailure) {
                            Toast.makeText(
                                this,
                                "操作失败：${r.exceptionOrNull()?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        render()
                    }
                }.start()
            }
        } else {
            // 无 Shizuku：引导到系统悬浮窗开关页
            btn.text = getString(R.string.checkup_manage)
            btn.setOnClickListener {
                // 跳到系统按应用悬浮窗开关页（Android 8+ 直达，低版本回退到权限列表）
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$pkg")
                    )
                )
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
