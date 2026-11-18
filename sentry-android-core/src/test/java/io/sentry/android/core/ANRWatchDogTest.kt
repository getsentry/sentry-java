package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
import android.app.ActivityManager.ProcessErrorStateInfo.NO_ERROR
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ANRWatchDogTest {
  private lateinit var ticker: TestMonotonicTicker

  @Before
  fun `setup`() {
    // an arbitrary, non-zero origin: no caller may assume a tick counts from zero
    ticker = TestMonotonicTicker(MILLISECONDS.toNanos(12341234))
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
      ANRWatchDog(
        ticker,
        interval,
        1L,
        true,
        { a -> anr = a },
        mock(),
        handler,
        mock(),
        AndroidDebugManager(),
      )
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS),
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
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
      ANRWatchDog(
        ticker,
        interval,
        1L,
        true,
        { a -> anr = a },
        mock(),
        handler,
        mock(),
        AndroidDebugManager(),
      )
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
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
      ANRWatchDog(
        ticker,
        interval,
        1L,
        true,
        { a -> anr = a },
        mock(),
        handler,
        context,
        AndroidDebugManager(),
      )
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS),
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
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
      ANRWatchDog(
        ticker,
        interval,
        1L,
        true,
        { a -> anr = a },
        mock(),
        handler,
        context,
        AndroidDebugManager(),
      )
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(
        latch.await(10L, TimeUnit.SECONDS),
      ) // Wait until worker posts the job for the "UI thread"
      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
        Thread.sleep(100L) // Let worker realize this is ANR
      } while (anr == null && waitCount++ < 100)
      assertNull(anr) // callback never ran
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  @Test
  fun `when getProcessesInErrorState runs out of memory, keeps watching without reporting`() {
    var anr: ApplicationNotResponding? = null
    val am = mock<ActivityManager>()
    whenever(am.processesInErrorState).thenThrow(OutOfMemoryError("large reply"))
    val posted = CountDownLatch(1)
    val sut = watchDog(am, posted) { a -> anr = a }
    val es = Executors.newSingleThreadExecutor()
    try {
      val running = es.submit { sut.run() }
      assertTrue(posted.await(10L, TimeUnit.SECONDS))

      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
        Thread.sleep(100L)
      } while (mockingDetails(am).invocations.isEmpty() && waitCount++ < 100)

      verify(am, atLeastOnce()).processesInErrorState
      assertNull(anr)
      assertFalse(running.isDone)
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  @Test
  fun `when getProcessesInErrorState throws a fatal error, the watchdog stops`() {
    val am = mock<ActivityManager>()
    whenever(am.processesInErrorState).thenThrow(StackOverflowError())
    val posted = CountDownLatch(1)
    val sut = watchDog(am, posted) {}
    val es = Executors.newSingleThreadExecutor()
    try {
      val running = es.submit { sut.run() }
      assertTrue(posted.await(10L, TimeUnit.SECONDS))

      var waitCount = 0
      do {
        ticker.advance(100, MILLISECONDS)
        Thread.sleep(100L)
      } while (!running.isDone && waitCount++ < 100)

      val error = assertFailsWith<ExecutionException> { running.get() }
      assertThat(error.cause).isInstanceOf(StackOverflowError::class.java)
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }

  private fun watchDog(
    am: ActivityManager,
    posted: CountDownLatch,
    listener: ANRWatchDog.ANRListener,
  ): ANRWatchDog {
    val handler = mock<MainLooperHandler>()
    // the posted runnable never runs, so the main thread looks blocked
    whenever(handler.post(any())).then { posted.countDown() }
    whenever(handler.thread).thenReturn(mock<Thread>())
    val context = mock<Context>()
    whenever(context.getSystemService(eq(Context.ACTIVITY_SERVICE))).thenReturn(am)
    return ANRWatchDog(
      ticker,
      10L,
      1L,
      true,
      listener,
      mock(),
      handler,
      context,
      AndroidDebugManager(),
    )
  }

  @Test
  fun `measures the ANR threshold on the ticker, not on wall time`() {
    var anr: ApplicationNotResponding? = null
    val handler = mock<MainLooperHandler>()
    val latch = CountDownLatch(1)
    whenever(handler.post(any())).then { latch.countDown() }
    whenever(handler.thread).thenReturn(mock<Thread>())

    // context is a mock, so ActivityManager is absent and any passed deadline reports an ANR
    val sut =
      ANRWatchDog(
        ticker,
        10L,
        1L,
        true,
        { a -> anr = a },
        mock(),
        handler,
        mock(),
        AndroidDebugManager(),
      )
    val es = Executors.newSingleThreadExecutor()
    try {
      es.submit { sut.run() }

      assertTrue(latch.await(10L, TimeUnit.SECONDS)) // wait until the watchdog is polling
      Thread.sleep(200) // hundreds of polls of wall time while the ticker stands still

      assertNull(anr)
    } finally {
      sut.interrupt()
      es.shutdown()
    }
  }
}
