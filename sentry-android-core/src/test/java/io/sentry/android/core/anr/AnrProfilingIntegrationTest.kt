package io.sentry.android.core.anr

import android.os.SystemClock
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.test.getProperty
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnrProfilingIntegrationTest {
  private lateinit var tempDir: File
  private lateinit var mockScopes: IScopes
  private lateinit var mockLogger: ILogger
  private lateinit var options: SentryOptions

  @BeforeTest
  fun setup() {
    tempDir = Files.createTempDirectory("anr_profile_test").toFile()
    mockScopes = mock()
    mockLogger = mock()
    options =
      SentryOptions().apply {
        cacheDirPath = tempDir.absolutePath
        setLogger(mockLogger)
      }
    AppState.getInstance().resetInstance()
  }

  @AfterTest
  fun cleanup() {
    if (::tempDir.isInitialized && tempDir.exists()) {
      tempDir.deleteRecursively()
    }
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `onForeground starts monitoring thread`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)

    // Act
    integration.onForeground()
    Thread.sleep(100) // Allow thread to start

    // Assert
    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)
    assertTrue(thread.isAlive)
    assertEquals("AnrProfilingIntegration", thread.name)
  }

  @Test
  fun `onBackground stops monitoring thread`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.onForeground()
    Thread.sleep(100)

    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)

    // Act
    integration.onBackground()
    thread.join(2000) // Wait for thread to stop

    // Assert
    assertTrue(!thread.isAlive)
  }

  @Test
  fun `close disables integration and interrupts thread`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.onForeground()
    Thread.sleep(100)

    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)

    // Act
    integration.close()
    thread.join(2000)

    // Assert
    assertTrue(!thread.isAlive)
    val enabled = integration.getProperty<java.util.concurrent.atomic.AtomicBoolean>("enabled")
    assertTrue(!enabled.get())
  }

  @Test
  fun `lifecycle methods have no influence after close`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.close()
    integration.onForeground()
    integration.onBackground()

    val thread = integration.getProperty<Thread?>("thread")
    assertTrue(thread == null || !thread.isAlive)
  }

  @Test
  fun `multiple foreground calls do not create multiple threads`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)

    // Act
    integration.onForeground()
    Thread.sleep(100)
    val thread1 = integration.getProperty<Thread?>("thread")

    integration.onForeground()
    Thread.sleep(100)
    val thread2 = integration.getProperty<Thread?>("thread")

    // Assert
    assertNotNull(thread1)
    assertNotNull(thread2)
    assertEquals(thread1, thread2, "Should reuse the same thread")

    integration.close()
  }

  @Test
  fun `foreground after background restarts thread`() {
    // Arrange
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)

    // Act
    integration.onForeground()
    Thread.sleep(100)
    val thread1 = integration.getProperty<Thread?>("thread")

    integration.onBackground()
    integration.onForeground()

    Thread.sleep(100)
    val thread2 = integration.getProperty<Thread?>("thread")

    // Assert
    assertNotNull(thread1)
    assertNotNull(thread2)
    assertTrue(thread1 != thread2, "Should create a new thread after background")

    integration.close()
  }

  @Test
  fun `properly walks through state transitions and collects stack traces`() {
    // Arrange
    val mainThread = Thread.currentThread()
    SystemClock.setCurrentTimeMillis(1_00)

    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.onForeground()

    // Act
    SystemClock.setCurrentTimeMillis(1_000)
    integration.checkMainThread(mainThread)
    assertEquals(AnrProfilingIntegration.MainThreadState.IDLE, integration.state)
    assertTrue(integration.profileManager.load().stacks.isEmpty())

    SystemClock.setCurrentTimeMillis(3_000)
    integration.checkMainThread(mainThread)
    assertEquals(AnrProfilingIntegration.MainThreadState.SUSPICIOUS, integration.state)

    SystemClock.setCurrentTimeMillis(6_000)
    integration.checkMainThread(mainThread)
    assertEquals(AnrProfilingIntegration.MainThreadState.ANR_DETECTED, integration.state)
    assertEquals(2, integration.profileManager.load().stacks.size)

    integration.close()
  }
}
