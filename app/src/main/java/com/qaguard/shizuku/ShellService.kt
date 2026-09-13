package com.qaguard.shizuku

import androidx.annotation.Keep
import com.qaguard.IShellService

/**
 * 运行在 Shizuku（shell uid）环境中的最小命令执行服务。
 * 仅用于 pm disable-user / pm enable 两条命令，暴露面极小。
 *
 * 注意：修改本类后必须同步提升 ShizukuController 中 serviceArgs 的 version，
 * 否则 Shizuku 会继续复用旧进程。
 */
@Keep
class ShellService : IShellService.Stub() {

    @Keep
    fun destroy() {
        // Shizuku 回收服务进程前调用，本服务无状态、无需清理
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        out
    } catch (t: Throwable) {
        "Error: ${t.message}"
    }
}
