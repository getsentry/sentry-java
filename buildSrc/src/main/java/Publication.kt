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
    val version = project.properties["versionName"].toString()
    val name = project.name

    this.maybeCreate("android").contents {
        from("build${sep}publications${sep}androidRelease") {
            renameModule(name, "android", version = version)
        }
        from("build${sep}outputs${sep}aar") {
            include("*-release*")
            rename {
                it.replace("-release", "-android-release")
            }
        }
        from("build${sep}libs") {
            include("*android*")
            include("*androidRelease-javadoc*")
            rename {
                it.replace("androidRelease-javadoc", "android")
            }
        }
    }
    this.getByName("main").contents {
        from("build${sep}publications${sep}kotlinMultiplatform") {
            renameModule(name, version = version)
        }
        from("build${sep}kotlinToolingMetadata")
        from("build${sep}libs") {
            include("*compose-kotlin*")
            include("*compose-metadata*")
            rename {
                it.replace("-kotlin", "")
                    .replace("-metadata", "")
                    .replace("Multiplatform-javadoc", "")
            }
        }
    }
    this.maybeCreate("desktop").contents {
        // kotlin multiplatform modules
        from("build${sep}publications${sep}desktop") {
            renameModule(name, "desktop", version = version)
        }
        from("build${sep}libs") {
            include("*desktop*")
            include("*desktop-javadoc*")
            rename {
                it.replace("desktop-javadoc", "desktop")
            }
        }
    }

    // make other distZip tasks run together with the main distZip
    val platformDists = project.tasks.filter { task ->
        task.name.matches(Consts.taskRegex)
    }.toTypedArray()
    project.tasks.getByName("distZip").finalizedBy(*platformDists)
}

fun DistributionContainer.configureForJvm(project: Project) {
    val sep = File.separator
    val version = project.properties["versionName"].toString()
    val name = project.name

    this.getByName("main").contents {
        // non android modules
        from("build${sep}libs")
        from("build${sep}publications${sep}maven") {
            renameModule(name, version = version)
        }
        // android modules
        from("build${sep}outputs${sep}aar") {
            include("*-release*")
        }
        from("build${sep}publications${sep}release") {
            renameModule(name, version = version)
        }
        from("build${sep}intermediates${sep}java_doc_jar${sep}release") {
            include("*javadoc*")
            rename { it.replace("release", "$name-$version") }
        }
        from("build${sep}intermediates${sep}source_jar${sep}release") {
            include("*sources*")
            rename { it.replace("release", "$name-$version") }
        }
    }
}

private fun CopySpec.renameModule(projectName: String, renameTo: String = "", version: String) {
    var target = ""
    if (renameTo.isNotEmpty()) {
        target = "-$renameTo"
    }
    rename {
        it.replace("module.json", "$projectName$target-$version.module")
    }
}
