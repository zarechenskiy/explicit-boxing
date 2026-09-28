package com.github.zarechenskiy.explicitboxing.bytecode

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** Boxing sites of one source file, collected from all the class files compiled from it. */
data class FileBoxingInfo(
    val sites: List<BoxingSite>,
    /** Modification time of the newest class file compiled from the source, or `null` if none was found. */
    val newestClassTimestamp: Long?,
)

/**
 * Finds class files compiled from a given source file inside output directories and extracts boxing sites from them.
 * Parsed class files are cached by path, size and modification time.
 */
class CompiledClassesIndex {
    private class CachedClass(val timestamp: Long, val size: Long, val info: ClassBoxingInfo?)

    private val cache = ConcurrentHashMap<Path, CachedClass>()

    /**
     * @param outputRoots candidate compiler output directories (class path roots)
     * @param packageFqName package of the source file, e.g. `com.example`
     * @param sourceFileName short name of the source file, e.g. `Foo.kt`
     */
    fun collect(outputRoots: Collection<Path>, packageFqName: String, sourceFileName: String): FileBoxingInfo {
        val packagePath = packageFqName.replace('.', '/')
        // The same class can be present in several output roots (e.g. a stale `out` next to `build`): keep the newest one
        val classes = mutableMapOf<String, Pair<Long, ClassBoxingInfo>>()

        for (root in outputRoots) {
            val packageDir = if (packagePath.isEmpty()) root else root.resolve(packagePath)
            if (!packageDir.isDirectory()) continue
            val classFiles = try {
                Files.list(packageDir).use { stream -> stream.filter { it.extension == "class" }.toList() }
            } catch (_: java.io.IOException) {
                continue
            }
            for (classFile in classFiles) {
                val (timestamp, info) = load(classFile) ?: continue
                if (info.sourceFile != sourceFileName) continue
                val existing = classes[info.internalName]
                if (existing == null || existing.first < timestamp) classes[info.internalName] = timestamp to info
            }
        }

        val sites = classes.values.flatMap { it.second.sites }.sortedWith(compareBy({ it.line }, { it.method }))
        return FileBoxingInfo(sites, classes.values.maxOfOrNull { it.first })
    }

    private fun load(classFile: Path): Pair<Long, ClassBoxingInfo>? {
        val file = classFile.toFile()
        val timestamp = file.lastModified()
        val size = file.length()
        val cached = cache[classFile]
        if (cached != null && cached.timestamp == timestamp && cached.size == size) {
            return cached.info?.let { timestamp to it }
        }
        val info = try {
            BoxingBytecodeAnalyzer.analyze(Files.readAllBytes(classFile))
        } catch (_: Exception) {
            null // unreadable or being written right now
        }
        cache[classFile] = CachedClass(timestamp, size, info)
        return info?.let { timestamp to it }
    }

    fun clear() = cache.clear()

    companion object {
        /**
         * Conventional output directories of popular build tools that can hold classes of sources located at or under [dir].
         */
        fun conventionalOutputRoots(dir: Path): List<Path> {
            val result = mutableListOf<Path>()
            // Gradle: build/classes/{kotlin,java}/<sourceSet>, and for KMP build/classes/kotlin/<target>/<compilation>
            for (language in listOf("kotlin", "java")) {
                val languageDir = dir.resolve("build/classes/$language")
                for (child in children(languageDir)) {
                    result.add(child)
                    result.addAll(children(child).filter { it.name == "main" || it.name == "test" })
                }
            }
            // Maven
            result.addAll(listOf(dir.resolve("target/classes"), dir.resolve("target/test-classes")).filter { it.isDirectory() })
            // IntelliJ (JPS)
            result.addAll(children(dir.resolve("out/production")) + children(dir.resolve("out/test")))
            return result
        }

        private fun children(dir: Path): List<Path> {
            if (!dir.isDirectory()) return emptyList()
            return try {
                Files.list(dir).use { stream -> stream.filter { it.isDirectory() }.toList() }
            } catch (_: java.io.IOException) {
                emptyList()
            }
        }
    }
}
