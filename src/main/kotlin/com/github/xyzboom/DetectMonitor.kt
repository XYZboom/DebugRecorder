@file:Suppress("unused")

package com.github.xyzboom

object DetectMonitor {
    @JvmStatic
    fun monitorLocalVar(vararg vars: Any?) {
        println(vars.contentToString())
    }
}