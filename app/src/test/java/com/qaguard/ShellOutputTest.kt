package com.qaguard

import com.qaguard.control.ShellOutput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellOutputTest {

    @Test
    fun detectsPmErrors() {
        assertTrue(ShellOutput.isError("Error: java.lang.SecurityException: permission denial"))
        assertTrue(ShellOutput.isError("Failure calling remote service"))
    }

    @Test
    fun acceptsPmSuccessOutput() {
        assertFalse(ShellOutput.isError("Package com.miui.hybrid new state: disabled-user"))
        assertFalse(ShellOutput.isError("Package com.miui.hybrid new state: enabled"))
        assertFalse(ShellOutput.isError(""))
    }
}
