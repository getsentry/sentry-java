package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
import android.app.ActivityManager.ProcessErrorStateInfo.NO_ERROR
import android.content.Context
import io.sentry.transport.ICurrentDateProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ANRWatchDogTest {
  private var currentTimeMs = 0L
  private val timeProvider = ICurrentDateProvider { currentTimeMs }

  @Before
  fun `setup`() {
    currentTimeMs = 12341234
  }

  @Test
  fun `when ANR is detected, callback is invoked with threads stacktrace`() {
    var anr: ApplicationNotResponding? = null
    val handler = mock<MainLooperHandler>()
    val thread = mock<Thread>()
    val expectedState = Thread.State.BLOCKED
    val stacktrace = StackTraceElement("class", "method", "fileName", 10)
    whenever(thread.state).thenReturn(expectedState)
    whenever(thread.stackTrace).thenReturn(arrayOf(stacktrace))
    val latch = CountDownLatch(1)
    whenever(handler.post(any())).then { latch.countDown() }
    whenever(handler.thread).thenReturn(thread)
    val interval = 10L

    val sut =
      ANRWatchDog(timeProvider, interval, 1L, true, { a -> anr = a }, mock(), handler, mock())
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS)
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        currentTimeMs += 100L
        Thread.sleep(100) // Let worker realize this is ANR
      } while (anr == null && waitCount++ < 100)

      assertNotNull(anr)
      assertEquals(expectedState, anr.thread!!.state)
      assertEquals(stacktrace.className, anr.stackTrace[0].className)
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  @Test
  fun `when ANR is not detected, callback is not invoked`() {
    var anr: ApplicationNotResponding? = null
    val handler = mock<MainLooperHandler>()
    val thread = mock<Thread>()
    var invoked = false
    whenever(handler.post(any())).then { i ->
      invoked = true
      (i.getArgument(0) as Runnable).run()
    }
    whenever(handler.thread).thenReturn(thread)
    val interval = 10L

    val sut =
      ANRWatchDog(timeProvider, interval, 1L, true, { a -> anr = a }, mock(), handler, mock())
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      var waitCount = 0
      do {
        currentTimeMs += 100L
        Thread.sleep(100) // Let worker realize his runner always runs
      } while (!invoked && waitCount++ < 100)

      assertTrue(invoked)
      assertNull(anr) // callback never ran
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  @Test
  fun `when ANR is detected and ActivityManager has ANR process, callback is invoked`() {
    var anr: ApplicationNotResponding? = null
    val handler = mock<MainLooperHandler>()
    val thread = mock<Thread>()
    val expectedState = Thread.State.BLOCKED
    val stacktrace = StackTraceElement("class", "method", "fileName", 10)
    whenever(thread.state).thenReturn(expectedState)
    whenever(thread.stackTrace).thenReturn(arrayOf(stacktrace))
    val latch = CountDownLatch(1)
    whenever(handler.post(any())).then { latch.countDown() }
    whenever(handler.thread).thenReturn(thread)
    val interval = 10L
    val context = mock<Context>()
    val am = mock<ActivityManager>()

    whenever(context.getSystemService(eq(Context.ACTIVITY_SERVICE))).thenReturn(am)
    val stateInfo = ActivityManager.ProcessErrorStateInfo()
    stateInfo.condition = NOT_RESPONDING
    val anrs = listOf(stateInfo)
    whenever(am.processesInErrorState).thenReturn(anrs)

    val sut =
      ANRWatchDog(timeProvider, interval, 1L, true, { a -> anr = a }, mock(), handler, context)
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS)
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        currentTimeMs += 100L
        Thread.sleep(100) // Let worker realize this is ANR
      } while (anr == null && waitCount++ < 100)

      assertNotNull(anr)
      assertEquals(expectedState, anr.thread!!.state)
      assertEquals(stacktrace.className, anr.stackTrace[0].className)
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  @Test
  fun `when ANR is detected and ActivityManager has no ANR process, callback is not invoked`() {
    var anr: ApplicationNotResponding? = null
    val handler = mock<MainLooperHandler>()
    val thread = mock<Thread>()
    val expectedState = Thread.State.BLOCKED
    val stacktrace = StackTraceElement("class", "method", "fileName", 10)
    whenever(thread.state).thenReturn(expectedState)
    whenever(thread.stackTrace).thenReturn(arrayOf(stacktrace))
    val latch = CountDownLatch(1)
    whenever(handler.post(any())).then { latch.countDown() }
    whenever(handler.thread).thenReturn(thread)
    val interval = 10L
    val context = mock<Context>()
    val am = mock<ActivityManager>()

    whenever(context.getSystemService(eq(Context.ACTIVITY_SERVICE))).thenReturn(am)
    val stateInfo = ActivityManager.ProcessErrorStateInfo()
    stateInfo.condition = NO_ERROR
    val anrs = listOf(stateInfo)
    whenever(am.processesInErrorState).thenReturn(anrs)

    val sut =
      ANRWatchDog(timeProvider, interval, 1L, true, { a -> anr = a }, mock(), handler, context)
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS)
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        currentTimeMs += 100L
        Thread.sleep(100L) // Let worker realize this is ANR
      } while (anr == null && waitCount++ < 100)
      assertNull(anr) // callback never ran
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }
}
