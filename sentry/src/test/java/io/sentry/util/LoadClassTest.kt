package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
  fun `isClassAvailable does not run the static initializer of the probed class`() {
    // Reading the flag initializes the flag holder, not the probe.
    assertFalse(IsClassAvailableNoInitFlag.initialized)

    // Obtaining the name via ::class.java does not initialize the probe either.
    LoadClass()
      .isClassAvailable(IsClassAvailableNoInitProbe::class.java.name, null as io.sentry.ILogger?)

    // Availability probing must not trigger the probe's static initializer.
    assertFalse(IsClassAvailableNoInitFlag.initialized)
  }

  @Test
  fun `loadClass runs the static initializer of the loaded class`() {
    assertFalse(LoadClassInitFlag.initialized)

    LoadClass().loadClass(LoadClassInitProbe::class.java.name, null)

    assertTrue(LoadClassInitFlag.initialized)
  }
}

private object IsClassAvailableNoInitFlag {
  @JvmField var initialized = false
}

private object IsClassAvailableNoInitProbe {
  init {
    IsClassAvailableNoInitFlag.initialized = true
  }
}

private object LoadClassInitFlag {
  @JvmField var initialized = false
}

private object LoadClassInitProbe {
  init {
    LoadClassInitFlag.initialized = true
  }
}
