package com.github.zarechenskiy.explicitboxing

import com.github.zarechenskiy.explicitboxing.bytecode.BoxingKind
import com.github.zarechenskiy.explicitboxing.bytecode.BoxingSite
import com.github.zarechenskiy.explicitboxing.bytecode.CompiledClassesIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Path

/**
 * Every line expected to box is marked with a `// box: <text>` comment, where `<text>` must be a part of
 * the description of a boxing site found on that line. Lines without such a comment must not box.
 */
class BoxingBytecodeAnalyzerTest {

    companion object {
        private val SOURCES = mapOf(
            "sample/Main.kt" to """
                package sample

                @JvmInline
                value class UserId(val id: Int)

                interface Shape

                @JvmInline
                value class Meters(val value: Double) : Shape

                fun primitives() {
                    val list = listOf(1, 2) // box: Int → java.lang.Integer
                    val any: Any = 42L // box: Long → java.lang.Long
                    val nullable: Int? = list.size // box: Int → java.lang.Integer
                    val sum = list.size + 2
                    println(any.toString() + nullable + sum)
                }

                fun valueClasses(id: UserId) {
                    val ids: List<UserId> = listOf(id) // box: value class sample.UserId (over Int)
                    val shape: Shape = Meters(1.0) // box: value class sample.Meters (over Double)
                    takeId(id)
                    println(ids.toString() + shape)
                }

                fun takeId(id: UserId): UserId = id

                fun unsigned(): Any = 5u // box: value class kotlin.UInt (over Int)

                fun inlinedFromOtherFile(): Any = 7.asAny() // box: Int → java.lang.Integer

                suspend fun suspending(): Int = 1 // box: Int → java.lang.Integer
            """,
            "sample/Util.kt" to """
                package sample

                inline fun Int.asAny(): Any = this // box: Int → java.lang.Integer
            """,
        )

        private lateinit var output: Path

        @BeforeClass
        @JvmStatic
        fun compile() {
            output = KotlinTestCompiler.compile(SOURCES)
        }

        private fun sitesOf(fileName: String): List<BoxingSite> =
            CompiledClassesIndex().collect(listOf(output), "sample", fileName).sites

        private fun expectedBoxing(fileName: String): Map<Int, String> =
            SOURCES.getValue("sample/$fileName").trimIndent().lines()
                .withIndex()
                .filter { "// box: " in it.value }
                .associate { (index, line) -> index + 1 to line.substringAfter("// box: ").trim() }
    }

    @Test
    fun `boxing is found exactly on expected lines of Main kt`() = checkFile("Main.kt")

    @Test
    fun `boxing is found exactly on expected lines of Util kt`() = checkFile("Util.kt")

    @Test
    fun `boxing inside inlined code is attributed to the call site`() {
        val line = expectedBoxing("Main.kt").entries.single { "asAny" in SOURCES.getValue("sample/Main.kt").trimIndent().lines()[it.key - 1] }.key
        val site = sitesOf("Main.kt").single { it.line == line }
        assertEquals(BoxingKind.PRIMITIVE, site.kind)
        assertEquals("Util.kt:3", site.inlinedFrom)
    }

    @Test
    fun `value class boxing reports the box-impl call`() {
        val site = sitesOf("Main.kt").first { it.boxedType == "sample.UserId" }
        assertEquals(BoxingKind.VALUE_CLASS, site.kind)
        assertEquals("UserId.box-impl", site.call)
        assertEquals("Int", site.underlyingType)
    }

    private fun checkFile(fileName: String) {
        val expected = expectedBoxing(fileName)
        val actual = sitesOf(fileName).groupBy { it.line }
        assertEquals(
            "Lines with boxing in $fileName; actual sites: ${actual.values.flatten().joinToString("\n", "\n")}",
            expected.keys.sorted(), actual.keys.sorted(),
        )
        for ((line, text) in expected) {
            val descriptions = actual.getValue(line).map { it.description }
            assertTrue("Line $line: expected '$text' in $descriptions", descriptions.any { text in it })
        }
    }
}
