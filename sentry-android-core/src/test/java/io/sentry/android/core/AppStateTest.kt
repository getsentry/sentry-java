package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.AppState.AppStateListener
import io.sentry.android.core.internal.util.AndroidThreadChecker
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppStateTest {

  private class Fixture {
    val mockThreadChecker: AndroidThreadChecker = mock()
    val mockHandler: MainLooperHandler = mock()
    val options = SentryAndroidOptions()
    val listener: AppStateListener = mock()
    lateinit var androidThreadCheckerMock: MockedStatic<AndroidThreadChecker>

    fun getSut(isMainThread: Boolean = true): AppState {
      val appState = AppState.getInstance()
      whenever(mockThreadChecker.isMainThread).thenReturn(isMainThread)
      appState.setHandler(mockHandler)
      return appState
    }

    fun createListener(): AppStateListener = mock()
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()

    // Mock AndroidThreadChecker
    fixture.androidThreadCheckerMock = mockStatic(AndroidThreadChecker::class.java)
    fixture.androidThreadCheckerMock
      .`when`<AndroidThreadChecker> { AndroidThreadChecker.getInstance() }
      .thenReturn(fixture.mockThreadChecker)
  }

  @AfterTest
  fun `tear down`() {
    fixture.androidThreadCheckerMock.close()
  }

  @Test
  fun `getInstance returns singleton instance`() {
    val instance1 = fixture.getSut()
    val instance2 = fixture.getSut()

    assertSame(instance1, instance2)
  }

  @Test
  fun `resetInstance creates new instance`() {
    val sut = fixture.getSut()
    sut.setInBackground(true)

    sut.resetInstance()

    val newInstance = fixture.getSut()
    assertNull(newInstance.isInBackground())
  }

  @Test
  fun `isInBackground returns null initially`() {
    val sut = fixture.getSut()

    assertNull(sut.isInBackground())
  }

  @Test
  fun `setInBackground updates state`() {
    val sut = fixture.getSut()

    sut.setInBackground(true)
    assertTrue(sut.isInBackground()!!)

    sut.setInBackground(false)
    assertFalse(sut.isInBackground()!!)
  }

  @Test
  fun `addAppStateListener creates lifecycle observer if needed`() {
    val sut = fixture.getSut()

    sut.addAppStateListener(fixture.listener)

    assertNotNull(sut.lifecycleObserver)
  }

  @Test
  fun `addAppStateListener from background thread posts to main thread`() {
    val sut = fixture.getSut(isMainThread = false)

    sut.addAppStateListener(fixture.listener)

    verify(fixture.mockHandler).post(any())
  }

  @Test
  fun `addAppStateListener notifies listener with onForeground when in foreground state`() {
    val sut = fixture.getSut()

    sut.setInBackground(false)
    sut.addAppStateListener(fixture.listener)

    verify(fixture.listener).onForeground()
    verify(fixture.listener, never()).onBackground()
  }

  @Test
  fun `addAppStateListener notifies listener with onBackground when in background state`() {
    val sut = fixture.getSut()

    sut.setInBackground(true)
    sut.addAppStateListener(fixture.listener)

    verify(fixture.listener).onBackground()
    verify(fixture.listener, never()).onForeground()
  }

  @Test
  fun `addAppStateListener does not notify listener when state is unknown`() {
    val sut = fixture.getSut()

    // State is null (unknown) by default
    sut.addAppStateListener(fixture.listener)

    verify(fixture.listener, never()).onForeground()
    verify(fixture.listener, never()).onBackground()
  }

  @Test
  fun `removeAppStateListener removes listener`() {
    val sut = fixture.getSut()

    sut.addAppStateListener(fixture.listener)
    val observer = sut.lifecycleObserver
    // Check that listener was added
    assertNotNull(observer)

    sut.removeAppStateListener(fixture.listener)
    // Listener should be removed but observer still exists
    assertNotNull(sut.lifecycleObserver)
  }

  @Test
  fun `removeAppStateListener handles null lifecycle observer`() {
    val sut = fixture.getSut()

    // Should not throw when lifecycleObserver is null
    sut.removeAppStateListener(fixture.listener)
  }

  @Test
  fun `registerLifecycleObserver does nothing if already registered`() {
    val sut = fixture.getSut()

    sut.registerLifecycleObserver(fixture.options)
    val firstObserver = sut.lifecycleObserver

    sut.registerLifecycleObserver(fixture.options)
    val secondObserver = sut.lifecycleObserver

    assertSame(firstObserver, secondObserver)
  }

  @Test
  fun `unregisterLifecycleObserver clears listeners and nulls observer`() {
    val sut = fixture.getSut()

    sut.addAppStateListener(fixture.listener)
    assertNotNull(sut.lifecycleObserver)

    sut.unregisterLifecycleObserver()

    assertNull(sut.lifecycleObserver)
  }

  @Test
  fun `unregisterLifecycleObserver handles null observer`() {
    val sut = fixture.getSut()

    // Should not throw when lifecycleObserver is already null
    sut.unregisterLifecycleObserver()
  }

  @Test
  fun `unregisterLifecycleObserver from background thread posts to main thread`() {
    val sut = fixture.getSut(isMainThread = false)

    sut.registerLifecycleObserver(fixture.options)

    sut.unregisterLifecycleObserver()

    // 2 times - register and unregister
    verify(fixture.mockHandler, times(2)).post(any())
  }

  @Test
  fun `close calls unregisterLifecycleObserver`() {
    val sut = fixture.getSut()
    sut.addAppStateListener(fixture.listener)

    sut.close()

    assertNull(sut.lifecycleObserver)
  }

  @Test
  fun `LifecycleObserver onStart notifies all listeners and sets foreground`() {
    val listener1 = fixture.createListener()
    val listener2 = fixture.createListener()
    val sut = fixture.getSut()

    // Add listeners to create observer
    sut.addAppStateListener(listener1)
    sut.addAppStateListener(listener2)

    val observer = sut.lifecycleObserver!!
    observer.onStart(mock())

    verify(listener1).onForeground()
    verify(listener2).onForeground()
    assertFalse(sut.isInBackground()!!)
  }

  @Test
  fun `LifecycleObserver onStop notifies all listeners and sets background`() {
    val listener1 = fixture.createListener()
    val listener2 = fixture.createListener()
    val sut = fixture.getSut()

    // Add listeners to create observer
    sut.addAppStateListener(listener1)
    sut.addAppStateListener(listener2)

    val observer = sut.lifecycleObserver!!
    observer.onStop(mock())

    verify(listener1).onBackground()
    verify(listener2).onBackground()
    assertTrue(sut.isInBackground()!!)
  }

  @Test
  fun `a listener can be unregistered within a callback`() {
    val sut = fixture.getSut()

    var onForegroundCalled = false
    val listener =
      object : AppStateListener {
        override fun onForeground() {
          sut.removeAppStateListener(this)
          onForegroundCalled = true
        }

        override fun onBackground() {
          // ignored
        }
      }

    sut.registerLifecycleObserver(fixture.options)
    val observer = sut.lifecycleObserver!!
    observer.onStart(mock())

    // if an observer is added
    sut.addAppStateListener(listener)

    // it should be notified
    assertTrue(onForegroundCalled)

    // and removed from the list of listeners if it unregisters itself within the callback
    assertEquals(sut.lifecycleObserver?.listeners?.size, 0)
  }

  @Test
  fun `state is correct within onStart and onStop callbacks`() {
    val sut = fixture.getSut()

    var onForegroundCalled = false
    var onBackgroundCalled = false
    val listener =
      object : AppStateListener {
        override fun onForeground() {
          assertFalse(sut.isInBackground!!)
          onForegroundCalled = true
        }

        override fun onBackground() {
          assertTrue(sut.isInBackground!!)
          onBackgroundCalled = true
        }
      }

    sut.addAppStateListener(listener)

    val observer = sut.lifecycleObserver!!
    observer.onStart(mock())
    observer.onStop(mock())

    assertTrue(onForegroundCalled)
    assertTrue(onBackgroundCalled)
  }

  @Test
  fun `thread safety - concurrent access is handled`() {
    val listeners = (1..5).map { fixture.createListener() }
    val sut = fixture.getSut()
    val latch = CountDownLatch(5)

    // Add listeners concurrently
    listeners.forEach { listener ->
      Thread {
          sut.addAppStateListener(listener)
          latch.countDown()
        }
        .start()
    }
    latch.await()

    val observer = sut.lifecycleObserver
    assertNotNull(observer)
  }
}
