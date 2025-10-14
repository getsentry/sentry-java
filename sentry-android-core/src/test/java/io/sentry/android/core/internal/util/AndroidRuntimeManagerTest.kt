package io.sentry.android.core.internal.util

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRuntimeManagerTest {

  val sut = AndroidRuntimeManager()

  @AfterTest
  fun `clean up`() {
    // Revert StrictMode policies to avoid issues with other tests
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
  }

  @Test
  fun `runWithRelaxedPolicy changes policy when running and restores it afterwards`() {
    var called = false
    val threadPolicy = StrictMode.ThreadPolicy.Builder().detectAll().penaltyDeath().build()
    val vmPolicy = StrictMode.VmPolicy.Builder().detectAll().penaltyDeath().build()
    assertNotEquals(StrictMode.ThreadPolicy.LAX, threadPolicy)
    assertNotEquals(StrictMode.VmPolicy.LAX, vmPolicy)

    // Set and assert the StrictMode policies
    StrictMode.setThreadPolicy(threadPolicy)
    StrictMode.setVmPolicy(vmPolicy)
    assertEquals(threadPolicy.toString(), StrictMode.getThreadPolicy().toString())
    assertEquals(vmPolicy.toString(), StrictMode.getVmPolicy().toString())

    // Run the function and assert LAX policies
    called =
      sut.runWithRelaxedPolicy {
        assertEquals(
          StrictMode.ThreadPolicy.LAX.toString(),
          StrictMode.getThreadPolicy().toString(),
        )
        assertEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
        true
      }

    // Policies should be reverted back
    assertEquals(threadPolicy.toString(), StrictMode.getThreadPolicy().toString())
    assertEquals(vmPolicy.toString(), StrictMode.getVmPolicy().toString())

    // Ensure the code ran
    assertTrue(called)
  }

  @Test
  fun `runWithRelaxedPolicy changes policy and restores it afterwards even if the code throws`() {
    var called = false
    val threadPolicy = StrictMode.ThreadPolicy.Builder().detectAll().penaltyDeath().build()
    val vmPolicy = StrictMode.VmPolicy.Builder().detectAll().penaltyDeath().build()

    // Set and assert the StrictMode policies
    StrictMode.setThreadPolicy(threadPolicy)
    StrictMode.setVmPolicy(vmPolicy)

    // Run the function and assert LAX policies
    try {
      sut.runWithRelaxedPolicy {
        assertEquals(
          StrictMode.ThreadPolicy.LAX.toString(),
          StrictMode.getThreadPolicy().toString(),
        )
        assertEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
        called = true
        throw Exception("Test exception")
      }
    } catch (_: Exception) {}

    // Policies should be reverted back
    assertEquals(threadPolicy.toString(), StrictMode.getThreadPolicy().toString())
    assertEquals(vmPolicy.toString(), StrictMode.getVmPolicy().toString())

    // Ensure the code ran
    assertTrue(called)
  }
}
