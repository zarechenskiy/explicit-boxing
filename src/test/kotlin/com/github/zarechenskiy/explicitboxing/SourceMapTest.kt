package com.github.zarechenskiy.explicitboxing

import com.github.zarechenskiy.explicitboxing.bytecode.SourceMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceMapTest {
    private val smap = SourceMap.parse(
        """
        SMAP
        Main.kt
        Kotlin
        *S Kotlin
        *F
        + 1 Main.kt
        sample/MainKt
        + 2 Util.kt
        sample/UtilKt
        *L
        1#1,20:1
        3#2:21
        5#2,2:22,3
        *E
        *S KotlinDebug
        *F
        + 1 Main.kt
        sample/MainKt
        *L
        12#1:21
        14#1:22,6
        *E
        """.trimIndent()
    )!!

    @Test
    fun `lines of the own file map to themselves`() {
        assertEquals(SourceMap.MappedLine("Main.kt", 7, isOwnFile = true), smap.mapToSource(7))
        assertNull(smap.mapToCallSite(7))
    }

    @Test
    fun `inlined lines map to the inline function and to the call site`() {
        assertEquals(SourceMap.MappedLine("Util.kt", 3, isOwnFile = false), smap.mapToSource(21))
        assertEquals(12, smap.mapToCallSite(21)?.line)
    }

    @Test
    fun `line increments are respected`() {
        assertEquals(5, smap.mapToSource(24)?.line)
        assertEquals(6, smap.mapToSource(25)?.line)
        assertEquals(14, smap.mapToCallSite(27)?.line)
        assertNull(smap.mapToSource(28))
    }

    @Test
    fun `not an smap`() {
        assertNull(SourceMap.parse("garbage"))
        assertNull(SourceMap.parse(null))
    }
}
