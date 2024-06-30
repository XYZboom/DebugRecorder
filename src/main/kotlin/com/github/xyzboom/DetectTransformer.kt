package com.github.xyzboom

import org.objectweb.asm.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import java.util.*


class DetectTransformer(properties: Properties) : ClassFileTransformer {

    private val packageName: String = (properties["package"] ?: "").toString()

    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
    ): ByteArray? {
        if (className == null) {
            return null
        }
        if (!className.replace("/", ".").startsWith(packageName)) {
            return null
        }
        println("Transforming class: $className")
        try {
            val classReader = ClassReader(className)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            val classVisitor = DetectClassVisitor(classWriter)
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            return classWriter.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return classfileBuffer
    }
}