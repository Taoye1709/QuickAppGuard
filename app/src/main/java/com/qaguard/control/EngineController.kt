package com.qaguard.control

/**
 * 快应用引擎处置通道统一接口。
 *
 * 约束：
 *  - 所有操作必须可逆（restore 与 deactivate 一一对应）
 *  - 只允许作用于机主本人显式授权过的设备
 */
interface EngineController {

    val modeName: String

    fun available(): Boolean

    fun reasonUnavailable(): String

    fun deactivate(pkg: String): kotlin.Result<Unit>

    fun restore(pkg: String): kotlin.Result<Unit>

    fun isDeactivated(pkg: String): Boolean
}
