package com.github.xyzboom.visitors

import com.github.xyzboom.DetectMonitor
import com.github.xyzboom.modified.asm.AnalyzerAdapter
import com.github.xyzboom.utils.boxIfNeed
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode

class TestCaseRecordVisitor(
    owner: String?,
    access: Int,
    name: String?,
    descriptor: String?,
    methodVisitor: MethodVisitor?
) : AnalyzerAdapter(ASM9, owner, access, name, descriptor, methodVisitor) {

    companion object {
        val assertEqualsObjectsDescriptor = setOf(
            "(Ljava/lang/Object;Ljava/lang/Object;)V",
            "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"
        )
    }
    var currentInsnNode: AbstractInsnNode? = null
    val stackChangedNodes = ArrayList<Pair<Int, AbstractInsnNode>>()

    /**
     * [copiedStack] here is used to record stack size.
     * long and double in [copiedStack] represent only one element.
     * 用于记录当前栈大小。
     * long和double类型在这里只占用一个元素。
     */
    val copiedStack = ArrayList<Any?>()
    var stackChanged = false
    var stackChangedSize = 0
    fun analyze(insnList: InsnList) {
        for (node in insnList) {
            currentInsnNode = node
            stackChanged = false
            stackChangedSize = 0
            if (node is MethodInsnNode) {
                if (node.owner == "org/junit/Assert" && node.name == "assertEquals"
                    && node.desc in assertEqualsObjectsDescriptor
                ) {
                    val (monitorInsnList, insertNode) = generateMonitorTestCase() ?: continue
                    insnList.insertBefore(insertNode, monitorInsnList)
                }
            }
            node.accept(this)
            if (stackChanged) {
                stackChangedNodes += stackChangedSize to node
            }
        }
    }

    override fun pop(numSlots: Int) {
        stackChanged = true
        val size = stack.size
        val end = size - numSlots
        var changedSize = 0
        for (i in size - 1 downTo end) {
            if (stack[i] != TOP) {
                changedSize++
                copiedStack.removeLast()
            }
        }
        stackChangedSize -= changedSize
        super.pop(numSlots)
    }

    override fun push(type: Any?) {
        stackChanged = true
        if (type != TOP) {
            stackChangedSize++
            copiedStack.add(type)
        }
        super.push(type)
    }

    private fun generateMonitorTestCase(): Pair<InsnList, AbstractInsnNode>? {
        val resultList = InsnList()
        var sizeNow = 2
        var resultNode: AbstractInsnNode? = null
        for ((size, node) in stackChangedNodes.asReversed()) {
            sizeNow -= size
            if (sizeNow == 1) {
                resultNode = node
                break
            }
        }
        if (resultNode == null) {
            return null
        }
        with(resultList) {
            if (copiedStack.last() == LONG || copiedStack.last() == DOUBLE) {
                add(InsnNode(DUP2))
            } else {
                add(InsnNode(DUP))
            }
            val boxIfNeed = boxIfNeed(stack[stack.size - 2].toString())
            if (boxIfNeed != null) {
                add(boxIfNeed)
            }
            add(
                MethodInsnNode(
                    INVOKESTATIC, Type.getInternalName(DetectMonitor::class.java),
                    DetectMonitor::monitorTestCase.name,
                    "(Ljava/lang/Object;)V", false
                )
            )
        }
        return resultList to resultNode
    }
}