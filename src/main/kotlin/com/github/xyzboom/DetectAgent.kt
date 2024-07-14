package com.github.xyzboom

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation
import java.util.Properties

@Suppress("unused")
object DetectAgent {
    private const val D4J_FILE = "defects4j.build.properties"
    private const val D4J_CLASSES_RELEVANT_KEY = "d4j.classes.relevant"
    private const val D4J_TEST_TRIGGER_KEY = "d4j.tests.trigger"
    private val logger = KotlinLogging.logger {}

    /**
     * 在主线程启动之前进行处理
     *
     * @param agentArgs       代理请求参数
     * @param instrumentation 插桩
     */
    @JvmStatic
    fun premain(agentArgs: String?, instrumentation: Instrumentation) {
        logger.info { "premain detect" }
        val command = System.getProperty("sun.java.command")
        if (command != null && "defects4j.build.xml" in command
            && !command.endsWith("run.dev.tests")
        ) { // hard coding to avoid transform in compile process
            return
        }
        val baseDirStr = System.getProperty("user.dir") ?: return
        val baseDir = File(baseDirStr)
        val properties = Properties()
        try {
            properties.load(File(baseDir, D4J_FILE).reader())
        } catch (e: IOException) {
            logger.error { e.stackTraceToString() }
            logger.info { "Error when reading file: $D4J_FILE! Skip." }
        }
        val classes = properties.getProperty(D4J_CLASSES_RELEVANT_KEY).split(",").toSet()
        val triggerTest = properties.getProperty(D4J_TEST_TRIGGER_KEY).split(",").toSet()
        instrumentation.addTransformer(DetectTransformer(classes, triggerTest), true)
    }
}