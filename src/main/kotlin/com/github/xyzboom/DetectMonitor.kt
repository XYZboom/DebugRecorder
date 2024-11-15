@file:Suppress("unused")

package com.github.xyzboom

import com.github.xyzboom.logger.ori.OriDebugLoggerHelper
import com.github.xyzboom.logger.small.SmallDebugLoggerHelper
import com.google.gson.*
import java.lang.reflect.Type


object DetectMonitor {

    private const val VAR_JSON_MAX_LENGTH = 512
    private const val VAR_JSON_MAX_BRACE = 128
    private const val VAR_ARRAY_MAX_LENGTH = 32
    private val notChangedVarRecorder = HashMap<String, HashMap<String, String>>()

    private val normalGson: Gson = GsonBuilder().create()

    internal object ClassJsonSerializer: JsonSerializer<Class<*>?> {
        override fun serialize(p0: Class<*>?, p1: Type?, p2: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(p0.toString())
        }
    }

    internal object ArrayJsonSerializer: JsonSerializer<Array<*>?> {
        override fun serialize(p0: Array<*>?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object CollectionJsonSerializer: JsonSerializer<Collection<*>?> {
        override fun serialize(p0: Collection<*>?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.take(VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object ByteArrayJsonSerializer: JsonSerializer<ByteArray?> {
        override fun serialize(p0: ByteArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object CharArrayJsonSerializer: JsonSerializer<CharArray?> {
        override fun serialize(p0: CharArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object IntArrayJsonSerializer: JsonSerializer<IntArray?> {
        override fun serialize(p0: IntArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object ShortArrayJsonSerializer: JsonSerializer<ShortArray?> {
        override fun serialize(p0: ShortArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object FloatArrayJsonSerializer: JsonSerializer<FloatArray?> {
        override fun serialize(p0: FloatArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object DoubleArrayJsonSerializer: JsonSerializer<DoubleArray?> {
        override fun serialize(p0: DoubleArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    internal object BooleanArrayJsonSerializer: JsonSerializer<BooleanArray?> {
        override fun serialize(p0: BooleanArray?, p1: Type?, p2: JsonSerializationContext): JsonElement {
            if (p0 == null) {
                return JsonNull.INSTANCE
            }
            if (p0.size <= VAR_ARRAY_MAX_LENGTH) {
                return normalGson.toJsonTree(p0)
            }
            val array = p0.copyOfRange(0, VAR_ARRAY_MAX_LENGTH)
            val result = normalGson.toJsonTree(array).asJsonArray
            result.add("...")
            return result
        }
    }

    @JvmStatic
    val mayErrorGson: Gson = GsonBuilder()
        .registerTypeAdapter(Class::class.java, ClassJsonSerializer)
        .registerTypeAdapter(Array::class.java, ArrayJsonSerializer)
        .registerTypeAdapter(Collection::class.java, CollectionJsonSerializer)
        .registerTypeAdapter(ByteArray::class.java, ByteArrayJsonSerializer)
        .registerTypeAdapter(CharArray::class.java, CharArrayJsonSerializer)
        .registerTypeAdapter(IntArray::class.java, IntArrayJsonSerializer)
        .registerTypeAdapter(ShortArray::class.java, ShortArrayJsonSerializer)
        .registerTypeAdapter(FloatArray::class.java, FloatArrayJsonSerializer)
        .registerTypeAdapter(DoubleArray::class.java, DoubleArrayJsonSerializer)
        .registerTypeAdapter(BooleanArray::class.java, BooleanArrayJsonSerializer)
        .create()

    internal object AllObjectSerializer: JsonSerializer<Any> {
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
    fun monitorEnterMethod(name: String) {
        // clear local variables when enter a method
        notChangedVarRecorder[name] = HashMap()
    }

    @JvmStatic
    fun monitorLocalVar(line: Int, vars: HashMap<String, Any?>, relevant: Boolean) {
        if (vars.isEmpty()) {
            return
        }
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
        val methodFullName = "$className::$methodName"
        val oriStr = gson.toJson(vars)
        if (oriStr != "{}") {
            OriDebugLoggerHelper.logger.info {
                "$className:$methodName:$line\n${oriStr}"
            }
        }
        if (relevant) return
        if (!notChangedVarRecorder.containsKey(methodFullName)) {
            notChangedVarRecorder[methodFullName] = HashMap()
        }
        val methodRecord = notChangedVarRecorder[methodFullName]!!
        val strMap = hashMapOf<String, Any?>()
        for ((str, value) in vars) {
            val valueStr = gson.toJson(value)
            if (valueStr.length > VAR_JSON_MAX_LENGTH || valueStr.count { it == '{' } > VAR_JSON_MAX_BRACE) {
                continue
            }
            if (methodRecord[str] == valueStr) {
                continue
            }
            strMap[str] = value
            methodRecord[str] = valueStr
        }
        if (strMap.isEmpty()) return
        val varsStr = gson.toJson(strMap)
        if (varsStr == "{}") return
        SmallDebugLoggerHelper.logger.info {
            "$className:$methodName:$line\n${varsStr}"
        }
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
        println(
            "$className:$methodName:$line\n" +
                    "expected: {$expectedStr} of type: {$expectedClassStr}"
        )
    }

    @JvmStatic
    fun monitorUnsupportedTestCase() {
        val stack = Thread.currentThread().stackTrace
        val className = stack[2].className
        val methodName = stack[2].methodName
        val line = stack[2].lineNumber
        println(
            "$className:$methodName:$line\n" +
                    "unsupported test case."
        )
    }
}