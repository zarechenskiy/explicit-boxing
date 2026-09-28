package com.github.zarechenskiy.explicitboxing

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Compiles Kotlin sources with a standalone Kotlin compiler run in a separate process,
 * so it doesn't interfere with the Kotlin plugin classes of the IDE test environment.
 */
object KotlinTestCompiler {
    private val compilerClasspath: List<File> by lazy {
        val property = System.getProperty("test.kotlin.compiler.classpath")
            ?: error("'test.kotlin.compiler.classpath' system property is not set; run tests via Gradle")
        property.split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)
    }

    private val stdlib: File by lazy {
        compilerClasspath.first { it.name.matches(Regex("kotlin-stdlib-\\d.*\\.jar")) }
    }

    /** Compiles [sources] (file name to content) and returns the output directory. */
    fun compile(sources: Map<String, String>, outputDir: Path = Files.createTempDirectory("boxing-out")): Path {
        val sourceDir = Files.createTempDirectory("boxing-src")
        val sourceFiles = sources.map { (name, text) ->
            sourceDir.resolve(name).also {
                Files.createDirectories(it.parent)
                Files.writeString(it, text.trimIndent())
            }
        }

        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val command = listOf(
            java, "-cp", compilerClasspath.joinToString(File.pathSeparator),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect",
            "-classpath", stdlib.path,
            "-d", outputDir.toString(),
        ) + sourceFiles.map { it.toString() }

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(5, TimeUnit.MINUTES)) { "Kotlin compiler timed out" }
        check(process.exitValue() == 0) { "Kotlin compilation failed:\n$output" }
        return outputDir
    }
}
