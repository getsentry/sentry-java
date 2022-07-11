import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import java.io.File

private object Consts {
    val taskRegex = Regex("(.*)DistZip")
}

// configure distZip tasks for multiplatform
fun DistributionContainer.configureForMultiplatform(project: Project) {
    val sep = File.separator

    this.maybeCreate("android").contents {
        from("build${sep}publications${sep}androidRelease")
        from("build${sep}outputs${sep}aar") {
            rename {
                it.replace("-release", "-android-${project.version}")
            }
        }
        from("build${sep}libs") {
            include("*android*")
            withJavadoc(renameTo = "compose-android")
        }
    }
    this.getByName("main").contents {
        from("build${sep}publications${sep}kotlinMultiplatform")
        from("build${sep}kotlinToolingMetadata")
        from("build${sep}libs") {
            include("*compose-kotlin*")
            rename {
                it.replace("-kotlin", "")
            }
            withJavadoc()
        }
    }
    this.maybeCreate("desktop").contents {
        // kotlin multiplatform modules
        from("build${sep}publications${sep}desktop")
        from("build${sep}libs") {
            include("*desktop*")
            withJavadoc(renameTo = "compose-desktop")
        }
    }

    // make other distZip tasks run together with the main distZip
    project.tasks.getByName("distZip").dependsOn(
        *project.tasks.filter { task ->
            task.name.matches(Consts.taskRegex)
        }.toTypedArray()
    )
}

private fun CopySpec.withJavadoc(renameTo: String = "compose") {
    include("*javadoc*")
    rename {
        if (it.contains("javadoc")) {
            it.replace("compose", renameTo)
        } else {
            it
        }
    }
}
