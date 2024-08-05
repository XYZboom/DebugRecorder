package com.github.xyzboom.utils

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode

fun boxIfNeed(desc: String): MethodInsnNode? {
    return when (desc) {
        Type.INT_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Integer",
                "valueOf", "(I)Ljava/lang/Integer;", false
            )

        Type.BOOLEAN_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Boolean",
                "valueOf", "(Z)Ljava/lang/Boolean;", false
            )

        Type.LONG_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Long",
                "valueOf", "(J)Ljava/lang/Long;", false
            )

        Type.DOUBLE_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Double",
                "valueOf", "(D)Ljava/lang/Double;", false
            )

        Type.FLOAT_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Float",
                "valueOf", "(F)Ljava/lang/Float;", false
            )

        Type.SHORT_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Short",
                "valueOf", "(S)Ljava/lang/Short;", false
            )

        Type.CHAR_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Character",
                "valueOf", "(C)Ljava/lang/Character;", false
            )

        Type.BYTE_TYPE.descriptor ->
            MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Byte",
                "valueOf", "(B)Ljava/lang/Byte;", false
            )

        else -> null
    }
}

fun loadVar(localVar: LocalVariableNode): VarInsnNode {
    val code = when (localVar.desc) {
        Type.INT_TYPE.descriptor, Type.SHORT_TYPE.descriptor,
        Type.BOOLEAN_TYPE.descriptor, Type.CHAR_TYPE.descriptor,
        Type.BYTE_TYPE.descriptor -> Opcodes.ILOAD

        Type.LONG_TYPE.descriptor -> Opcodes.LLOAD

        Type.DOUBLE_TYPE.descriptor -> Opcodes.DLOAD

        Type.FLOAT_TYPE.descriptor -> Opcodes.FLOAD
        else -> Opcodes.ALOAD
    }
    return VarInsnNode(code, localVar.index)
}

fun VarInsnNode.isWriteLocalVar(): Boolean {
    return when (opcode) {
        Opcodes.ISTORE,
        Opcodes.LSTORE,
        Opcodes.FSTORE,
        Opcodes.DSTORE,
        Opcodes.ASTORE -> {
            // 检查操作数是否是一个局部变量的索引
            `var` >= 0
        }

        else -> false
    }
}