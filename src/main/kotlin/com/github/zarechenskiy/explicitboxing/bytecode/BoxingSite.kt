package com.github.zarechenskiy.explicitboxing.bytecode

enum class BoxingKind { PRIMITIVE, VALUE_CLASS }

/**
 * A single boxing instruction found in the bytecode.
 *
 * @param line 1-based line in the source file the class was compiled from
 * @param boxedType human-readable type of the unboxed value, e.g. `Int` or `com.example.UserId`
 * @param boxType JVM class the value is boxed into, e.g. `java.lang.Integer` or `com.example.UserId`
 * @param underlyingType for value classes, the type of the underlying property, e.g. `Int`
 * @param call the boxing method invoked, e.g. `Integer.valueOf` or `UserId.box-impl`
 * @param method the method (in JVM terms) containing the instruction, e.g. `Foo.bar`
 * @param inlinedFrom `File.kt:line` when the instruction comes from an inlined function body
 */
data class BoxingSite(
    val line: Int,
    val kind: BoxingKind,
    val boxedType: String,
    val boxType: String,
    val underlyingType: String?,
    val call: String,
    val method: String,
    val inlinedFrom: String?,
) {
    val description: String
        get() = when (kind) {
            BoxingKind.PRIMITIVE -> "$boxedType → $boxType"
            BoxingKind.VALUE_CLASS -> "value class $boxedType${underlyingType?.let { " (over $it)" }.orEmpty()} → boxed"
        }
}

/** Boxing information extracted from one class file. */
data class ClassBoxingInfo(
    val internalName: String,
    val sourceFile: String?,
    val sites: List<BoxingSite>,
)
