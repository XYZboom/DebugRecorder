package com.github.xyzboom.visitors

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class DetectClassVisitor(classWriter: ClassWriter) : ClassVisitor(Opcodes.ASM9, classWriter) {
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val mv = cv.visitMethod(access, name, descriptor, signature, exceptions)
        val methodVisitor = DetectMethodVisitor(mv, access, name, descriptor)
        return methodVisitor
    }
}