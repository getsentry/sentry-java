package io.sentry.android.buddy

import io.sentry.android.buddy.model.BuddyExceptionFrame

/**
 * Turns a stack [BuddyExceptionFrame] into an absolute host source path.
 *
 * A frame only carries a class [BuddyExceptionFrame.module] and a [BuddyExceptionFrame.filename],
 * never the host path, so the path is reconstructed from a build-time [basePath] (the module
 * directory, injected through `SentryBuddyOptions.sourceBasePath`) plus the package directories.
 * The result is a best guess for the standard `src/main/java` (or `kotlin`) layout; the host
 * validates it and a miss is simply ignored.
 */
internal object BuddySourcePathResolver {
  private val SOURCE_SETS = listOf("src/main/java", "src/main/kotlin")

  fun resolve(basePath: String?, frame: BuddyExceptionFrame): String? {
    val root = basePath?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
    val filename = frame.filename?.takeIf { it.isNotBlank() } ?: return null
    val packagePath = frame.module?.substringBeforeLast('.', "")?.replace('.', '/').orEmpty()
    val relative = listOf(packagePath, filename).filter { it.isNotBlank() }.joinToString("/")
    // The first source set is the best guess; the host tries the path and ignores a miss.
    return "$root/${SOURCE_SETS.first()}/$relative"
  }
}
