package io.sentry.gradle

import com.android.build.api.dsl.LibraryExtension
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.work.DisableCachingByDefault

/**
 * The android-all jars our Robolectric tests run against, by API level. Robolectric picks the
 * version for each level itself (see its DefaultSdkProvider), so a Robolectric upgrade can change
 * them, and a test on a level missing here fails with "no artifacts found for
 * org.robolectric:android-all-instrumented:...".
 */
private val ANDROID_ALL_VERSIONS =
  mapOf(
    21 to "5.0.2_r3-robolectric-r0",
    24 to "7.0.0_r1-robolectric-r1",
    28 to "9-robolectric-4913185-2",
    29 to "10-robolectric-5803371",
    30 to "11-robolectric-6757853",
    31 to "12-robolectric-7732740",
    33 to "13-robolectric-9030017",
    35 to "15-robolectric-12650502",
  )

/** The instrumentation revision Robolectric appends to every android-all version. */
private const val ANDROID_ALL_INSTRUMENTATION = "i7"

/**
 * Has Gradle download the android-all jars that Robolectric runs tests against, instead of
 * Robolectric fetching them from Maven Central while the tests run.
 *
 * Gradle caches them with every other dependency, so CI restores them with the Gradle cache, and
 * fetches them before any test JVM starts. See
 * https://robolectric.org/blog/2023/11/11/improving-android-all-downloading/. Unlike the post,
 * which copies the jars into `robolectric.dependency.dir`, the tests read them straight from
 * Gradle's cache through a `robolectric-deps.properties` file, so no module keeps a 1 GB copy.
 */
class SentryRobolectricPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val coordinatesToJar =
      ANDROID_ALL_VERSIONS.values.associate { version ->
        val coordinates =
          "org.robolectric:android-all-instrumented:$version-$ANDROID_ALL_INSTRUMENTATION"
        // One configuration per jar, because resolving them together would keep only the highest
        // version.
        coordinates to
          project.configurations.detachedConfiguration(project.dependencies.create(coordinates))
      }

    val writeDeps =
      project.tasks.register("writeRobolectricDeps", WriteRobolectricDeps::class.java) {
        coordinatesToJar.forEach { (coordinates, jar) ->
          jars.put(coordinates, jar.elements.map { it.single().asFile.absolutePath })
        }
        propertiesFile.set(project.layout.buildDirectory.file("robolectric/deps.properties"))
      }

    project.plugins.withId("com.android.library") {
      project.extensions.getByType(LibraryExtension::class.java).testOptions.unitTests.all { test ->
        // The properties file holds absolute paths, so it can't be a test input without breaking
        // build cache hits across machines. The jars are the input instead.
        test.dependsOn(writeDeps)
        test.jvmArgumentProviders.add(
          project.objects.newInstance(RobolectricDepsArgument::class.java).apply {
            androidAllJars.from(coordinatesToJar.values)
            propertiesFile.set(writeDeps.flatMap { it.propertiesFile })
          }
        )
      }
    }
  }
}

/**
 * Writes the file Robolectric's PropertiesDependencyResolver reads: each android-all coordinate
 * mapped to its jar.
 */
@DisableCachingByDefault(because = "Writing a few lines is cheaper than caching them")
abstract class WriteRobolectricDeps : DefaultTask() {
  @get:Input abstract val jars: MapProperty<String, String>

  @get:OutputFile abstract val propertiesFile: RegularFileProperty

  @TaskAction
  fun write() {
    val properties = Properties()
    properties.putAll(jars.get())
    propertiesFile.get().asFile.outputStream().use { properties.store(it, null) }
  }
}

abstract class RobolectricDepsArgument : CommandLineArgumentProvider {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val androidAllJars: ConfigurableFileCollection

  @get:Internal abstract val propertiesFile: RegularFileProperty

  override fun asArguments(): List<String> =
    listOf("-Drobolectric-deps.properties=${propertiesFile.get().asFile.absolutePath}")
}
