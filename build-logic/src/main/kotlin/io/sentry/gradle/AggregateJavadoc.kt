package io.sentry.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class AggregateJavadoc @Inject constructor(
    @get:Internal val fs: FileSystemOperations
) : DefaultTask() {
    @get:InputFiles
    abstract val javadocFiles: Property<FileCollection>

    // Marked as Internal since this is only used to relativize the paths for the output directories
    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun aggregate() {
        javadocFiles.get().forEach { file ->
            fs.copy {
                // Get the relative path of the project directory to the root directory
                val relativePath = file.relativeTo(rootDir.get().asFile)
                // Remove the 'build/docs/javadoc' part from the path
                val projectPath = relativePath.path.replace("build/docs/javadoc", "")
                from(file)
                // Use the project name as the output directory name so that each javadoc goes into its own directory
                into(outputDir.get().file(projectPath))
            }
        }
    }
}
