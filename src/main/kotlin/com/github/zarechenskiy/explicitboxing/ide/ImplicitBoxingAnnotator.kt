package com.github.zarechenskiy.explicitboxing.ide

import com.github.zarechenskiy.explicitboxing.bytecode.BoxingSite
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the compiled bytecode of a Kotlin file and marks lines where boxing happens with a gutter icon.
 */
class ImplicitBoxingAnnotator : ExternalAnnotator<ImplicitBoxingAnnotator.Input, ImplicitBoxingAnnotator.Result>(), DumbAware {

    class Input(
        val packageFqName: String,
        val fileName: String,
        val sourcePath: Path?,
        val hasUnsavedChanges: Boolean,
        val outputRoots: List<Path>,
    )

    class Result(val sitesByLine: Map<Int, List<BoxingSite>>, val outdated: Boolean)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? = collectInformation(file)

    override fun collectInformation(file: PsiFile): Input? {
        if (file !is KtFile || file.isScript()) return null
        val virtualFile = file.virtualFile ?: return null
        if (!virtualFile.isInLocalFileSystem) return null
        val roots = OutputRoots.forFile(file.project, virtualFile)
        if (roots.isEmpty()) return null
        return Input(
            packageFqName = file.packageFqName.asString(),
            fileName = virtualFile.name,
            sourcePath = runCatching { virtualFile.toNioPath() }.getOrNull(),
            hasUnsavedChanges = FileDocumentManager.getInstance().isFileModified(virtualFile),
            outputRoots = roots,
        )
    }

    override fun doAnnotate(input: Input): Result? {
        val info = BoxingService.getInstance().index.collect(input.outputRoots, input.packageFqName, input.fileName)
        val newestClass = info.newestClassTimestamp ?: return null
        if (info.sites.isEmpty()) return null
        val sourceTimestamp = input.sourcePath?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrNull() } ?: 0L
        val outdated = input.hasUnsavedChanges || sourceTimestamp > newestClass
        return Result(info.sites.groupBy { it.line }, outdated)
    }

    override fun apply(file: PsiFile, result: Result, holder: AnnotationHolder) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        val text = document.charsSequence
        for ((line, sites) in result.sitesByLine) {
            val lineIndex = line - 1
            if (lineIndex < 0 || lineIndex >= document.lineCount) continue
            val lineStart = document.getLineStartOffset(lineIndex)
            val lineEnd = document.getLineEndOffset(lineIndex)
            var start = lineStart
            while (start < lineEnd && text[start].isWhitespace()) start++
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(if (start < lineEnd) start else lineStart, lineEnd))
                .gutterIconRenderer(BoxingGutterIconRenderer(line, sites, result.outdated))
                .create()
        }
    }
}
