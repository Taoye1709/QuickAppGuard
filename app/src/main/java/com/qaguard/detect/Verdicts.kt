package com.qaguard.detect

import com.qaguard.model.DetectedEngine

/**
 * 扫描结果的整体判定，纯逻辑、可单测。
 *
 * 核心原则：识别面求全，处置面求稳——
 * 只有 verified 且非耦合的引擎才进入 actionable（允许自动/一键停用）；
 * 启发式与未验证条目一律归入 pending（仅展示，待人工确认），
 * 避免"误停一个无关应用"对老人造成"手机坏了"的二阶伤害。
 */
data class Verdict(
    val engineCount: Int,
    val actionableCount: Int,
    val pendingCount: Int,
    val coupledCount: Int,
    val level: Level
) {
    enum class Level { CLEAN, ACTION_NEEDED, PENDING_ONLY, GUIDE_ONLY }
}

object Verdicts {

    fun of(engines: List<DetectedEngine>): Verdict {
        var actionable = 0
        var pending = 0
        var coupled = 0
        for (e in engines) {
            when {
                e.entry == null -> pending++
                e.entry.coupled -> coupled++
                e.entry.verified -> actionable++
                else -> pending++
            }
        }
        val level = when {
            engines.isEmpty() -> Verdict.Level.CLEAN
            actionable > 0 -> Verdict.Level.ACTION_NEEDED
            coupled > 0 -> Verdict.Level.GUIDE_ONLY
            else -> Verdict.Level.PENDING_ONLY
        }
        return Verdict(engines.size, actionable, pending, coupled, level)
    }
}
