import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.LinkedHashSet
import java.util.zip.ZipFile
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class MergeSpringMetadataAction(
    private val runtimeClasspath: FileCollection,
    private val springMetadataFiles: List<String>,
) : Action<Task> {
    companion object {
        val DEFAULT_SPRING_METADATA_FILES =
            listOf(
                "META-INF/spring.factories",
                "META-INF/spring.handlers",
                "META-INF/spring.schemas",
                "META-INF/spring-autoconfigure-metadata.properties",
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                "META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports",
            )
    }

    override fun execute(task: Task) {
        val archiveTask = task as AbstractArchiveTask
        val jar = archiveTask.archiveFile.get().asFile
        val runtimeJars = runtimeClasspath.files.filter { it.name.endsWith(".jar") }
        val uri = URI.create("jar:${jar.toURI()}")

        FileSystems.newFileSystem(uri, mapOf("create" to "false")).use { fs ->
            springMetadataFiles.forEach { entryPath ->
                val target = fs.getPath(entryPath)
                val contents = mutableListOf<String>()

                if (Files.exists(target)) {
                    contents.add(Files.readString(target))
                }

                runtimeJars.forEach { depJar ->
                    try {
                        ZipFile(depJar).use { zip ->
                            val entry = zip.getEntry(entryPath)
                            if (entry != null) {
                                contents.add(zip.getInputStream(entry).bufferedReader().readText())
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore non-zip files on the runtime classpath.
                    }
                }

                val merged =
                    when {
                        entryPath == "META-INF/spring.factories" -> mergeListProperties(contents)
                        entryPath.endsWith(".imports") -> mergeLineBasedMetadata(contents)
                        else -> mergeMapProperties(contents)
                    }

                if (merged.isNotEmpty()) {
                    if (target.parent != null) {
                        Files.createDirectories(target.parent)
                    }
                    Files.write(target, merged.toByteArray())
                }
            }

            val serviceEntries = linkedSetOf<String>()

            runtimeJars.forEach { depJar ->
                try {
                    ZipFile(depJar).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && entry.name.startsWith("META-INF/services/")) {
                                serviceEntries.add(entry.name)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore non-zip files on the runtime classpath.
                }
            }

            serviceEntries.forEach { entryPath ->
                val providers = LinkedHashSet<String>()
                val target = fs.getPath(entryPath)

                if (Files.exists(target)) {
                    Files.newBufferedReader(target).useLines { lines ->
                        lines.forEach { line ->
                            val provider = line.trim()
                            if (provider.isNotEmpty() && !provider.startsWith("#")) {
                                providers.add(provider)
                            }
                        }
                    }
                }

                runtimeJars.forEach { depJar ->
                    try {
                        ZipFile(depJar).use { zip ->
                            val entry = zip.getEntry(entryPath)
                            if (entry != null) {
                                zip.getInputStream(entry).bufferedReader().useLines { lines ->
                                    lines.forEach { line ->
                                        val provider = line.trim()
                                        if (provider.isNotEmpty() && !provider.startsWith("#")) {
                                            providers.add(provider)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore non-zip files on the runtime classpath.
                    }
                }

                if (providers.isNotEmpty()) {
                    if (target.parent != null) {
                        Files.createDirectories(target.parent)
                    }
                    Files.write(target, providers.joinToString(separator = "\n", postfix = "\n").toByteArray())
                }
            }
        }
    }

    private fun mergeLineBasedMetadata(contents: List<String>): String {
        val lines = LinkedHashSet<String>()

        contents.forEach { content ->
            content.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    lines.add(line)
                }
            }
        }

        return if (lines.isEmpty()) "" else lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun mergeMapProperties(contents: List<String>): String {
        val merged = linkedMapOf<String, String>()

        contents.forEach { content ->
            parseProperties(content).forEach { (key, value) ->
                merged[key] = value
            }
        }

        return if (merged.isEmpty()) {
            ""
        } else {
            merged.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
        }
    }

    private fun mergeListProperties(contents: List<String>): String {
        val merged = linkedMapOf<String, LinkedHashSet<String>>()

        contents.forEach { content ->
            parseProperties(content).forEach { (key, value) ->
                val values = merged.getOrPut(key) { LinkedHashSet() }
                value
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach(values::add)
            }
        }

        return if (merged.isEmpty()) {
            ""
        } else {
            merged.entries.joinToString(separator = "\n", postfix = "\n") { (key, values) ->
                "$key=${values.joinToString(separator = ",")}"
            }
        }
    }

    private fun parseProperties(content: String): List<Pair<String, String>> {
        val logicalLines = mutableListOf<String>()
        val current = StringBuilder()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (current.isEmpty() && (line.isEmpty() || line.startsWith("#") || line.startsWith("!"))) {
                return@forEach
            }

            val normalized = if (current.isEmpty()) line else line.trimStart()
            current.append(
                if (endsWithContinuation(rawLine)) normalized.dropLast(1) else normalized,
            )

            if (!endsWithContinuation(rawLine)) {
                logicalLines.add(current.toString())
                current.setLength(0)
            }
        }

        if (current.isNotEmpty()) {
            logicalLines.add(current.toString())
        }

        return logicalLines.map { line ->
            val separatorIndex = findSeparatorIndex(line)
            if (separatorIndex < 0) {
                line to ""
            } else {
                val keyEnd = trimTrailingWhitespace(line, separatorIndex)
                val valueStart = findValueStart(line, separatorIndex)
                line.substring(0, keyEnd) to line.substring(valueStart).trim()
            }
        }
    }

    private fun endsWithContinuation(line: String): Boolean {
        var backslashCount = 0

        for (index in line.length - 1 downTo 0) {
            if (line[index] == '\\') {
                backslashCount++
            } else {
                break
            }
        }

        return backslashCount % 2 == 1
    }

    private fun findSeparatorIndex(line: String): Int {
        var backslashCount = 0

        line.forEachIndexed { index, char ->
            if (char == '\\') {
                backslashCount++
            } else {
                val isEscaped = backslashCount % 2 == 1
                if (!isEscaped && (char == '=' || char == ':' || char.isWhitespace())) {
                    return index
                }
                backslashCount = 0
            }
        }

        return -1
    }

    private fun trimTrailingWhitespace(line: String, endExclusive: Int): Int {
        var end = endExclusive

        while (end > 0 && line[end - 1].isWhitespace()) {
            end--
        }

        return end
    }

    private fun findValueStart(line: String, separatorIndex: Int): Int {
        var valueStart = separatorIndex

        while (valueStart < line.length && line[valueStart].isWhitespace()) {
            valueStart++
        }

        if (valueStart < line.length && (line[valueStart] == '=' || line[valueStart] == ':')) {
            valueStart++
        }

        while (valueStart < line.length && line[valueStart].isWhitespace()) {
            valueStart++
        }

        return valueStart
    }
}
