package com.github.zarechenskiy.explicitboxing.ide

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager

/** Re-highlights files once a build (JPS or delegated to Gradle/Maven) finishes, so gutter icons reflect fresh bytecode. */
class BoxingBuildListener(private val project: Project) : ProjectTaskListener {
    override fun finished(result: ProjectTaskManager.Result) {
        if (!result.isAborted) restartHighlighting(project)
    }
}

/** Re-highlights files when class files change on disk, e.g. after a build started outside of the IDE build system. */
class BoxingClassFilesListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.none { it.fileSystem is LocalFileSystem && it.path.endsWith(".class") }) return
        for (project in ProjectManager.getInstance().openProjects) {
            restartHighlighting(project)
        }
    }
}

private fun restartHighlighting(project: Project) {
    if (project.isDisposed) return
    DaemonCodeAnalyzer.getInstance(project).restart("Compiled classes changed; refreshing implicit boxing gutter icons")
}
