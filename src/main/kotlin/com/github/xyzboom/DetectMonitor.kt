@file:Suppress("unused")

package com.github.xyzboom

object DetectMonitor {
    @JvmStatic
    fun monitorLocalVar(line: Int, vars: HashMap<String, Any?>, boxVars: Array<String>) {
        println("$line: ${vars}, boxVars: ${boxVars.contentToString()}")
    }
}