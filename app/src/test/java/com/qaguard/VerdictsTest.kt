package com.qaguard

import com.qaguard.detect.Verdict
import com.qaguard.detect.Verdicts
import com.qaguard.model.DetectedEngine
import com.qaguard.model.EngineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictsTest {

    private fun entry(pkg: String, coupled: Boolean, verified: Boolean) =
        EngineEntry(pkg, "测试厂商", coupled, verified, note = "测试")

    @Test
    fun emptyListIsClean() {
        val v = Verdicts.of(emptyList())
        assertEquals(Verdict.Level.CLEAN, v.level)
        assertEquals(0, v.engineCount)
        assertEquals(0, v.actionableCount)
    }

    @Test
    fun verifiedUncoupledIsActionable() {
        val engines = listOf(
            DetectedEngine(entry("a", coupled = false, verified = true), "a", byHeuristic = false),
            DetectedEngine(null, "b", byHeuristic = true)
        )
        val v = Verdicts.of(engines)
        assertEquals(Verdict.Level.ACTION_NEEDED, v.level)
        assertEquals(1, v.actionableCount)
        assertEquals(1, v.pendingCount)
    }

    @Test
    fun unverifiedIsNeverActionable() {
        // 未验证的库内条目哪怕疑似引擎也不允许自动处置
        val engines = listOf(
            DetectedEngine(entry("a", coupled = false, verified = false), "a", byHeuristic = false)
        )
        val v = Verdicts.of(engines)
        assertEquals(0, v.actionableCount)
        assertEquals(1, v.pendingCount)
        assertEquals(Verdict.Level.PENDING_ONLY, v.level)
    }

    @Test
    fun heuristicOnlyIsPendingOnly() {
        val engines = listOf(DetectedEngine(null, "u", byHeuristic = true))
        val v = Verdicts.of(engines)
        assertEquals(Verdict.Level.PENDING_ONLY, v.level)
        assertEquals(1, v.pendingCount)
    }

    @Test
    fun coupledOnlyIsGuideOnly() {
        val engines = listOf(
            DetectedEngine(entry("c", coupled = true, verified = true), "c", byHeuristic = false)
        )
        val v = Verdicts.of(engines)
        assertEquals(Verdict.Level.GUIDE_ONLY, v.level)
        assertEquals(1, v.coupledCount)
        assertEquals(0, v.actionableCount)
    }

    @Test
    fun mixedCountsAll() {
        val engines = listOf(
            DetectedEngine(entry("a", coupled = false, verified = true), "a", byHeuristic = false),
            DetectedEngine(entry("c", coupled = true, verified = true), "c", byHeuristic = false),
            DetectedEngine(entry("u", coupled = false, verified = false), "u", byHeuristic = false),
            DetectedEngine(null, "h", byHeuristic = true)
        )
        val v = Verdicts.of(engines)
        assertEquals(4, v.engineCount)
        assertEquals(1, v.actionableCount)
        assertEquals(2, v.pendingCount)
        assertEquals(1, v.coupledCount)
        assertEquals(Verdict.Level.ACTION_NEEDED, v.level)
    }
}
