package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoadClassTest {
  @Test
  fun `loadClass returns the class when it is available`() {
    assertNotNull(LoadClass().loadClass("io.sentry.SentryEvent", null))
  }

  @Test
  fun `loadClass returns null when the class is not available`() {
    assertNull(LoadClass().loadClass("io.sentry.ThisClassDoesNotExist", null))
  }

  @Test
  fun `isClassAvailable reflects whether the class is on the classpath`() {
    val loadClass = LoadClass()
    assertNotNull(loadClass.loadClass("io.sentry.SentryEvent", null))
    assertFalse(
      loadClass.isClassAvailable("io.sentry.ThisClassDoesNotExist", null as io.sentry.ILogger?)
    )
  }

  @Test
  fun `loadClass does not run the static initializer of the probed class`() {
    // Reading the flag initializes the flag holder, not the probe.
    assertFalse(LoadClassNoInitFlag.initialized)

    // Obtaining the name via ::class.java does not initialize the probe either.
    LoadClass().loadClass(LoadClassNoInitProbe::class.java.name, null)

    // Availability probing must not trigger the probe's static initializer.
    assertFalse(LoadClassNoInitFlag.initialized)
  }
}

private object LoadClassNoInitFlag {
  @JvmField var initialized = false
}

private object LoadClassNoInitProbe {
  init {
    LoadClassNoInitFlag.initialized = true
  }
}
