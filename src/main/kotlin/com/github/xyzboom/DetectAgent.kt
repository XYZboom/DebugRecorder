package com.github.xyzboom

import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation
import java.util.Properties

@Suppress("unused")
object DetectAgent {
    /**
     * 在主线程启动之前进行处理
     *
     * @param agentArgs       代理请求参数
     * @param instrumentation 插桩
     */
    @JvmStatic
    fun premain(agentArgs: String?, instrumentation: Instrumentation) {
        println("premain detect")
        if (agentArgs == null) {
            println("No args specified! Skip.")
            return
        }
        val properties = Properties()
        val file = File(agentArgs)
        if (!file.exists()) {
            println("File $file not exists! Skip.")
            return
        }
        if (file.isDirectory) {
            println("File $file must be a file! Skip.")
            return
        }
        try {
            properties.load(file.reader())
        } catch (e: IOException) {
            e.printStackTrace()
            println("Error when reading file: $file! Skip.")
        }
        instrumentation.addTransformer(DetectTransformer(properties), true)
    }
}