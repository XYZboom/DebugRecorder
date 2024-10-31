package com.github.xyzboom

import com.github.xyzboom.DetectAgent.D4J_FILE
import com.github.xyzboom.utils.boxIfNeed
import com.github.xyzboom.utils.isWriteLocalVar
import com.github.xyzboom.utils.loadVar
import com.github.xyzboom.visitors.DetectClassVisitor
import com.github.xyzboom.visitors.DetectMethodVisitor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class DetectTransformer(
    classes: Set<String>,
    /**
     * methods here only used by transform d4j test classes
     */
    methods: Set<String>,
    args: Properties
) : ClassFileTransformer {

    private val classes: HashSet<String> = HashSet()
    private val methods: HashSet<String> = HashSet()

    /** Monitor only changed variables or not.
     * Note that all variables will be monitored when they come out for the first time.
     */
    private val changedLocalVarsOnly = args.getProperty(KEY_CHANGED_LOCAL_VARS_ONLY, "false").toBoolean()

    init {
        if (!args.getProperty(KEY_ARGS_USE_SPECIFIED).toBoolean()) {
            this.classes.addAll(classes)
            for (methodName in methods) {
                this.classes.add(methodName.split("::")[0])
            }
        } else {
            val specifiedMethods = args.getProperty(KEY_ARGS_METHODS, "").split(",")
            this.methods.addAll(specifiedMethods)
            val specifiedClasses = args.getProperty(KEY_ARGS_CLASSES, "").split(",")
            this.classes.addAll(specifiedClasses)
            for (methodName in specifiedMethods) {
                this.classes.add(methodName.split("::")[0])
            }
            val addingMethods = HashSet<String>()
            for (method in this.methods) {
                if (method.isEmpty()) continue
                val clazzAndMethodName = method.split("::")
                val clazzName = clazzAndMethodName[0]
                val methodName = clazzAndMethodName[1]
                if (clazzName.endsWith(methodName)) {
                    addingMethods.add("${clazzName}::<init>")
                }
            }
            this.methods.addAll(addingMethods)
            this.classes.remove("")
            this.methods.remove("")
            if (this.classes.isEmpty() && this.methods.isEmpty()) {
                // when baseline location is not available, use relevant ones.
                this.classes.addAll(classes)
            }
        }
        this.classes.remove("")
        this.methods.remove("")
        // hard code to avoid print exception classes
        this.classes.removeIf { "Exception" in it }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val KEY_CHANGED_LOCAL_VARS_ONLY = "changedLocalVarsOnly"

        /**
         * Use specified but not classes in [D4J_FILE]
         */
        private const val KEY_ARGS_USE_SPECIFIED = "args.use.specified"
        private const val KEY_ARGS_CLASSES = "args.classes"
        private const val KEY_ARGS_METHODS = "args.methods"
        val classNameWhiteList = listOf(
            DetectAgent::class.java,
            DetectClassVisitor::class.java,
            DetectMethodVisitor::class.java,
            DetectMonitor::class.java,
            DetectTransformer::class.java,
            Companion::class.java,
            DetectMonitor.ClassJsonSerializer::class.java,
            DetectMonitor.AllObjectSerializer::class.java,
        ).map(Type::getInternalName).toHashSet()
    }


    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
    ): ByteArray? {
        val classLoader = loader ?: ClassLoader.getSystemClassLoader()
        if (className == null || className in classNameWhiteList) {
            return null
        }
        val name = className.replace("/", ".")
        if (classes.isNotEmpty() && name !in classes) {
            return null
        }
        println("Transforming class: $className")
        try {
            val classReader = ClassReader(
                classLoader.getResourceAsStream(className.replace(".", "/") + ".class")
            )
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
            logger.error { e.stackTraceToString() }
        }
        return classfileBuffer
    }

    private fun transformClass(classNode: ClassNode) {
        for (method in classNode.methods) {
            try {
                transformMethod(method, classNode.name)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun transformMethod(methodNode: MethodNode, owner: String) {
        if (methods.isNotEmpty() &&
            "${owner.replace("/", ".")}::${methodNode.name}" !in methods) {
            return
        }
        if (methodNode.name == "<clinit>") {
            return
        }
        if (methodNode.localVariables == null) {
            return
        }
        try {
//            TestCaseRecordVisitor(owner, methodNode.access, methodNode.name, methodNode.desc, null)
//                .analyze(methodNode.instructions)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val notFirstTimeVars = hashSetOf<LocalVariableNode>()
        val availableVars = hashMapOf<Int, LocalVariableNode>()
        val wroteOrFirstVisitVars = hashSetOf<LocalVariableNode>()
        val startVarMap = hashMapOf<LabelNode, HashMap<Int, LocalVariableNode>>()
        val endVarMap = hashMapOf<LabelNode, HashMap<Int, LocalVariableNode>>()
        for (localVar in methodNode.localVariables) {
            if (localVar.start !in startVarMap) {
                startVarMap[localVar.start] = HashMap()
            }
            if (localVar.end !in endVarMap) {
                endVarMap[localVar.end] = HashMap()
            }
            startVarMap[localVar.start]!![localVar.index] = localVar
            endVarMap[localVar.end]!![localVar.index] = localVar
        }
        for (inst in methodNode.instructions) {
            if (inst is LabelNode) {
                if (inst in startVarMap) {
                    val varNodes = startVarMap[inst]!!
                    availableVars.putAll(varNodes)
                }
                if (inst in endVarMap) {
                    val varNodes = endVarMap[inst]!!
                    for ((index, _) in varNodes) {
                        availableVars.remove(index)
                    }
                }
            }
            if (changedLocalVarsOnly) {
                if (inst is VarInsnNode) {
                    if (availableVars.containsKey(inst.`var`)) {
                        val localVar = availableVars[inst.`var`]!!
                        if (inst.isWriteLocalVar()) {
                            wroteOrFirstVisitVars.add(localVar)
                        }
                    }
                }
            }
            if (inst is LineNumberNode) {
                if (availableVars.isEmpty()) {
                    continue
                }
                val insnList = if (changedLocalVarsOnly) {
                    for ((_, varNode) in availableVars) {
                        if (varNode !in notFirstTimeVars) {
                            notFirstTimeVars.add(varNode)
                            wroteOrFirstVisitVars.add(varNode)
                        }
                    }
                    generateMonitorLocalVar(inst.line, wroteOrFirstVisitVars)
                } else {
                    generateMonitorLocalVar(inst.line, availableVars.values)
                }
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
                if (changedLocalVarsOnly) {
                    wroteOrFirstVisitVars.clear()
                }
            }
        }

        insertEnterMonitor(owner, methodNode)
    }

    private fun insertEnterMonitor(owner: String, methodNode: MethodNode) {
        val methodName = "${owner.replace("/", ".")}::${methodNode.name}"
        val printNameInsnList = InsnList().apply {
            add(LdcInsnNode(methodName))
            add(
                MethodInsnNode(
                    INVOKESTATIC,
                    Type.getInternalName(DetectMonitor::class.java),
                    DetectMonitor::monitorEnterMethod.name,
                    "(Ljava/lang/String;)V"
                )
            )
        }
        methodNode.instructions.first?.apply {
            methodNode.instructions.insertBefore(this, printNameInsnList)
        } ?: methodNode.instructions.insert(printNameInsnList)
    }

    private fun generateMonitorLocalVar(
        line: Int,
        visitedVars: Collection<LocalVariableNode>,
    ): InsnList {
        val list = InsnList()
        if (visitedVars.isEmpty() || visitedVars.all { it.name == "this" }) {
            return list
        }
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
        for (localVar in visitedVars) {
            if (localVar.name == "this") {
                continue
            }
            list.add(InsnNode(DUP))
            list.add(LdcInsnNode(localVar.name))
            list.add(loadVar(localVar))
            val boxIfNeed = boxIfNeed(localVar.desc)
            if (boxIfNeed != null) {
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
        list.add(
            MethodInsnNode(
                AdviceAdapter.INVOKESTATIC, Type.getInternalName(DetectMonitor::class.java),
                DetectMonitor::monitorLocalVar.name,
                "(ILjava/util/HashMap;)V", false
            )
        )
        return list
    }
}