import com.android.build.api.dsl.CommonExtension

// Shared Android lint configuration for the SDK's library and application modules.
// See Tor Norbye's rationale for checkGeneratedSources/checkDependencies:
// https://groups.google.com/forum/#!msg/lint-dev/JqTI4eQ8GpI/nBfS7xLKBwAJ
pluginManager.withPlugin("com.android.base") {
  extensions.configure(CommonExtension::class.java) {
    lint {
      warningsAsErrors = true
      // Avoids false "unused" reports for code used only by generated classes (e.g. ViewBinding).
      checkGeneratedSources = true
      // Lets `:module:lint` also analyze dependency modules.
      checkDependencies = true
      // Test sources dominate lint time; skipping them is the main speed-up.
      ignoreTestSources = true

      // Suppress OldTargetApi: lint 8.13.1 expects API 37 but we target 36. Only the
      // application modules set targetSdk, so this is a no-op for the library modules.
      disable += "OldTargetApi"

      // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
      checkReleaseBuilds = false
    }
  }
}
