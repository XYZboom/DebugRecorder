@file:Suppress("unused")

package com.github.xyzboom

object DetectMonitor {
    @JvmStatic
    fun monitorLocalVar(line: Int, vars: HashMap<String, Any?>, boxVars: Array<String>) {
        val stack = Thread.currentThread().stackTrace
        val className = stack[2].className
        val methodName = stack[2].methodName
        println("$className:$methodName:$line: ${vars}, boxVars: ${boxVars.contentToString()}")
    }
}