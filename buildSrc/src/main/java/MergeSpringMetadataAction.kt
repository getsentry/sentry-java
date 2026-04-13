import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipFile
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class MergeSpringMetadataAction(
    private val runtimeClasspath: FileCollection,
    private val springMetadataFiles: List<String>,
) : Action<Task> {

    override fun execute(task: Task) {
        val archiveTask = task as AbstractArchiveTask
        val jar = archiveTask.archiveFile.get().asFile
        val runtimeJars = runtimeClasspath.files.filter { it.name.endsWith(".jar") }
        val uri = URI.create("jar:${jar.toURI()}")

        FileSystems.newFileSystem(uri, mapOf("create" to "false")).use { fs ->
            springMetadataFiles.forEach { entryPath ->
                val merged = StringBuilder()

                runtimeJars.forEach { depJar ->
                    try {
                        ZipFile(depJar).use { zip ->
                            val entry = zip.getEntry(entryPath)
                            if (entry != null) {
                                merged.append(zip.getInputStream(entry).bufferedReader().readText())
                                if (!merged.endsWith("\n")) {
                                    merged.append("\n")
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore non-zip files on the runtime classpath.
                    }
                }

                if (merged.isNotEmpty()) {
                    val target = fs.getPath(entryPath)
                    if (target.parent != null) {
                        Files.createDirectories(target.parent)
                    }
                    Files.write(target, merged.toString().toByteArray())
                }
            }
        }
    }
}
