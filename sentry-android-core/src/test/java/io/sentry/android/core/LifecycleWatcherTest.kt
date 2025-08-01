package io.sentry.android.core

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.IContinuousProfiler
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ReplayController
import io.sentry.ScopeCallback
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.Session.State
import io.sentry.transport.ICurrentDateProvider
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
    val dateProvider = mock<ICurrentDateProvider>()
    val options = SentryOptions()
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
        dateProvider,
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
    verify(fixture.replayController).start()
  }

  @Test
  fun `if last started session is after interval, start new session`() {
    val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
    whenever(fixture.dateProvider.currentTimeMillis).thenReturn(1L, 2L)
    watcher.onForeground()
    watcher.onForeground()
    verify(fixture.scopes, times(2)).startSession()
    verify(fixture.replayController, times(2)).start()
  }

  @Test
  fun `if last started session is before interval, it should not start a new session`() {
    val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
    whenever(fixture.dateProvider.currentTimeMillis).thenReturn(2L, 1L)
    watcher.onForeground()
    watcher.onForeground()
    verify(fixture.scopes).startSession()
    verify(fixture.replayController).start()
  }

  @Test
  fun `if app goes to background, end session after interval`() {
    val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    watcher.onBackground()
    verify(fixture.scopes, timeout(10000)).endSession()
    verify(fixture.replayController, timeout(10000)).stop()
    verify(fixture.continuousProfiler, timeout(10000)).close(eq(false))
  }

  @Test
  fun `if app goes to background and foreground again, dont end the session`() {
    val watcher =
      fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()

    watcher.onBackground()
    assertNotNull(watcher.timerTask)

    watcher.onForeground()
    assertNull(watcher.timerTask)

    verify(fixture.scopes, never()).endSession()
    verify(fixture.replayController, never()).stop()
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
  fun `timer is created if session tracking is enabled`() {
    val watcher =
      fixture.getSUT(enableAutoSessionTracking = true, enableAppLifecycleBreadcrumbs = false)
    assertNotNull(watcher.timer)
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
    verify(fixture.replayController, never()).start()
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
    verify(fixture.replayController).start()
  }

  @Test
  fun `if the hub has already a fresh session running, resumes replay to invalidate isManualPause flag`() {
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
    verify(fixture.replayController).resume()
  }

  @Test
  fun `background-foreground replay`() {
    whenever(fixture.dateProvider.currentTimeMillis).thenReturn(1L)
    val watcher = fixture.getSUT(sessionIntervalMillis = 2L, enableAppLifecycleBreadcrumbs = false)
    watcher.onForeground()
    verify(fixture.replayController).start()

    watcher.onBackground()
    verify(fixture.replayController).pause()

    watcher.onForeground()
    verify(fixture.replayController, times(2)).resume()

    watcher.onBackground()
    verify(fixture.replayController, timeout(10000)).stop()
  }
}
