package com.github.xyzboom

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.commons.AdviceAdapter

class DetectMethodVisitor(mv: MethodVisitor, access: Int, name: String?, desc: String?) :
    AdviceAdapter(ASM9, mv, access, name, desc) {

    override fun visitLineNumber(line: Int, start: Label?) {
        super.visitLineNumber(line, start)
        generateMonitorLocalVar(line)
    }

    private fun generateMonitorLocalVar(line: Int) {
        if (firstLocal == nextLocal) return
        with(mv) {
            push(line)
            push(nextLocal - firstLocal)
            visitTypeInsn(ANEWARRAY, "java/lang/Object")
            for (i in firstLocal until nextLocal) {
                visitInsn(DUP)
                push(i - firstLocal)
                visitVarInsn(ALOAD, i)
                visitInsn(AASTORE)
            }
            visitMethodInsn(
                INVOKESTATIC, "com/github/xyzboom/DetectMonitor",
                "monitorLocalVar", "(I[Ljava/lang/Object;)V", false
            )
        }
    }
}