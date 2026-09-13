package com.qaguard

import com.qaguard.detect.EngineDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineDatabaseTest {

    @Test
    fun packageNamesAreUnique() {
        val duplicated = EngineDatabase.entries
            .groupBy { it.packageName }
            .filterValues { it.size > 1 }
        assertTrue("特征库存在重复包名: $duplicated", duplicated.isEmpty())
    }

    @Test
    fun coupledEntriesMustExplainWhy() {
        EngineDatabase.entries.filter { it.coupled }.forEach {
            assertTrue("耦合条目 ${it.packageName} 缺少说明", it.note.isNotBlank())
        }
    }

    @Test
    fun coupledEntriesMustBeVerified() {
        EngineDatabase.entries.filter { it.coupled }.forEach {
            assertTrue("耦合判定必须基于已确认的包名: ${it.packageName}", it.verified)
        }
    }

    @Test
    fun coupledEntriesMustHaveOfficialToggle() {
        EngineDatabase.entries.filter { it.coupled }.forEach {
            assertTrue("耦合条目 ${it.packageName} 缺少官方关闭入口指引", it.officialToggle.isNotBlank())
        }
    }

    @Test
    fun findsKnownEngine() {
        assertNotNull(EngineDatabase.find("com.miui.hybrid"))
        assertNull(EngineDatabase.find("com.android.launcher"))
    }

    @Test
    fun manifestQueriesCoverDatabase() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        EngineDatabase.entries.forEach {
            assertTrue(
                "AndroidManifest <queries> 缺少 ${it.packageName}，Android 11+ 将查不到该包",
                manifest.contains("android:name=\"${it.packageName}\"")
            )
        }
    }
}
