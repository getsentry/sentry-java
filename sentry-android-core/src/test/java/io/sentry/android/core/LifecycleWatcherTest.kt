package io.sentry.android.core

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.IContinuousProfiler
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ReplayController
import io.sentry.ScopeCallback
import io.sentry.SentryExecutorService
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.Session.State
import io.sentry.time.EpochClock
import io.sentry.time.TestMonotonicTicker
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LifecycleWatcherTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val ticker = TestMonotonicTicker()
    // the wall clock, which only the staleness of a session already on the scope depends on
    val nowMillis = AtomicLong(0L)
    val epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(nowMillis.get())) }
    // a real executor so scheduled end-session tasks actually run
    val options = SentryOptions().apply { setTimerExecutorService(SentryExecutorService(this)) }
    val replayController = mock<ReplayController>()
    val continuousProfiler = mock<IContinuousProfiler>()

    fun getSUT(
      sessionIntervalMillis: Long = 0L,
      enableAutoSessionTracking: Boolean = true,
      enableAppLifecycleBreadcrumbs: Boolean = true,
      session: Session? = null,
    ): LifecycleWatcher {
      val argumentCaptor: ArgumentCaptor<ScopeCallback> =
        ArgumentCaptor.forClass(ScopeCallback::class.java)
      val scope = mock<IScope>()
      whenever(scope.session).thenReturn(session)
      whenever(scopes.configureScope(argumentCaptor.capture())).thenAnswer {
        argumentCaptor.value.run(scope)
      }
      options.setReplayController(replayController)
      options.setContinuousProfiler(continuousProfiler)
      whenever(scopes.options).thenReturn(options)

      return LifecycleWatcher(
        scopes,
        sessionIntervalMillis,
        enableAutoSessionTracking,
        enableAppLifecycleBreadcrumbs,
        ticker,
        epochClock,
      )
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `if last started session is 0, start new session`() {
    val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    verify(fixture.scopes).startSession()
    verify(fixture.replayController).onAppForegrounded(true)
  }

  @Test
  fun `if the background window has elapsed, start new session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    watcher.onBackground()
    fixture.ticker.advance(30000, MILLISECONDS)

    watcher.onForeground()

    verify(fixture.scopes, times(2)).startSession()
    verify(fixture.replayController, times(2)).onAppForegrounded(true)
  }

  @Test
  fun `if the app returns within the background window, it should not start a new session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    watcher.onBackground()
    fixture.ticker.advance(29999, MILLISECONDS)

    watcher.onForeground()

    verify(fixture.scopes).startSession()
    verify(fixture.replayController).onAppForegrounded(true)
    verify(fixture.replayController).onAppForegrounded(false)
  }

  @Test
  fun `a wall clock stepping forward during the background window does not rotate the session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    watcher.onBackground()

    // the device syncs its clock an hour forward, which two wall-clock reads used to report as an
    // hour spent in the background
    fixture.nowMillis.addAndGet(HOURS.toMillis(1))
    fixture.ticker.advance(1, MILLISECONDS)
    watcher.onForeground()

    verify(fixture.scopes).startSession()
    verify(fixture.replayController).onAppForegrounded(false)
  }

  @Test
  fun `a wall clock stepping backwards during the background window still rotates the session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    fixture.nowMillis.set(HOURS.toMillis(1))
    watcher.onForeground()
    watcher.onBackground()

    fixture.nowMillis.set(0L)
    fixture.ticker.advance(30000, MILLISECONDS)
    watcher.onForeground()

    verify(fixture.scopes, times(2)).startSession()
    verify(fixture.replayController, times(2)).onAppForegrounded(true)
  }

  @Test
  fun `if app goes to background, end session after interval`() {
    val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    watcher.onBackground()
    verify(fixture.scopes, timeout(10000)).endSession()
    verify(fixture.replayController, timeout(10000)).onAppSessionEnded()
    verify(fixture.continuousProfiler, timeout(10000)).close(eq(false))
  }

  @Test
  fun `if app goes to background and foreground again, dont end the session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()

    watcher.onBackground()
    assertNotNull(watcher.endSessionFuture)

    watcher.onForeground()
    assertNull(watcher.endSessionFuture)

    verify(fixture.scopes, never()).endSession()
    verify(fixture.replayController, never()).onAppSessionEnded()
  }

  @Test
  fun `When session tracking is disabled, do not start session`() {
    val watcher =
      fixture.getSUT(enableAutoSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    verify(fixture.scopes, never()).startSession()
  }

  @Test
  fun `When session tracking is disabled, do not end session`() {
    val watcher =
      fixture.getSUT(enableAutoSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
    watcher.onBackground()
    verify(fixture.scopes, never()).endSession()
  }

  @Test
  fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on start`() {
    val watcher = fixture.getSUT(enableAutoSessionTracking = false)
    watcher.onForeground()
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("app.lifecycle", it.category)
          assertEquals("navigation", it.type)
          assertEquals(SentryLevel.INFO, it.level)
          // cant assert data, its not a public API
        }
      )
  }

  @Test
  fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on start`() {
    val watcher =
      fixture.getSUT(enableAutoSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
  }

  @Test
  fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on stop`() {
    val watcher = fixture.getSUT(enableAutoSessionTracking = false)
    watcher.onBackground()
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("app.lifecycle", it.category)
          assertEquals("navigation", it.type)
          assertEquals(SentryLevel.INFO, it.level)
          // cant assert data, its not a public API
        }
      )
  }

  @Test
  fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on stop`() {
    val watcher =
      fixture.getSUT(enableAutoSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
    watcher.onBackground()
    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
  }

  @Test
  fun `if the scopes has already a fresh session running, don't start new one`() {
    val watcher =
      fixture.getSUT(
        enableAppLifecycleBreadcrumbs = false,
        session =
          Session(
            State.Ok,
            DateUtils.getCurrentDateTime(),
            DateUtils.getCurrentDateTime(),
            0,
            "abc",
            "3c1ffc32-f68f-4af2-a1ee-dd72f4d62d17",
            true,
            0,
            10.0,
            null,
            null,
            null,
            "release",
            null,
          ),
      )

    watcher.onForeground()
    verify(fixture.scopes, never()).startSession()
    verify(fixture.replayController).onAppForegrounded(false)
  }

  @Test
  fun `if the scopes has a long running session, start new one`() {
    val watcher =
      fixture.getSUT(
        enableAppLifecycleBreadcrumbs = false,
        session =
          Session(
            State.Ok,
            DateUtils.getDateTime(-1),
            DateUtils.getDateTime(-1),
            0,
            "abc",
            "3c1ffc32-f68f-4af2-a1ee-dd72f4d62d17",
            true,
            0,
            10.0,
            null,
            null,
            null,
            "release",
            null,
          ),
      )

    watcher.onForeground()
    verify(fixture.scopes).startSession()
    verify(fixture.replayController).onAppForegrounded(true)
  }

  @Test
  fun `background-foreground replay`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 500L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    verify(fixture.replayController).onAppForegrounded(true)

    watcher.onBackground()
    verify(fixture.replayController).onAppBackgrounded()

    watcher.onForeground()
    verify(fixture.replayController).onAppForegrounded(false)

    watcher.onBackground()
    verify(fixture.replayController, timeout(10000)).onAppSessionEnded()
  }
}
