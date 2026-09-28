package com.github.zarechenskiy.explicitboxing.bytecode

/**
 * Minimal JSR-45 (SMAP) parser for the `SourceDebugExtension` attribute that the Kotlin compiler emits
 * when function bodies get inlined.
 *
 * The `Kotlin` stratum maps bytecode lines to the file the code was written in (possibly another file,
 * for code of inline functions), and the `KotlinDebug` stratum maps lines of inlined code to the line of
 * the call site.
 */
class SourceMap private constructor(
    private val strata: Map<String, Stratum>,
) {
    data class MappedLine(val fileName: String, val line: Int, val isOwnFile: Boolean)

    private class Stratum(val files: Map<Int, String>, val lines: List<LineRange>) {
        val ownFileId: Int? = files.keys.minOrNull()

        fun map(outputLine: Int): MappedLine? {
            for (range in lines) {
                val offset = outputLine - range.outputStart
                // an increment of 0 maps all input lines of the range onto a single output line
                if (offset < 0 || offset >= maxOf(range.repeat * range.increment, 1)) continue
                val fileName = files[range.fileId] ?: continue
                val inputLine = if (range.increment == 0) range.inputStart else range.inputStart + offset / range.increment
                return MappedLine(fileName, inputLine, range.fileId == ownFileId)
            }
            return null
        }
    }

    private class LineRange(val inputStart: Int, val fileId: Int, val repeat: Int, val outputStart: Int, val increment: Int)

    /** Where [outputLine] of the class file originates from, according to the `Kotlin` stratum. */
    fun mapToSource(outputLine: Int): MappedLine? = strata[KOTLIN]?.map(outputLine)

    /** The call site line in the class's own source file if [outputLine] belongs to inlined code. */
    fun mapToCallSite(outputLine: Int): MappedLine? = strata[KOTLIN_DEBUG]?.map(outputLine)

    companion object {
        private const val KOTLIN = "Kotlin"
        private const val KOTLIN_DEBUG = "KotlinDebug"

        fun parse(text: String?): SourceMap? {
            if (text == null) return null
            val lines = text.lineSequence().map { it.trim() }.toList()
            if (lines.firstOrNull() != "SMAP") return null

            val strata = mutableMapOf<String, Stratum>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (!line.startsWith("*S ")) {
                    i++
                    continue
                }
                val name = line.removePrefix("*S ").trim()
                i++
                val files = mutableMapOf<Int, String>()
                val ranges = mutableListOf<LineRange>()
                var section = ""
                var lastFileId = 0
                while (i < lines.size && !lines[i].startsWith("*S ") && lines[i] != "*E") {
                    val current = lines[i]
                    when {
                        current.startsWith("*") -> section = current
                        section == "*F" -> {
                            val withPath = current.startsWith("+ ")
                            val parts = current.removePrefix("+ ").trim().split(' ', limit = 2)
                            val id = parts.getOrNull(0)?.toIntOrNull()
                            val fileName = parts.getOrNull(1)
                            if (id != null && fileName != null) files[id] = fileName
                            if (withPath) i++ // skip the path line
                        }
                        section == "*L" -> parseLineInfo(current, lastFileId)?.let {
                            ranges += it
                            lastFileId = it.fileId
                        }
                    }
                    i++
                }
                strata[name] = Stratum(files, ranges)
            }
            return SourceMap(strata)
        }

        // InputStartLine [ "#" LineFileID ] [ "," RepeatCount ] ":" OutputStartLine [ "," OutputLineIncrement ]
        private fun parseLineInfo(text: String, lastFileId: Int): LineRange? {
            val (input, output) = text.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val inputStart = input.substringBefore('#').substringBefore(',').toIntOrNull() ?: return null
            val fileId = if ('#' in input) input.substringAfter('#').substringBefore(',').toIntOrNull() ?: return null else lastFileId
            val repeat = if (',' in input) input.substringAfter(',').toIntOrNull() ?: return null else 1
            val outputStart = output.substringBefore(',').toIntOrNull() ?: return null
            val increment = if (',' in output) output.substringAfter(',').toIntOrNull() ?: return null else 1
            if (increment < 0 || repeat <= 0) return null
            return LineRange(inputStart, fileId, repeat, outputStart, increment)
        }
    }
}
