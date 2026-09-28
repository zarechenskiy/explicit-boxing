package com.github.zarechenskiy.explicitboxing.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Finds boxing operations in compiled class files:
 *  - primitive boxing: `Integer.valueOf(int)` and friends, plus `kotlin.coroutines.jvm.internal.Boxing.box*`
 *    used by the compiler inside suspend functions;
 *  - value class boxing: calls to the synthetic `box-impl` method generated for every `value class`.
 */
object BoxingBytecodeAnalyzer {
    const val VALUE_CLASS_BOX_METHOD = "box-impl"

    private const val COROUTINES_BOXING = "kotlin/coroutines/jvm/internal/Boxing"

    private val PRIMITIVE_TO_KOTLIN = mapOf(
        Type.BOOLEAN_TYPE to "Boolean",
        Type.BYTE_TYPE to "Byte",
        Type.CHAR_TYPE to "Char",
        Type.SHORT_TYPE to "Short",
        Type.INT_TYPE to "Int",
        Type.LONG_TYPE to "Long",
        Type.FLOAT_TYPE to "Float",
        Type.DOUBLE_TYPE to "Double",
    )

    private val PRIMITIVE_TO_WRAPPER = mapOf(
        Type.BOOLEAN_TYPE to "java/lang/Boolean",
        Type.BYTE_TYPE to "java/lang/Byte",
        Type.CHAR_TYPE to "java/lang/Character",
        Type.SHORT_TYPE to "java/lang/Short",
        Type.INT_TYPE to "java/lang/Integer",
        Type.LONG_TYPE to "java/lang/Long",
        Type.FLOAT_TYPE to "java/lang/Float",
        Type.DOUBLE_TYPE to "java/lang/Double",
    )

    fun analyze(classBytes: ByteArray): ClassBoxingInfo {
        val node = ClassNode()
        ClassReader(classBytes).accept(node, ClassReader.SKIP_FRAMES)

        val sourceMap = SourceMap.parse(node.sourceDebug)
        val className = displayName(node.name)
        val sites = mutableListOf<BoxingSite>()

        for (method in node.methods) {
            // The body of `box-impl` itself only calls the constructor, but don't let its own recursion confuse us
            if (method.name == VALUE_CLASS_BOX_METHOD) continue
            val methodName = "${className.substringAfterLast('.')}.${method.name}"

            var bytecodeLine = -1
            for (insn in method.instructions) {
                when (insn) {
                    is LineNumberNode -> bytecodeLine = insn.line
                    is MethodInsnNode -> {
                        if (insn.opcode != Opcodes.INVOKESTATIC || bytecodeLine <= 0) continue
                        val site = recognize(insn) ?: continue
                        val location = resolveLine(bytecodeLine, sourceMap) ?: continue
                        sites += site(location.line, methodName, location.inlinedFrom)
                    }
                }
            }
        }
        return ClassBoxingInfo(node.name, node.sourceFile, sites)
    }

    private class Location(val line: Int, val inlinedFrom: String?)

    private fun resolveLine(bytecodeLine: Int, sourceMap: SourceMap?): Location? {
        if (sourceMap == null) return Location(bytecodeLine, null)
        val source = sourceMap.mapToSource(bytecodeLine)
        val callSite = sourceMap.mapToCallSite(bytecodeLine)
        if (callSite != null) {
            return Location(callSite.line, source?.let { "${it.fileName}:${it.line}" })
        }
        return when {
            source == null -> Location(bytecodeLine, null)
            source.isOwnFile -> Location(source.line, null)
            // Inlined code from another file without a known call site: nothing to attach it to
            else -> null
        }
    }

    private fun recognize(insn: MethodInsnNode): ((Int, String, String?) -> BoxingSite)? {
        val args = Type.getArgumentTypes(insn.desc)
        if (args.size != 1) return null
        val arg = args[0]
        val returnType = Type.getReturnType(insn.desc)
        if (returnType.sort != Type.OBJECT) return null

        val wrapper = PRIMITIVE_TO_WRAPPER[arg]
        if (wrapper != null && returnType.internalName == wrapper &&
            ((insn.owner == wrapper && insn.name == "valueOf") || (insn.owner == COROUTINES_BOXING && insn.name.startsWith("box")))
        ) {
            val call = "${insn.owner.substringAfterLast('/')}.${insn.name}"
            return { line, method, inlinedFrom ->
                BoxingSite(line, BoxingKind.PRIMITIVE, PRIMITIVE_TO_KOTLIN.getValue(arg), displayName(wrapper), null, call, method, inlinedFrom)
            }
        }

        if (insn.name == VALUE_CLASS_BOX_METHOD && returnType.internalName == insn.owner) {
            val valueClass = displayName(insn.owner)
            val call = "${valueClass.substringAfterLast('.')}.$VALUE_CLASS_BOX_METHOD"
            return { line, method, inlinedFrom ->
                BoxingSite(line, BoxingKind.VALUE_CLASS, valueClass, valueClass, typeName(arg), call, method, inlinedFrom)
            }
        }
        return null
    }

    private fun typeName(type: Type): String =
        PRIMITIVE_TO_KOTLIN[type] ?: displayName(type.internalName).let { if (it.startsWith("java.lang.")) it.removePrefix("java.lang.") else it }

    private fun displayName(internalName: String): String = internalName.replace('/', '.').replace('$', '.')
}
