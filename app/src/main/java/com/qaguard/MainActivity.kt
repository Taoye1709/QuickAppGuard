package com.qaguard

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.qaguard.control.Controllers
import com.qaguard.control.DeviceOwnerController
import com.qaguard.control.EngineController
import com.qaguard.control.ShizukuController
import com.qaguard.detect.Detector
import com.qaguard.detect.EngineDatabase
import com.qaguard.detect.Verdicts
import com.qaguard.monitor.DailyGuard
import com.qaguard.model.DetectedEngine
import com.qaguard.model.EngineCategory
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单页应用：一个大状态灯 + 大按钮 + 三个开关。
 * 目标用户是老人：不展示功能列表，只回答“手机安不安全、点哪里”。
 *
 * 线程约束：Shizuku 首次连接最长阻塞 8 秒，所有 deactivate/restore 批量操作
 * 必须在工作线程执行，结束后回主线程刷新。
 */
class MainActivity : AppCompatActivity() {

    private val detector by lazy { Detector(packageManager, packageName) }
    private val owner by lazy { DeviceOwnerController(this) }
    private val shizuku by lazy { ShizukuController(this) }

    private lateinit var dot: View
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView
    private lateinit var tvLastCheck: TextView
    private lateinit var btnScan: Button
    private lateinit var swAuto: Switch
    private lateinit var swA11y: Switch
    private lateinit var swInstallLock: Switch
    private lateinit var swVendorAd: Switch
    private lateinit var llEngines: LinearLayout
    private lateinit var tvBlocked: TextView
    private lateinit var btnRestore: Button
    private lateinit var btnCheckup: Button
    private lateinit var btnSetup: Button

    /** refresh() 程序化同步开关状态期间，屏蔽监听器，避免覆写用户偏好 */
    private var uiSyncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dot = findViewById(R.id.view_status_dot)
        tvTitle = findViewById(R.id.tv_status_title)
        tvSub = findViewById(R.id.tv_status_sub)
        tvLastCheck = findViewById(R.id.tv_last_check)
        btnScan = findViewById(R.id.btn_scan)
        swAuto = findViewById(R.id.sw_auto)
        swA11y = findViewById(R.id.sw_a11y)
        swInstallLock = findViewById(R.id.sw_install_lock)
        swVendorAd = findViewById(R.id.sw_vendor_ad)
        llEngines = findViewById(R.id.ll_engines)
        tvBlocked = findViewById(R.id.tv_blocked)
        btnRestore = findViewById(R.id.btn_restore)
        btnCheckup = findViewById(R.id.btn_checkup)
        btnSetup = findViewById(R.id.btn_setup)

        btnScan.setOnClickListener { onScanClicked() }
        btnRestore.setOnClickListener { confirmRestore() }
        btnSetup.setOnClickListener { showSetup() }
        btnCheckup.setOnClickListener { startActivity(Intent(this, CheckupActivity::class.java)) }

