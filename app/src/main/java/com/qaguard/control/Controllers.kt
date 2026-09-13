package com.qaguard.control

import android.content.Context

/** 通道选择：Device Owner 优先（重启不失效），Shizuku 兜底。 */
object Controllers {

    /** 当前所有可用通道。恢复操作必须遍历全部通道：引擎可能由任一通道停用。 */
    fun all(context: Context): List<EngineController> =
        listOf(DeviceOwnerController(context), ShizukuController(context))
            .filter { it.available() }

    fun best(context: Context): EngineController? = all(context).firstOrNull()
}
