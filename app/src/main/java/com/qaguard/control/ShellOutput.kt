package com.qaguard.control

/** pm/shell 命令输出的成败判定，纯逻辑、可单测。 */
object ShellOutput {

    private val errorMarkers = listOf("Error", "Exception", "Failure")

    fun isError(out: String): Boolean =
        errorMarkers.any { out.contains(it, ignoreCase = false) }
}