        swAuto.setOnCheckedChangeListener { _, checked ->
            if (uiSyncing) return@setOnCheckedChangeListener
            Store.autoProtect = checked
        }
        swA11y.setOnCheckedChangeListener { _, checked ->
            if (uiSyncing) return@setOnCheckedChangeListener
            onA11yToggled(checked)
        }
        swInstallLock.setOnCheckedChangeListener { _, checked ->
            if (uiSyncing) return@setOnCheckedChangeListener
            onInstallLockToggled(checked)
        }
        swVendorAd.setOnCheckedChangeListener { _, checked ->
            if (uiSyncing) return@setOnCheckedChangeListener
            Store.vendorAdGuard = checked
            Toast.makeText(
                this,
                if (checked) "已启用：广告组件将随自动守护一起停用" else "已关闭：广告组件恢复原状请用「恢复」",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 保持复查闹钟新鲜（setInexactRepeating 幂等）
        DailyGuard.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        maybeShowInformed()
    }

    /**
     * 首次保护生效后的知情说明（只弹一次）。
     * 害处控制的关键一环：手机行为的变化必须可解释，
     * 否则"入口打不开"会被误读为手机坏了，反而把老人推向维修骗局。
     */
    private fun maybeShowInformed() {
        if (Store.informedShown) return
        val controllers = Controllers.all(this)
        if (controllers.isEmpty()) return
        if (countDeactivated(controllers) == 0) return
        Store.informedShown = true
        AlertDialog.Builder(this)
            .setTitle("已开启保护，请知悉")
            .setMessage(
                "1. 快应用的推广弹窗会被拦下，个别推广入口会打不开——" +
                    "这是保护在起作用，不是手机坏了。\n\n" +
                    "2. 需要安装新应用时，回到本应用先关闭「锁定未知来源安装」。\n\n" +
                    "3. 任何时候点主页的「恢复」即可还原全部。\n\n" +
                    "建议把本应用告诉老人和共同生活的家人。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun refresh() {
        uiSyncing = true
        try {
            refreshInner()
        } finally {
            // 无论刷新中途出任何异常，都必须还原同步标志，
            // 否则这批开关的监听器将永久沉默（静默失效比崩溃更难被发现）
            uiSyncing = false
        }
    }

    private fun refreshInner() {
        val engines = detector.scan()
        val adComponents = detector.scanAdComponents()
        val verdict = Verdicts.of(engines)
        val controllers = Controllers.all(this)

        // 处置面求稳：状态灯与一键处置只针对已确认的引擎；
        // 疑似组件（启发式/未验证）单独展示，待人工确认
        val actionable = engines.filter { it.entry?.verified == true && it.entry.coupled != true }
        val anyRunning = actionable.any { e -> controllers.none { it.isDeactivated(e.packageName) } }
        val deactivatedCount = countDeactivated(controllers)

        val (title, sub, color) = when {
            actionable.isNotEmpty() && anyRunning -> Triple(
                "发现 ${actionable.size} 个快应用框架",
                "点击下方「检查并保护」一键停用",
                R.color.warn
            )
            actionable.isEmpty() && verdict.coupledCount > 0 -> Triple(
                "本机快应用与系统应用耦合",
                "请查看下方每个引擎给出的官方关闭说明",
                R.color.caution
            )
            actionable.isEmpty() && verdict.pendingCount > 0 -> Triple(
                "发现 ${verdict.pendingCount} 个疑似快应用组件",
                "确认为引擎后才会停用，请与家人核对下方列表",
                R.color.caution
            )
            else -> Triple(
                "本机已受保护",
                if (deactivatedCount > 0) {
                    "已停用 $deactivatedCount 个快应用引擎，持续复查中"
                } else {
                    "未发现快应用框架，将自动持续复查"
                },
                R.color.ok
            )
        }
        tvTitle.text = title
        tvSub.text = sub
        dot.backgroundTintList = ContextCompat.getColorStateList(this, color)

        renderEngines(engines, adComponents, controllers)

        swAuto.isEnabled = controllers.isNotEmpty()
        swAuto.isChecked = controllers.isNotEmpty() && Store.autoProtect

        swA11y.isChecked = Store.a11yEnabled && A11yUtil.enabled(this)

        val installLockVisible = owner.available()
        swInstallLock.visibility = if (installLockVisible) View.VISIBLE else View.GONE
        if (installLockVisible) {
            val locked = InstallLock.isEnabled(this)
            if (swInstallLock.isChecked != locked) swInstallLock.isChecked = locked
        }

        // 广告组件开关：本机存在该类组件时才显示（默认关闭，实验性）
        swVendorAd.visibility = if (adComponents.isEmpty()) View.GONE else View.VISIBLE
        if (swVendorAd.isChecked != Store.vendorAdGuard) swVendorAd.isChecked = Store.vendorAdGuard

        tvBlocked.text = "无障碍弹窗拦截：累计 ${Store.blockedCount} 次"
        tvLastCheck.text = if (Store.lastCheckAt == 0L) {
            "上次复查：尚未运行"
        } else {
            "上次复查：${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(Store.lastCheckAt))}"
        }
    }

    /** 特征库里已确认被任一通道停用的引擎数（含扫描不到的隐藏包，让状态页给出真实保护规模）。 */
    private fun countDeactivated(controllers: List<EngineController>): Int {
        if (controllers.isEmpty()) return 0
        val pkgs = EngineDatabase.entries
            .filter { !it.coupled && (it.category == EngineCategory.QUICK_APP || Store.vendorAdGuard) }
            .map { it.packageName }
        return pkgs.count { pkg -> controllers.any { it.isDeactivated(pkg) } }
    }

    private fun renderEngines(
        engines: List<DetectedEngine>,
        adComponents: List<DetectedEngine>,
        controllers: List<EngineController>
    ) {
        llEngines.removeAllViews()
        if (engines.isEmpty() && adComponents.isEmpty()) {
            addEngineLine("未检测到快应用框架 ✓")
            return
        }
        for (e in engines) {
            val entry = e.entry
            val vendor = entry?.vendor ?: "未收录（启发式发现）"
            val handled = controllers.any { it.isDeactivated(e.packageName) }
            val state = StringBuilder("$vendor\n${e.packageName} — ").append(
                when {
                    entry != null && entry.coupled -> "耦合组件 · 请走官方开关"
                    handled -> "已停用 ✓"
                    entry == null || !entry.verified -> "疑似组件 · 待人工确认，不自动停用"
                    else -> "运行中 ⚠"
                }
            )
            if (entry != null && entry.officialToggle.isNotEmpty()) {
                state.append("\n官方关闭：").append(entry.officialToggle)
            }
            if (entry != null && entry.coupled) {
                state.append("\n").append(entry.note)
            }
            addEngineLine(state.toString())
        }
        for (e in adComponents) {
            val entry = e.entry
            val handled = controllers.any { it.isDeactivated(e.packageName) }
            val state = when {
                handled -> "已停用 ✓"
                Store.vendorAdGuard -> "运行中 ⚠"
                else -> "未拦截（打开上方实验开关后自动处理）"
            }
            val line = StringBuilder("${entry?.vendor ?: "未知"}·广告组件\n${e.packageName} — $state")
            if (!entry?.officialToggle.isNullOrEmpty()) {
                line.append("\n官方关闭：").append(entry!!.officialToggle)
            }
            addEngineLine(line.toString())
        }
    }

    private fun addEngineLine(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.setLineSpacing(0f, 1.1f)
        tv.setPadding(0, dp(8), 0, dp(8))
        llEngines.addView(tv)
    }

    private fun onScanClicked() {
        // 无任何通道：进入激活向导
        if (!shizuku.running() && controllerBest() == null) {
            showSetup()
            return
        }
        // Shizuku 在跑但未授权：走授权
        if (controllerBest() == null && shizuku.running()) {
            Shizuku.requestPermission(0)
            Toast.makeText(this, "请在 Shizuku 中允许本应用，然后重试", Toast.LENGTH_LONG).show()
            return
        }

        btnScan.isEnabled = false
        Thread {
            val controllers = Controllers.all(this)
            val all = detector.scan()
            val targets = all
                .filter { it.entry?.verified == true && it.entry.coupled != true }
                .toMutableList()
            if (Store.vendorAdGuard) targets += detector.scanAdComponents()
            val pending = all.count {
                it.entry == null || (it.entry != null && !it.entry.verified && it.entry.coupled != true)
            }
            var done = 0
            var fail = 0
            for (e in targets) {
                if (controllers.any { it.isDeactivated(e.packageName) }) continue
                // 通道 1 失败（如系统判定为关键组件）则换通道 2 重试
                var ok = false
                for (c in controllers) {
                    if (c.deactivate(e.packageName).isSuccess) {
                        ok = true
                        break
                    }
                }
                if (ok) done++ else fail++
            }
            Store.lastCheckAt = System.currentTimeMillis()
            runOnUiThread {
                btnScan.isEnabled = true
                val pendingHint = if (pending > 0) "；另有 $pending 个疑似组件待确认" else ""
                val msg = when {
                    targets.isEmpty() ->
                        if (pending > 0) "没有已确认的可处理对象$pendingHint"
                        else "本机没有快应用框架，无需处理"
                    done == 0 && fail == 0 -> "全部引擎已处于停用状态$pendingHint"
                    fail == 0 -> "本次停用 $done 个引擎$pendingHint"
                    else -> "本次停用 $done 个，失败 $fail 个$pendingHint"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                refresh()
            }
        }.start()
    }

    private fun controllerBest(): EngineController? = Controllers.best(this)

    private fun onA11yToggled(checked: Boolean) {
        Store.a11yEnabled = checked
        if (checked && !A11yUtil.enabled(this)) {
            Toast.makeText(this, "请在系统无障碍设置中开启「净屏守护」", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun onInstallLockToggled(checked: Boolean) {
        val r = InstallLock.set(this, checked)
        Toast.makeText(
            this,
            if (r.isSuccess) {
                if (checked) "已锁定：未知来源应用无法安装" else "已解除锁定"
            } else {
                "设置失败：${r.exceptionOrNull()?.message}"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle("恢复快应用框架")
            .setMessage("将把已停用的快应用引擎全部恢复为启用状态（广告骚扰会随之回来）。确定继续？")
            .setPositiveButton("恢复") { _, _ -> doRestore() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestore() {
        btnRestore.isEnabled = false
        Thread {
            val controllers = Controllers.all(this)
            val targets = (
                detector.scan().map { it.packageName } +
                    EngineDatabase.entries.map { it.packageName }
                ).distinct()
            // 遍历全部通道：引擎可能由 Device Owner 隐藏，也可能由 Shizuku 停用
            for (c in controllers) {
                for (pkg in targets) c.restore(pkg)
            }
            runOnUiThread {
                btnRestore.isEnabled = true
                Toast.makeText(this, "已尝试恢复全部引擎", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    private fun showSetup() {
        val cmd = "adb shell dpm set-device-owner com.qaguard/.admin.GuardAdminReceiver"
        val msg = """
            一次性激活，之后无需电脑：

            1. 手机：设置 → 关于手机 → 连点“版本号”7次开启开发者选项，并打开“USB 调试”
            2. 手机先退出所有账号（设置 → 账号），这是系统要求
            3. 电脑执行：
               $cmd
            4. 回到本应用，看到绿色“已受保护”即成功

            无法连电脑？可安装 Shizuku：手机开“无线调试”启动 Shizuku，再点「检查并保护」按提示授权。

            提示：激活成功后可重新登录原账号，不影响日常使用。
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("激活 Device Owner（推荐）")
            .setMessage(msg)
            .setPositiveButton("复制命令") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cmd", cmd))
                Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
