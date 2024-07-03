@file:Suppress("unused")

package com.github.xyzboom

import com.google.gson.*
import java.lang.reflect.Type


object DetectMonitor {
    internal object ClassJsonSerializer : JsonSerializer<Class<*>?> {
        override fun serialize(p0: Class<*>?, p1: Type?, p2: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(p0.toString())
        }
    }

    @JvmStatic
    val mayErrorGson: Gson = GsonBuilder()
        .registerTypeAdapter(Class::class.java, ClassJsonSerializer)
        .create()

    internal object AllObjectSerializer : JsonSerializer<Any> {
        override fun serialize(p0: Any?, p1: Type?, p2: JsonSerializationContext?): JsonElement {
            return try {
                mayErrorGson.toJsonTree(p0, p1)
            } catch (e: Throwable) {
                JsonPrimitive(p0.toString())
            } catch (e: StackOverflowError) {
                JsonPrimitive(p0.toString())
            }
        }
    }

    @JvmStatic
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Class::class.java, ClassJsonSerializer)
        .registerTypeHierarchyAdapter(Any::class.java, AllObjectSerializer)
        .addReflectionAccessFilter(ReflectionAccessFilter.BLOCK_INACCESSIBLE_JAVA)
        .create()

    @JvmStatic
    fun monitorLocalVar(line: Int, vars: HashMap<String, Any?>, boxVars: Array<String>) {
        val stack = Thread.currentThread().stackTrace
        var monitorInStack = false
        for (i in stack) {
            if (i.className == DetectMonitor::class.qualifiedName) {
                // do not monitor the method called by monitor
                if (monitorInStack) {
                    return
                }
                monitorInStack = true
            }
        }
        val className = stack[2].className
        val methodName = stack[2].methodName
        val varsStr = gson.toJson(vars)
        println("$className:$methodName:$line\n" +
                "${varsStr}, boxVars: ${boxVars.contentToString()}")
    }

    @JvmStatic
    fun monitorTestCase(expected: Any?) {
        val expectedStr = gson.toJson(expected)
        val expectedClassStr = if (expected != null) {
            expected::class.java
        } else {
            null
        }
        val stack = Thread.currentThread().stackTrace
        val className = stack[2].className
        val methodName = stack[2].methodName
        val line = stack[2].lineNumber
        println("$className:$methodName:$line\n" +
                "expected: {$expectedStr} of type: {$expectedClassStr}")
    }

    @JvmStatic
    fun monitorUnsupportedTestCase() {
        val stack = Thread.currentThread().stackTrace
        val className = stack[2].className
        val methodName = stack[2].methodName
        val line = stack[2].lineNumber
        println("$className:$methodName:$line\n" +
                "unsupported test case.")
    }
}