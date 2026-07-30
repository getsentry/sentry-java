package io.sentry.util

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadClassTest {
  @Test
  fun `isClassAvailable uses known build-time results and reflects unknown classes`() {
    setClassAvailability(
      mapOf(
        "io.sentry.SentryEvent" to false,
        "io.sentry.ThisClassDoesNotExist" to true,
      )
    )

    try {
      val loadClass = LoadClass()
      assertThat(loadClass.isClassAvailable("io.sentry.SentryEvent", null as io.sentry.ILogger?))
        .isFalse()
      assertThat(
          loadClass.isClassAvailable(
            "io.sentry.ThisClassDoesNotExist",
            null as io.sentry.ILogger?,
          )
        )
        .isTrue()
      assertThat(loadClass.isClassAvailable("io.sentry.Sentry", null as io.sentry.ILogger?))
        .isTrue()
    } finally {
      setClassAvailability(null)
    }
  }

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

  private fun setClassAvailability(availability: Any?) {
    val field = LoadClass::class.java.getDeclaredField("classAvailability")
    field.isAccessible = true
    field.set(null, availability)
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
