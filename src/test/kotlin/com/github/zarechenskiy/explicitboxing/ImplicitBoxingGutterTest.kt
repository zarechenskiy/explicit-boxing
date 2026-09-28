package com.github.zarechenskiy.explicitboxing

import com.github.zarechenskiy.explicitboxing.ide.BoxingGutterIconRenderer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.nio.file.Path

class ImplicitBoxingGutterTest : BasePlatformTestCase() {

    // Real files on disk: the annotator looks for build outputs next to the sources
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    fun testGutterIconsOnBoxingLines() {
        val source = """
            package sample

            @JvmInline
            value class UserId(val id: Int)

            fun foo(id: UserId): Any {
                val ids = listOf(id)
                val n = ids.size + 1
                return n
            }
        """.trimIndent()

        val file = myFixture.addFileToProject("sample/Main.kt", source).virtualFile
        KotlinTestCompiler.compile(mapOf("sample/Main.kt" to source), Path.of(myFixture.tempDirPath).resolve("build/classes/kotlin/main"))
        val sourceRoot = file.parent.parent
        VfsUtil.markDirtyAndRefresh(false, true, true, sourceRoot)

        // Kotlin doesn't highlight files outside source roots
        PsiTestUtil.addSourceRoot(module, sourceRoot)
        try {
            myFixture.configureFromExistingVirtualFile(file)

            setSourceModified(file, System.currentTimeMillis() - 60_000)
            val tooltips = boxingTooltips()
            assertEquals(tooltips.joinToString("\n"), 2, tooltips.size)
            assertTrue(tooltips.joinToString("\n"), tooltips.any { "line 7" in it && "value class sample.UserId (over Int)" in it && "UserId.box-impl" in it })
            assertTrue(tooltips.joinToString("\n"), tooltips.any { "line 9" in it && "Int → java.lang.Integer" in it && "Integer.valueOf" in it })
            assertTrue(tooltips.none { "outdated" in it })

            setSourceModified(file, System.currentTimeMillis() + 60_000)
            DaemonCodeAnalyzer.getInstance(project).restart("source timestamp changed")
            val outdatedTooltips = boxingTooltips()
            assertEquals(2, outdatedTooltips.size)
            assertTrue(outdatedTooltips.all { "outdated" in it })
        } finally {
            PsiTestUtil.removeSourceRoot(module, sourceRoot)
        }
    }

    private fun setSourceModified(file: VirtualFile, timestamp: Long) {
        check(file.toNioPath().toFile().setLastModified(timestamp))
    }

    private fun boxingTooltips(): List<String> =
        myFixture.findAllGutters().filterIsInstance<BoxingGutterIconRenderer>()
            // the fixture reports each renderer both from highlight infos and from the markup model
            .distinct()
            .map { it.tooltipText }
}
