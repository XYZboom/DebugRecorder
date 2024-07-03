package com.github.xyzboom

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import java.util.*
import kotlin.collections.HashSet


class DetectTransformer(properties: Properties) : ClassFileTransformer {

    companion object {
        val classNameWhiteList = listOf(
            DetectAgent::class.java,
            DetectClassVisitor::class.java,
            DetectMethodVisitor::class.java,
            DetectMonitor::class.java,
            DetectTransformer::class.java,
            DetectMonitor.ClassJsonSerializer::class.java,
            DetectMonitor.AllObjectSerializer::class.java,
        ).map(Type::getInternalName).toHashSet()
    }

    private val packageName: String = (properties["package"] ?: "").toString()
    private val classes: Set<String> =
        properties["classes"]?.toString()?.split(";")?.toSet()
            ?: emptySet()

    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
    ): ByteArray? {
        if (className == null || className in classNameWhiteList) {
            return null
        }
        val name = className.replace("/", ".")
        if (!name.startsWith(packageName)) {
            return null
        }
        if (classes.isNotEmpty() && name.removePrefix("${packageName}.") !in classes) {
            return null
        }
        println("Transforming class: $className")
        try {
            val classReader = ClassReader(className)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
//            val classVisitor = DetectClassVisitor(classWriter)
//            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
//            return classWriter.toByteArray()
            val classNode = ClassNode(ASM9)
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES)
            transformClass(classNode)
            classNode.accept(classWriter)
            return classWriter.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return classfileBuffer
    }

    private fun transformClass(classNode: ClassNode) {
        classNode.methods.forEach(this::transformMethod)
    }

    private fun transformMethod(methodNode: MethodNode) {
        if (methodNode.name == "<clinit>" || methodNode.name == "<init>") {
            return
        }
        if (methodNode.localVariables == null) {
            return
        }
        val visitedVars = hashSetOf<LocalVariableNode>()
        val startVarMap = hashMapOf<LabelNode, HashSet<LocalVariableNode>>()
        val endVarMap = hashMapOf<LabelNode, HashSet<LocalVariableNode>>()
        for (localVar in methodNode.localVariables) {
            if (localVar.start !in startVarMap) {
                startVarMap[localVar.start] = HashSet()
            }
            if (localVar.end !in endVarMap) {
                endVarMap[localVar.end] = HashSet()
            }
            startVarMap[localVar.start]!!.add(localVar)
            endVarMap[localVar.end]!!.add(localVar)
        }
        for (inst in methodNode.instructions) {
            if (inst is LabelNode) {
                if (inst in startVarMap) {
                    val varNode = startVarMap[inst]!!
                    visitedVars.addAll(varNode)
                }
                if (inst in endVarMap) {
                    val varNode = endVarMap[inst]!!
                    visitedVars.removeAll(varNode)
                }
            }
            if (inst is LineNumberNode) {
                val insnList = generateMonitorLocalVar(inst.line, visitedVars)
                if (inst.next is FrameNode) {
                    var insertBefore = inst.next
                    while (insertBefore is FrameNode) {
                        insertBefore = insertBefore.next
                    }
                    methodNode.instructions.insertBefore(insertBefore, insnList)
                } else if (inst.next is TypeInsnNode && inst.next.opcode == NEW) {
                    methodNode.instructions.insertBefore(inst, insnList)
                } else {
                    methodNode.instructions.insert(inst, insnList)
                }
            }
        }
    }

    private fun generateMonitorLocalVar(
        line: Int,
        visitedVars: HashSet<LocalVariableNode>,
    ): InsnList {
        val list = InsnList()
        when (line) {
            in -1..5 -> {
                list.add(InsnNode(ICONST_0 + line))
            }
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
                list.add(IntInsnNode(BIPUSH, line))
            }
            in Short.MIN_VALUE..Short.MAX_VALUE -> {
                list.add(IntInsnNode(SIPUSH, line))
            }
            else -> list.add(IntInsnNode(LDC, line))
        }
        list.add(TypeInsnNode(NEW, "java/util/HashMap"))
        list.add(InsnNode(DUP))
        list.add(
            MethodInsnNode(
                INVOKESPECIAL, "java/util/HashMap",
                "<init>", "()V", false
            )
        )
        val boxVars = hashSetOf<String>()
        for (localVar in visitedVars) {
            list.add(InsnNode(DUP))
            list.add(LdcInsnNode(localVar.name))
            list.add(loadVar(localVar))
            val boxIfNeed = boxIfNeed(localVar.desc)
            if (boxIfNeed != null) {
                boxVars.add(localVar.name)
                list.add(boxIfNeed)
            }
            list.add(
                MethodInsnNode(
                    INVOKEVIRTUAL, "java/util/HashMap",
                    "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false
                )
            )
            list.add(InsnNode(POP))
        }
        list.add(IntInsnNode(BIPUSH, boxVars.size))
        list.add(TypeInsnNode(ANEWARRAY, "java/lang/String"))
        for ((index, boxVarName) in boxVars.withIndex()) {
            list.add(InsnNode(DUP))
            list.add(IntInsnNode(BIPUSH, index))
            list.add(LdcInsnNode(boxVarName))
            list.add(InsnNode(AASTORE))
        }
        list.add(
            MethodInsnNode(
                AdviceAdapter.INVOKESTATIC, "com/github/xyzboom/DetectMonitor",
                "monitorLocalVar", "(ILjava/util/HashMap;[Ljava/lang/String;)V", false
            )
        )
        return list
    }

    private fun loadVar(localVar: LocalVariableNode): VarInsnNode {
        val code = when (localVar.desc) {
            Type.INT_TYPE.descriptor, Type.SHORT_TYPE.descriptor,
            Type.BOOLEAN_TYPE.descriptor, Type.CHAR_TYPE.descriptor,
            Type.BYTE_TYPE.descriptor -> ILOAD

            Type.LONG_TYPE.descriptor -> LLOAD

            Type.DOUBLE_TYPE.descriptor -> DLOAD

            Type.FLOAT_TYPE.descriptor -> FLOAD
            else -> ALOAD
        }
        return VarInsnNode(code, localVar.index)
    }

    private fun boxIfNeed(desc: String): MethodInsnNode? {
        return when (desc) {
            Type.INT_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Integer",
                    "valueOf", "(I)Ljava/lang/Integer;", false
                )

            Type.BOOLEAN_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Boolean",
                    "valueOf", "(Z)Ljava/lang/Boolean;", false
                )

            Type.LONG_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false
                )

            Type.DOUBLE_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false
                )

            Type.FLOAT_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false
                )

            Type.SHORT_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Short",
                    "valueOf", "(S)Ljava/lang/Short;", false
                )

            Type.CHAR_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Character",
                    "valueOf", "(C)Ljava/lang/Character;", false
                )

            Type.BYTE_TYPE.descriptor ->
                MethodInsnNode(
                    INVOKESTATIC, "java/lang/Byte",
                    "valueOf", "(B)Ljava/lang/Byte;", false
                )


            else -> null
        }
    }
}