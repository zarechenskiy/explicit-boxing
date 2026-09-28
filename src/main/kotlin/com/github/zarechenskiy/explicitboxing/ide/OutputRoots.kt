package com.github.zarechenskiy.explicitboxing.ide

import com.github.zarechenskiy.explicitboxing.bytecode.CompiledClassesIndex
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** Collects directories that may contain classes compiled from a source file. Must be called under a read action. */
object OutputRoots {
    private const val MAX_PARENT_DEPTH = 12

    fun forFile(project: Project, file: VirtualFile): List<Path> {
        val roots = LinkedHashSet<Path>()

        val module = ModuleUtilCore.findModuleForFile(file, project)
        if (module != null) {
            CompilerModuleExtension.getInstance(module)?.let { extension ->
                listOfNotNull(extension.compilerOutputUrl, extension.compilerOutputUrlForTests)
                    .mapNotNullTo(roots) { url -> runCatching { Path.of(VfsUtilCore.urlToPath(url)) }.getOrNull() }
            }
            // Gradle-imported modules point to build/classes/java/<sourceSet>, while Kotlin compiles into build/classes/kotlin/<sourceSet>
            for (root in roots.toList()) {
                val languageDir = root.parent ?: continue
                if (languageDir.name == "java" && languageDir.parent?.name == "classes") {
                    roots.add(languageDir.resolveSibling("kotlin").resolve(root.name))
                }
            }
        }

        // Walk up from the file and look for conventional build tool outputs
        val stopAt = buildSet {
            project.basePath?.let { add(Path.of(it)) }
            module?.let { ModuleRootManager.getInstance(it).contentRoots.mapNotNullTo(this) { root -> root.toNioPathOrNull()?.parent } }
        }
        var dir: Path? = file.parent?.toNioPathOrNull()
        var depth = 0
        while (dir != null && depth++ < MAX_PARENT_DEPTH) {
            roots.addAll(CompiledClassesIndex.conventionalOutputRoots(dir))
            if (dir in stopAt) break
            dir = dir.parent
        }

        return roots.filter { it.isDirectory() }
    }

    private fun VirtualFile.toNioPathOrNull(): Path? = if (isInLocalFileSystem) runCatching { toNioPath() }.getOrNull() else null
}
