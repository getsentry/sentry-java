import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Name of the per-project Maven repository the dist zips are assembled from.
 * `distZip` depends on `publishAllPublicationsToDistMavenRepository`.
 */
const val DIST_MAVEN_REPO = "distMaven"

val Project.distMavenDir: Provider<Directory> get() = layout.buildDirectory.dir(DIST_MAVEN_REPO)

private fun Project.versionName(): String =
    providers.gradleProperty("versionName").get()

// configure distZip tasks for multiplatform
fun DistributionContainer.configureForMultiplatform(project: Project) {
    getByName("main").contents { fromDistMaven(project, project.name) }
    maybeCreate("android").contents { fromDistMaven(project, "${project.name}-android") }
    maybeCreate("desktop").contents { fromDistMaven(project, "${project.name}-desktop") }

    // make other distZip tasks run together with the main distZip
    val platformDists = project.tasks.filter { it.name.matches(Regex("(.*)DistZip")) }.toTypedArray()
    project.tasks.getByName("distZip").finalizedBy(*platformDists)
}

fun DistributionContainer.configureForJvm(project: Project) {
    getByName("main").contents { fromDistMaven(project, project.name) }
}

/**
 * Copies one publication out of the project's Maven repository layout. Maven guarantees file names
 * there (`<artifactId>-<version>[-<classifier>].<ext>`) independently of how the publish plugin
 * names its tasks or intermediate outputs, which is exactly what craft expects. Only the few
 * craft-specific names below need renaming.
 */
private fun CopySpec.fromDistMaven(project: Project, artifactId: String) {
    val version = project.versionName()
    val groupPath = project.group.toString().replace('.', '/')
    from(project.distMavenDir.map { it.dir("$groupPath/$artifactId/$version") }) {
        exclude("*.md5", "*.sha1", "*.sha256", "*.sha512", "*.asc")
        rename {
            when (it) {
                // craft reads the POM from a fixed name
                "$artifactId-$version.pom" -> "pom-default.xml"
                // craft's android config maps <dir>-<version> to <dir>-release.aar
                "$artifactId-$version.aar" -> "$artifactId-release.aar"
                // kept for parity with the old layout
                "$artifactId-$version-kotlin-tooling-metadata.json" -> "kotlin-tooling-metadata.json"
                else -> it
            }
        }
    }
}
