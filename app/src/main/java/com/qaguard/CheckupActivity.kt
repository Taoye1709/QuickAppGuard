package com.qaguard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 体检中心：把“不进自动处置管线、但能查能引导”的骚扰来源集中在这里。
 * v0.2 覆盖：悬浮窗权限（“盖住屏幕”类广告的来源，含拨号界面被遮挡场景）。
 * 只检测 + 引导到系统页面，不代用户做决定。
 */
class CheckupActivity : AppCompatActivity() {

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

    private fun buildRow(pkg: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val tv = TextView(this).apply {
            text = pkg
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            text = getString(R.string.checkup_manage)
            textSize = 14f
            setOnClickListener {
                // 跳到系统按应用悬浮窗开关页（Android 8+ 直达，低版本回退到权限列表）
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$pkg")
                    )
                )
            }
        }
        row.addView(tv)
        row.addView(btn)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
