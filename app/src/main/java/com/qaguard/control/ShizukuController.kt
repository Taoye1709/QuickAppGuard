package com.qaguard.control

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.qaguard.IShellService
import com.qaguard.shizuku.ShellService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku 通道（备选）：
 * 用户开启一次“无线调试”或连电脑启动 Shizuku 后，
 * 本应用借 shell 身份执行 pm disable-user / pm enable。
 * 注意：手机重启后 Shizuku 需重新启动（可由 Shizuku 应用自行提示）。
 */
class ShizukuController(private val context: Context) : EngineController {

    override val modeName = "Shizuku"

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ShellService::class.java)
    )
        .processNameSuffix("guard")
        .debuggable(false)
        .version(1)

    @Volatile
    private var cached: IShellService? = null

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    private fun granted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    override fun available(): Boolean = running() && granted()

    override fun reasonUnavailable(): String = when {
        !running() -> "Shizuku 未运行，请先启动 Shizuku 应用"
        else -> "请在 Shizuku 中授权本应用"
    }

    private fun obtain(): IShellService {
        cached?.let { return it }
        if (!running()) throw IllegalStateException(reasonUnavailable())
        if (!granted()) throw SecurityException(reasonUnavailable())

        val latch = CountDownLatch(1)
        var binder: IBinder? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                binder = b
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                cached = null
            }
        }
        Shizuku.bindUserService(serviceArgs, conn)
        if (!latch.await(8, TimeUnit.SECONDS)) {
            throw IllegalStateException("连接 Shizuku 用户服务超时")
        }
        val connected = binder ?: throw IllegalStateException("Shizuku 用户服务未连接")
        val service = IShellService.Stub.asInterface(connected)
        cached = service
        return service
    }

    override fun deactivate(pkg: String): kotlin.Result<Unit> {
        val disable = shell("pm disable-user --user 0 $pkg")
        if (disable.isSuccess) return disable
        // 部分机型（如 HyperOS）把引擎标记为不可 disable；
        // 参考开源项目 FxxkMIUIAd 的实测结论，退级为 suspend 仍然有效
        return shell("pm suspend --user 0 $pkg")
    }

    override fun restore(pkg: String): kotlin.Result<Unit> {
        // 两种停用状态都尝试还原，命令均幂等
        val unsuspend = shell("pm unsuspend --user 0 $pkg")
        val enable = shell("pm enable $pkg")
        return if (enable.isSuccess) enable else unsuspend
    }

    override fun isDeactivated(pkg: String): Boolean = try {
        val pm = context.packageManager
        pm.getApplicationEnabledSetting(pkg) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || pm.isPackageSuspended(pkg)
    } catch (t: Throwable) {
        false
    }

    private fun shell(cmd: String): kotlin.Result<Unit> = try {
        val out = obtain().exec(cmd)
        if (ShellOutput.isError(out)) {
            kotlin.Result.failure(RuntimeException(out.trim().take(200)))
        } else {
            kotlin.Result.success(Unit)
        }
    } catch (t: Throwable) {
        kotlin.Result.failure(t)
    }
}
