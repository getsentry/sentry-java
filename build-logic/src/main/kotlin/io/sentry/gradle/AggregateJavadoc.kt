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

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun aggregate() {
        javadocFiles.get().forEach { file ->
            fs.copy {
                // Get the third to last part (project name) to use as the directory name for the output
                val parts = file.path.split('/')
                val projectName = parts[parts.size - 4]
                from(file)
                // Use the project name as the output directory name so that each javadoc goes into its own directory
                into(outputDir.get().file(projectName))
            }
        }
    }
}
