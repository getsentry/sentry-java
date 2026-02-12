package io.sentry.android.core.anr

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.test.getProperty
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AnrProfilingIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private lateinit var mockScopes: IScopes
  private lateinit var mockLogger: ILogger
  private lateinit var options: SentryAndroidOptions

  @BeforeTest
  fun setup() {
    mockScopes = mock()
    mockLogger = mock()
    options =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isEnableAnrProfiling = true
      }
    AppState.getInstance().resetInstance()
  }

  @AfterTest
  fun cleanup() {
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `onForeground starts monitoring thread`() {
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)

    integration.onForeground()
    Thread.sleep(100) // Allow thread to start

    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)
    assertTrue(thread.isAlive)
    assertEquals("AnrProfilingIntegration", thread.name)
  }

  @Test
  fun `onBackground stops monitoring thread`() {
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.onForeground()
    Thread.sleep(100)

    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)

    integration.onBackground()
    thread.join(2000) // Wait for thread to stop

    assertTrue(!thread.isAlive)
  }

  @Test
  fun `close disables integration and interrupts thread`() {
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)
    integration.onForeground()
    Thread.sleep(100)

    val thread = integration.getProperty<Thread?>("thread")
    assertNotNull(thread)

    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isNotEmpty())

    integration.close()
    thread.join(2000)

    assertTrue(!thread.isAlive)
    val enabled = integration.getProperty<java.util.concurrent.atomic.AtomicBoolean>("enabled")
    assertTrue(!enabled.get())
    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }

  @Test
  fun `lifecycle methods have no influence after close`() {
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
    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, options)

    integration.onForeground()
    Thread.sleep(100)
    val thread1 = integration.getProperty<Thread?>("thread")

    integration.onForeground()
    Thread.sleep(100)
    val thread2 = integration.getProperty<Thread?>("thread")

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

    integration.onForeground()
    Thread.sleep(100)
    val thread1 = integration.getProperty<Thread?>("thread")

    integration.onBackground()
    integration.onForeground()

    Thread.sleep(100)
    val thread2 = integration.getProperty<Thread?>("thread")

    assertNotNull(thread1)
    assertNotNull(thread2)
    assertTrue(thread1 != thread2, "Should create a new thread after background")

    integration.close()
  }

  @Test
  fun `properly walks through state transitions and collects stack traces`() {
    val mainThread = Thread.currentThread()
    SystemClock.setCurrentTimeMillis(1_00)

    val androidOptions =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isEnableAnrProfiling = true
      }

    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, androidOptions)
    integration.onForeground()

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

    for (i in 0 until AnrProfilingIntegration.MAX_NUM_STACKS + 1) {
      integration.checkMainThread(mainThread)
    }
    assertEquals(AnrProfilingIntegration.MAX_NUM_STACKS, integration.numCollectedStacks.get())
  }

  @Test
  fun `background foreground transitions don't trigger an ANR`() {
    val mainThread = Thread.currentThread()
    SystemClock.setCurrentTimeMillis(1_000)

    val androidOptions =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isEnableAnrProfiling = true
      }

    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, androidOptions)
    integration.onBackground()

    SystemClock.setCurrentTimeMillis(20_000)
    integration.onForeground()

    Thread.sleep(100)
    integration.checkMainThread(mainThread)
    assertEquals(AnrProfilingIntegration.MainThreadState.IDLE, integration.state)
  }

  @Test
  fun `does not register when options is not SentryAndroidOptions`() {
    val plainOptions =
      SentryOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
      }

    val integration = AnrProfilingIntegration()

    try {
      integration.register(mockScopes, plainOptions)
    } catch (e: IllegalArgumentException) {
      // ignored
    }

    // Verify no listeners were added
    val lifecycleObserver = AppState.getInstance().lifecycleObserver
    if (lifecycleObserver != null) {
      assertTrue(lifecycleObserver.listeners.isEmpty())
    }
  }

  @Test
  fun `does not register when ANR profiling is disabled`() {
    val androidOptions =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isEnableAnrProfiling = false
      }

    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, androidOptions)

    // When ANR profiling is disabled, the integration doesn't add itself to AppState
    // So the lifecycle observer may be null or have no listeners
    val lifecycleObserver = AppState.getInstance().lifecycleObserver
    if (lifecycleObserver != null) {
      assertTrue(lifecycleObserver.listeners.isEmpty())
    }
  }

  @Test
  fun `registers when ANR profiling is enabled`() {
    val androidOptions =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isEnableAnrProfiling = true
      }

    val integration = AnrProfilingIntegration()
    integration.register(mockScopes, androidOptions)

    assertFalse(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
    assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("AnrProfiling"))

    integration.close()
  }
}
