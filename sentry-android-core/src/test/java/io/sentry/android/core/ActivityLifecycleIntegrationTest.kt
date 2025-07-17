package io.sentry.android.core

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DateUtils
import io.sentry.FullyDisplayedReporter
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Scopes
import io.sentry.Sentry
import io.sentry.SentryDate
import io.sentry.SentryDateProvider
import io.sentry.SentryNanotimeDate
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanStatus
import io.sentry.SpanStatus.OK
import io.sentry.TraceContext
import io.sentry.TransactionContext
import io.sentry.TransactionFinishedCallback
import io.sentry.TransactionOptions
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.TransactionNameSource
import io.sentry.test.DeferredExecutorService
import io.sentry.test.getProperty
import java.util.Date
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager

@RunWith(AndroidJUnit4::class)
class ActivityLifecycleIntegrationTest {

  private class Fixture {
    val application = mock<Application>()
    val scopes = mock<Scopes>()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" }
    val bundle = mock<Bundle>()
    val activityFramesTracker = mock<ActivityFramesTracker>()
    val transactionFinishedCallback = mock<TransactionFinishedCallback>()
    lateinit var shadowActivityManager: ShadowActivityManager

    // we init the transaction with a mock to avoid errors when finishing it after tests that don't
    // start it
    var transaction: SentryTracer = mock()
    val buildInfo = mock<BuildInfoProvider>()

    fun getSut(
      apiVersion: Int = Build.VERSION_CODES.Q,
      importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
      initializer: Sentry.OptionsConfiguration<SentryAndroidOptions>? = null,
    ): ActivityLifecycleIntegration {
      initializer?.configure(options)

      whenever(scopes.options).thenReturn(options)

      val metrics = AppStartMetrics.getInstance()
      metrics.isAppLaunchedInForeground = true
      metrics.appStartTimeSpan.start()

      // We let the ActivityLifecycleIntegration create the proper transaction here
      val optionCaptor = argumentCaptor<TransactionOptions>()
      val contextCaptor = argumentCaptor<TransactionContext>()
      whenever(scopes.startTransaction(contextCaptor.capture(), optionCaptor.capture()))
        .thenAnswer {
          val t = SentryTracer(contextCaptor.lastValue, scopes, optionCaptor.lastValue)
          transaction = t
          return@thenAnswer t
        }
      whenever(buildInfo.sdkInfoVersion).thenReturn(apiVersion)

      val process = RunningAppProcessInfo().apply { this.importance = importance }
      val processes = mutableListOf(process)
      shadowActivityManager.setProcesses(processes)

      return ActivityLifecycleIntegration(application, buildInfo, activityFramesTracker)
    }

    fun createView(): View {
      val view = View(ApplicationProvider.getApplicationContext())

      // Adding a listener forces ViewTreeObserver.mOnDrawListeners to be initialized and non-null.
      val dummyListener = ViewTreeObserver.OnDrawListener {}
      view.viewTreeObserver.addOnDrawListener(dummyListener)
      view.viewTreeObserver.removeOnDrawListener(dummyListener)

      return view
    }
  }

  private val fixture = Fixture()
  private lateinit var context: Context

  @BeforeTest
  fun `reset instance`() {
    AppStartMetrics.getInstance().clear()
    ContextUtils.resetInstance()

    context = ApplicationProvider.getApplicationContext()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    fixture.shadowActivityManager = Shadow.extract(activityManager)
  }

  @AfterTest
  fun `clear instance`() {
    fixture.transaction.finish()
  }

  @Test
  fun `When ActivityLifecycleIntegration is registered, it registers activity callback`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `When ActivityLifecycleIntegration is closed, it should unregister the callback`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `When ActivityLifecycleIntegration is closed, it should close the ActivityFramesTracker`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    verify(fixture.activityFramesTracker).stop()
  }

  @Test
  fun `When tracing is disabled, do not start tracing`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes, never()).startTransaction(any(), any<TransactionOptions>())
  }

  @Test
  fun `When tracing is enabled but activity is running, do not start tracing again`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes).startTransaction(any(), any<TransactionOptions>())
  }

  @Test
  fun `Transaction op is ui_load and idle+deadline timeouts are set`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        check<TransactionContext> {
          assertEquals("ui.load", it.operation)
          assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
        },
        check<TransactionOptions> { transactionOptions ->
          assertEquals(fixture.options.idleTimeout, transactionOptions.idleTimeout)
          assertEquals(
            TransactionOptions.DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION,
            transactionOptions.deadlineTimeout,
          )
          assertEquals("auto.ui.activity", transactionOptions.origin)
        },
      )
  }

  @Test
  fun `Activity transaction uses custom deadline timeout when autoTransactionDeadlineTimeoutMillis is set to positive value`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.deadlineTimeout = 60000L // 60 seconds

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(mock(), fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { transactionOptions ->
          assertEquals(60000L, transactionOptions.deadlineTimeout)
        },
      )
  }

  @Test
  fun `Activity transaction uses no deadline timeout when autoTransactionDeadlineTimeoutMillis is set to zero`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.deadlineTimeout = 0L // No deadline

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(mock(), fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { transactionOptions ->
          assertNull(transactionOptions.deadlineTimeout)
        },
      )
  }

  @Test
  fun `Activity transaction uses no deadline timeout when autoTransactionDeadlineTimeoutMillis is set to negative value`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.deadlineTimeout = -1L // No deadline

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(mock(), fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { transactionOptions ->
          assertNull(transactionOptions.deadlineTimeout)
        },
      )
  }

  @Test
  fun `Activity transaction uses default deadline timeout when autoTransactionDeadlineTimeoutMillis is default`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(mock(), fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { transactionOptions ->
          assertEquals(
            TransactionOptions.DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION,
            transactionOptions.deadlineTimeout,
          )
        },
      )
  }

  @Test
  fun `Activity gets added to ActivityFramesTracker during transaction creation`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityStarted(activity)

    verify(fixture.activityFramesTracker).addActivity(eq(activity))
  }

  @Test
  fun `Transaction name is the Activity's name`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        check {
          assertEquals("Activity", it.name)
          assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
        },
        any<TransactionOptions>(),
      )
  }

  @Test
  fun `When transaction is created, set transaction to the bound Scope`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0

    sut.register(fixture.scopes, fixture.options)

    whenever(fixture.scopes.configureScope(any())).thenAnswer {
      val scope = Scope(fixture.options)

      sut.applyScope(scope, fixture.transaction)

      assertNotNull(scope.transaction)
    }

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
  }

  @Test
  fun `When transaction is created, do not overwrite transaction already bound to the Scope`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0

    sut.register(fixture.scopes, fixture.options)

    whenever(fixture.scopes.configureScope(any())).thenAnswer {
      val scope = Scope(fixture.options)
      val previousTransaction = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
      scope.transaction = previousTransaction

      sut.applyScope(scope, fixture.transaction)

      assertEquals(previousTransaction, scope.transaction)
    }

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
  }

  @Test
  fun `When tracing auto finish is enabled and ttid and ttfd spans are finished, it schedules the transaction finish`() {
    val activity = mock<Activity>()
    val sut =
      fixture.getSut(
        initializer = {
          it.tracesSampleRate = 1.0
          it.isEnableTimeToFullDisplayTracing = true
          it.idleTimeout = 100
        }
      )
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    sut.ttidSpanMap.values.first().finish()
    sut.ttfdSpanMap.values.first().finish()

    // then transaction should not be immediately finished
    verify(fixture.scopes, never())
      .captureTransaction(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

    // but when idle timeout has passed
    Thread.sleep(200)

    // then the transaction should be finished
    verify(fixture.scopes)
      .captureTransaction(
        check { assertEquals(SpanStatus.OK, it.status) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `When tracing auto finish is enabled, it doesn't stop the transaction on onActivityPostResumed`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityPostResumed(activity)

    verify(fixture.scopes, never())
      .captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `When tracing has status, do not overwrite it`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    fixture.transaction.status = SpanStatus.UNKNOWN_ERROR

    sut.onActivityPostResumed(activity)
    sut.onActivityDestroyed(activity)

    verify(fixture.scopes)
      .captureTransaction(
        check { assertEquals(SpanStatus.UNKNOWN_ERROR, it.status) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `When tracing auto finish is disabled, do not finish transaction`() {
    val sut =
      fixture.getSut(
        initializer = {
          it.tracesSampleRate = 1.0
          it.isEnableActivityLifecycleTracingAutoFinish = false
        }
      )
    sut.register(fixture.scopes, fixture.options)
    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    // We don't schedule the transaction to finish
    assertFalse(fixture.transaction.isFinishing())
    assertFalse(fixture.transaction.isFinished)
  }

  @Test
  fun `When tracing is disabled, do not finish transaction`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityPostResumed(activity)

    verify(fixture.scopes, never())
      .captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `When Activity is destroyed but transaction is running, finish it`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityDestroyed(activity)

    verify(fixture.scopes)
      .captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `When transaction is started, adds to WeakWef`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    assertFalse(sut.activitiesWithOngoingTransactions.isEmpty())
  }

  @Test
  fun `When Activity is destroyed removes WeakRef`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityDestroyed(activity)

    assertTrue(sut.activitiesWithOngoingTransactions.isEmpty())
  }

  @Test
  fun `When Activity is destroyed, sets appStartSpan status to cancelled and finish it`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityDestroyed(activity)

    val span = fixture.transaction.children.first()
    assertEquals(SpanStatus.CANCELLED, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `When Activity is destroyed, sets appStartSpan to null`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityDestroyed(activity)

    assertNull(sut.appStartSpan)
  }

  @Test
  fun `When Activity is destroyed, finish ttidSpan with deadline_exceeded and remove from map`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    assertNotNull(sut.ttidSpanMap[activity])
    sut.onActivityDestroyed(activity)

    val span =
      fixture.transaction.children.first { it.operation == ActivityLifecycleIntegration.TTID_OP }
    assertEquals(SpanStatus.DEADLINE_EXCEEDED, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `When Activity is destroyed, sets ttidSpan to null`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    assertNotNull(sut.ttidSpanMap[activity])

    sut.onActivityDestroyed(activity)
    assertNull(sut.ttidSpanMap[activity])
  }

  @Test
  fun `When Activity is destroyed, finish ttfdSpan with deadline_exceeded and remove from map`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    assertNotNull(sut.ttfdSpanMap[activity])
    sut.onActivityDestroyed(activity)

    val span =
      fixture.transaction.children.first { it.operation == ActivityLifecycleIntegration.TTFD_OP }
    assertEquals(SpanStatus.DEADLINE_EXCEEDED, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `When Activity is destroyed, sets ttfdSpan to null`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)

    setAppStartTime()

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    assertNotNull(sut.ttfdSpanMap[activity])

    sut.onActivityDestroyed(activity)
    assertNull(sut.ttfdSpanMap[activity])
  }

  @Test
  fun `When new Activity and transaction is created, finish previous ones`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    sut.onActivityCreated(mock(), mock())

    sut.onActivityCreated(mock(), fixture.bundle)
    verify(fixture.scopes)
      .captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `do not stop transaction on resumed`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, mock())
    sut.onActivityResumed(activity)

    verify(fixture.scopes, never()).captureTransaction(any(), any(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `start transaction on created`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(mock(), mock())

    verify(fixture.scopes).startTransaction(any(), any<TransactionOptions>())
  }

  @Test
  fun `stop transaction on resumed does not finish ttfd if isEnableTimeToFullDisplayTracing`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    fixture.options.idleTimeout = 0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, mock())
    val ttfd = sut.ttfdSpanMap[activity]
    sut.ttidSpanMap.values.first().finish()
    sut.onActivityResumed(activity)
    sut.onActivityPostResumed(activity)
    runFirstDraw(fixture.createView())

    assertNotNull(ttfd)
    assertFalse(ttfd.isFinished)
  }

  @Test
  fun `reportFullyDrawn finishes the ttfd`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, mock())
    val ttfdSpan = sut.ttfdSpanMap[activity]
    sut.ttidSpanMap.values.first().finish()
    fixture.options.fullyDisplayedReporter.reportFullyDrawn()
    assertTrue(ttfdSpan!!.isFinished)
    assertNotEquals(SpanStatus.CANCELLED, ttfdSpan.status)
  }

  @Test
  fun `if ttfd is disabled, no listener is registered for FullyDisplayedReporter`() {
    val ttfdReporter = mock<FullyDisplayedReporter>()

    val sut = fixture.getSut()
    fixture.options.apply {
      tracesSampleRate = 1.0
      isEnableTimeToFullDisplayTracing = false
      fullyDisplayedReporter = ttfdReporter
    }

    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, mock())

    verify(ttfdReporter, never()).registerFullyDrawnListener(any())
  }

  @Test
  fun `When firstActivityCreated is false, start transaction with given appStartTime`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(false)

    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(date)
    fixture.options.dateProvider = SentryDateProvider { date }

    val activity = mock<Activity>()
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    // call only once
    verify(fixture.scopes)
      .startTransaction(
        any(),
        check<TransactionOptions> {
          assertEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
        },
      )
  }

  @Test
  fun `When firstActivityCreated is false and app start sampling decision is set, start transaction with isAppStart true`() {
    AppStartMetrics.getInstance().appStartSamplingDecision = mock()
    val sut = fixture.getSut { it.tracesSampleRate = 1.0 }
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(false)

    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(date)

    val activity = mock<Activity>()
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any(),
        check<TransactionOptions> {
          assertEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
          assertTrue(it.isAppStartTransaction)
        },
      )
  }

  @Test
  fun `When firstActivityCreated is false and app start sampling decision is not set, start transaction with isAppStart false`() {
    val sut = fixture.getSut { it.tracesSampleRate = 1.0 }
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(false)

    val date = SentryNanotimeDate(Date(1), 0)
    val date2 = SentryNanotimeDate(Date(2), 2)
    setAppStartTime(date)

    val activity = mock<Activity>()
    // The activity onCreate date will be ignored
    fixture.options.dateProvider = SentryDateProvider { date2 }
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any(),
        check<TransactionOptions> {
          assertEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
          assertNotEquals(date2.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
          assertFalse(it.isAppStartTransaction)
        },
      )
  }

  @Test
  fun `When firstActivityCreated is true and app start sampling decision is set, start transaction with isAppStart false`() {
    AppStartMetrics.getInstance().appStartSamplingDecision = mock()
    val sut = fixture.getSut { it.tracesSampleRate = 1.0 }
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(true)
    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(date)

    val activity = mock<Activity>()
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(any(), check<TransactionOptions> { assertFalse(it.isAppStartTransaction) })
  }

  @Test
  fun `When firstActivityCreated is false and no app start time is set, default to onActivityPreCreated time`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(false)

    // usually set by SentryPerformanceProvider
    val date = SentryNanotimeDate(Date(1), 0)
    val date2 = SentryNanotimeDate(Date(2), 2)

    val activity = mock<Activity>()
    // Activity onCreate date will be used
    fixture.options.dateProvider = SentryDateProvider { date2 }
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    verify(fixture.scopes)
      .startTransaction(
        any(),
        check<TransactionOptions> {
          assertEquals(date2.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
          assertNotEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
        },
      )
  }

  @Test
  fun `When not foregroundImportance, do not create app start span`() {
    val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_BACKGROUND)
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    // usually set by SentryPerformanceProvider
    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(date)

    val activity = mock<Activity>()
    sut.onActivityPreCreated(activity, fixture.bundle)
    sut.onActivityCreated(activity, fixture.bundle)

    // call only once
    verify(fixture.scopes)
      .startTransaction(
        any(),
        check<TransactionOptions> {
          assertNotEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
        },
      )
  }

  @Test
  fun `Create and finish app start span immediately in case SDK init is deferred`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    // usually set by SentryPerformanceProvider
    val startDate = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(startDate)
    val appStartMetrics = AppStartMetrics.getInstance()
    appStartMetrics.appStartType = AppStartType.WARM
    appStartMetrics.sdkInitTimeSpan.setStoppedAt(2)
    appStartMetrics.appStartTimeSpan.setStoppedAt(2)

    val activity = mock<Activity>()
    // An Activity already started, as SDK init is deferred
    sut.onActivityPrePaused(activity)
    sut.onActivityPaused(activity)
    // And when we create a new Activity
    sut.onActivityPreCreated(activity, null)
    sut.onActivityCreated(activity, null)
    sut.onActivityStopped(activity)
    sut.onActivityDestroyed(activity)

    // No app start span is created
    val appStartSpan =
      fixture.transaction.children.firstOrNull {
        it.operation.startsWith("app.start.warm") || it.operation.startsWith("app.start.cold")
      }
    assertNull(appStartSpan)
  }

  @Test
  fun `When SentryPerformanceProvider is disabled, app start time span is still created`() {
    val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    // usually done by SentryPerformanceProvider, if disabled it's done by
    // SentryAndroid.init
    val startDate = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(startDate)
    AppStartMetrics.getInstance().appStartType = AppStartType.WARM

    // when activity is created
    val view = fixture.createView()
    val activity = mock<Activity>()
    whenever(activity.findViewById<View>(any())).thenReturn(view)
    sut.onActivityCreated(activity, fixture.bundle)
    // then app-start end time should still be null
    assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasNotStopped())

    // when activity is resumed
    sut.onActivityResumed(activity)
    runFirstDraw(view)
    // end-time should be set
    assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasStopped())
  }

  @Test
  fun `When app-start end time is already set, it should not be overwritten`() {
    val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    // usually done by SentryPerformanceProvider
    val startDate = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(startDate)
    AppStartMetrics.getInstance().appStartType = AppStartType.WARM
    AppStartMetrics.getInstance().sdkInitTimeSpan.setStoppedAt(1234)

    // when activity is created and resumed
    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityResumed(activity)

    // then the end time should not be overwritten
    assertEquals(
      DateUtils.millisToNanos(1234),
      AppStartMetrics.getInstance().sdkInitTimeSpan.projectedStopTimestamp!!.nanoTimestamp(),
    )
  }

  @Test
  fun `When activity lifecycle happens multiple times, app-start end time should not be overwritten`() {
    val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    // usually done by SentryPerformanceProvider
    val startDate = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(startDate)
    AppStartMetrics.getInstance().appStartType = AppStartType.WARM

    // when activity is created, started and resumed multiple times
    val view = fixture.createView()
    val activity = mock<Activity>()
    whenever(activity.findViewById<View>(any())).thenReturn(view)
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityStarted(activity)
    sut.onActivityResumed(activity)
    runFirstDraw(view)

    val firstAppStartEndTime = AppStartMetrics.getInstance().sdkInitTimeSpan.projectedStopTimestamp

    sut.onActivityPaused(activity)
    sut.onActivityStopped(activity)
    sut.onActivityStarted(activity)
    sut.onActivityResumed(activity)
    runFirstDraw(view)

    // then the end time should not be overwritten
    assertEquals(
      firstAppStartEndTime!!.nanoTimestamp(),
      AppStartMetrics.getInstance().sdkInitTimeSpan.projectedStopTimestamp!!.nanoTimestamp(),
    )
  }

  @Test
  fun `When firstActivityCreated is true, start transaction but not with given appStartTime`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(true)

    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime()

    val activity = mock<Activity>()
    // First invocation: we expect to start a transaction with the appStartTime
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityPostResumed(activity)
    assertNotEquals(date.nanoTimestamp(), fixture.transaction.startDate.nanoTimestamp())
  }

  @Test
  fun `When transaction is finished, it gets removed from scope`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    whenever(fixture.scopes.configureScope(any())).thenAnswer {
      val scope = Scope(fixture.options)

      scope.transaction = fixture.transaction

      sut.clearScope(scope, fixture.transaction)

      assertNull(scope.transaction)
    }

    sut.onActivityDestroyed(activity)
  }

  @Test
  fun `When transaction is started and isEnableTimeToFullDisplayTracing is disabled, no ttfd span is started`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = false
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    val ttfdSpan = sut.ttfdSpanMap[activity]
    assertNull(ttfdSpan)
  }

  @Test
  fun `When transaction is started and isEnableTimeToFullDisplayTracing is enabled, ttfd span is started`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    val ttfdSpan = sut.ttfdSpanMap[activity]
    assertNotNull(ttfdSpan)
  }

  @Test
  fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is not called, ttfd span is finished automatically with timeout`() {
    val sut = fixture.getSut()
    val deferredExecutorService = DeferredExecutorService()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    fixture.options.executorService = deferredExecutorService
    sut.register(fixture.scopes, fixture.options)
    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    val ttfdSpan = sut.ttfdSpanMap[activity]

    // Assert the ttfd span is running and a timeout autoCancel task has been scheduled
    assertNotNull(ttfdSpan)
    assertFalse(ttfdSpan.isFinished)
    assertTrue(deferredExecutorService.hasScheduledRunnables())

    // Run the autoClose task and assert the ttfd span is finished with deadlineExceeded
    deferredExecutorService.runAll()
    assertTrue(ttfdSpan.isFinished)
    assertEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)

    sut.onActivityDestroyed(activity)
    verify(fixture.scopes)
      .captureTransaction(
        check {
          // ttfd timed out, so its measurement should not be set
          val ttfdMeasurement = it.measurements[MeasurementValue.KEY_TIME_TO_FULL_DISPLAY]
          assertNull(ttfdMeasurement)
        },
        any(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is called, ttfd is finished on first frame if ttid is running`() {
    val sut = fixture.getSut()
    val view = fixture.createView()
    val activity = mock<Activity>()
    whenever(activity.findViewById<View>(any())).thenReturn(view)
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityResumed(activity)
    val ttidSpan = sut.ttidSpanMap[activity]
    val ttfdSpan = sut.ttfdSpanMap[activity]

    // Assert the ttfd span is running and a timeout autoCancel future has been scheduled
    assertNotNull(ttidSpan)
    assertNotNull(ttfdSpan)
    assertFalse(ttidSpan.isFinished)
    assertFalse(ttfdSpan.isFinished)

    // ReportFullyDrawn should not finish the ttfd span, as the ttid is still running
    fixture.options.fullyDisplayedReporter.reportFullyDrawn()
    assertFalse(ttfdSpan.isFinished)

    // But when ReportFullyDrawn should not finish the ttfd span, as the ttid is still running
    runFirstDraw(view)
    assertTrue(ttidSpan.isFinished)
    assertTrue(ttfdSpan.isFinished)
  }

  @Test
  fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is called, ttfd autoClose future is cancelled`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)
    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    val ttidSpan = sut.ttidSpanMap[activity]
    val ttfdSpan = sut.ttfdSpanMap[activity]
    var autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")
    ttidSpan?.finish()

    // Assert the ttfd span is running and a timeout autoCancel future has been scheduled
    assertNotNull(ttfdSpan)
    assertFalse(ttfdSpan.isFinished)
    assertNotNull(autoCloseFuture)

    // ReportFullyDrawn should finish the ttfd span and cancel the future
    fixture.options.fullyDisplayedReporter.reportFullyDrawn()
    assertTrue(ttfdSpan.isFinished)
    assertNotEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
    assertTrue(autoCloseFuture.isCancelled)

    // The current internal reference to autoClose future should be null after ReportFullyDrawn
    autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")
    assertNull(autoCloseFuture)

    sut.onActivityDestroyed(activity)
    verify(fixture.scopes)
      .captureTransaction(
        check {
          // ttfd was finished successfully, so its measurement should be set
          val ttfdMeasurement = it.measurements[MeasurementValue.KEY_TIME_TO_FULL_DISPLAY]
          assertNotNull(ttfdMeasurement)
          assertTrue(ttfdMeasurement.value.toLong() > 0)
        },
        any(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `When isEnableTimeToFullDisplayTracing is true and another activity starts, the old ttfd is finished and the old autoClose future is cancelled`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    sut.register(fixture.scopes, fixture.options)
    val activity = mock<Activity>()
    val activity2 = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityPostResumed(activity)
    val ttfdSpan = sut.ttfdSpanMap[activity]
    val autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")

    // Assert the ttfd span is running and a timeout autoCancel future has been scheduled
    assertNotNull(ttfdSpan)
    assertFalse(ttfdSpan.isFinished)
    assertNotNull(autoCloseFuture)

    // Starting a new Activity should finish the old ttfd span with deadlineExceeded and cancel the
    // old future
    sut.onActivityCreated(activity2, fixture.bundle)
    assertTrue(ttfdSpan.isFinished)
    assertEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
    assertTrue(autoCloseFuture.isCancelled)

    // Another autoClose future and ttfd span should be started after the second activity starts
    val autoCloseFuture2 = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")
    val ttfdSpan2 = sut.ttfdSpanMap[activity2]
    assertNotNull(ttfdSpan2)
    assertFalse(ttfdSpan2.isFinished)
    assertNotNull(autoCloseFuture2)
    assertFalse(autoCloseFuture2.isCancelled)
    sut.onActivityDestroyed(activity)
  }

  @Test
  fun `ttid is finished after first frame drawn`() {
    val sut = fixture.getSut()
    val view = fixture.createView()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = 1.0
    whenever(activity.findViewById<View>(any())).thenReturn(view)

    // Make the integration create the spans and register to the FirstDrawDoneListener
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityResumed(activity)

    // The ttid span should be running
    val ttidSpan = sut.ttidSpanMap[activity]
    assertNotNull(ttidSpan)
    assertFalse(ttidSpan.isFinished)

    // Mock the draw of the view. The ttid span should finish now
    runFirstDraw(view)
    assertTrue(ttidSpan.isFinished)

    sut.onActivityDestroyed(activity)
    verify(fixture.scopes)
      .captureTransaction(
        check {
          // ttid measurement should be set
          val ttidMeasurement = it.measurements[MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY]
          assertNotNull(ttidMeasurement)
          assertTrue(ttidMeasurement.value.toLong() > 0)
        },
        any(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is called too early, ttfd is adjusted to equal ttid`() {
    val sut =
      fixture.getSut() {
        //            it.fullyDisplayedReporter = mock()
      }
    val view = fixture.createView()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    whenever(activity.findViewById<View>(any())).thenReturn(view)

    // Make the integration create the spans and register to the FirstDrawDoneListener
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityResumed(activity)

    // The ttid and ttfd spans should be running
    val ttidSpan = sut.ttidSpanMap[activity] as Span
    val ttfdSpan = sut.ttfdSpanMap[activity] as Span
    assertFalse(ttidSpan.isFinished)
    assertFalse(ttfdSpan.isFinished)

    // Let's finish the ttfd span too early (before the first view is drawn)
    fixture.options.fullyDisplayedReporter.reportFullyDrawn()

    // The TTFD shouldn't be finished yet
    assertFalse(ttfdSpan.isFinished)

    // Mock the draw of the view. The ttid span should finish now and the ttfd, too
    runFirstDraw(view)
    assertTrue(ttidSpan.isFinished)
    assertTrue(ttfdSpan.isFinished)
    assertEquals(ttfdSpan.finishDate, ttidSpan.finishDate)

    sut.onActivityDestroyed(activity)

    // The measurements should be set to the same value for ttid and ttfd
    val ttidDuration = TimeUnit.NANOSECONDS.toMillis(ttidSpan.finishDate!!.diff(ttidSpan.startDate))
    val ttfdDuration = TimeUnit.NANOSECONDS.toMillis(ttfdSpan.finishDate!!.diff(ttfdSpan.startDate))
    assertEquals(ttidDuration, ttfdDuration)
    // TTID also has initial display measurement, but TTFD has not
    assertEquals(
      ttidDuration,
      ttidSpan.measurements[MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY]!!.value,
    )
    assertEquals(
      ttidDuration,
      ttidSpan.measurements[MeasurementValue.KEY_TIME_TO_FULL_DISPLAY]!!.value,
    )
    assertEquals(
      ttidDuration,
      ttfdSpan.measurements[MeasurementValue.KEY_TIME_TO_FULL_DISPLAY]!!.value,
    )

    verify(fixture.scopes)
      .captureTransaction(
        check {
          // ttid and ttfd measurements should be the same
          val ttidMeasurement = it.measurements[MeasurementValue.KEY_TIME_TO_INITIAL_DISPLAY]
          val ttfdMeasurement = it.measurements[MeasurementValue.KEY_TIME_TO_FULL_DISPLAY]
          assertNotNull(ttidMeasurement)
          assertNotNull(ttfdMeasurement)
          assertEquals(ttidMeasurement.value, ttfdMeasurement.value)
        },
        any(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `transaction has same start timestamp of ttid and ttfd`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    // The ttid span should be running
    val ttidSpan = sut.ttidSpanMap[activity]
    val ttfdSpan = sut.ttfdSpanMap[activity]
    assertNotNull(ttidSpan)
    assertNotNull(ttfdSpan)

    assertEquals(ttidSpan.startDate, fixture.transaction.startDate)
    assertEquals(ttfdSpan.startDate, fixture.transaction.startDate)
  }

  @Test
  fun `ttfd span is trimmed if reportFullyDisplayed is never called`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    val view = fixture.createView()
    val deferredExecutorService = DeferredExecutorService()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true
    fixture.options.executorService = deferredExecutorService
    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)
    sut.onActivityResumed(activity)

    runFirstDraw(view)
    val ttidSpan = sut.ttidSpanMap[activity]
    val ttfdSpan = sut.ttfdSpanMap[activity]

    // The ttid should be finished
    assertNotNull(ttidSpan)
    assertTrue(ttidSpan.isFinished)

    // Assert the ttfd span is still running
    assertNotNull(ttfdSpan)
    assertFalse(ttfdSpan.isFinished)

    // Run the autoClose task 1 ms after finishing the ttid span and assert the ttfd span is
    // finished
    deferredExecutorService.runAll()
    assertTrue(ttfdSpan.isFinished)

    // the ttfd span should be trimmed to be equal to the ttid span, and the description should end
    // with "-exceeded"
    assertEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
    assertEquals(ttidSpan.finishDate, ttfdSpan.finishDate)
    assertEquals(ttfdSpan.description, "Activity full display - Deadline Exceeded")
  }

  @Test
  fun `ttfd span is running on new activity when previous finishes`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    val activity2 = mock<Activity>()
    fixture.options.tracesSampleRate = 1.0
    fixture.options.isEnableTimeToFullDisplayTracing = true

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)
    val ttfdSpan = sut.ttfdSpanMap[activity]
    assertNotNull(ttfdSpan)
    assertFalse(ttfdSpan.isFinished)
    sut.onActivityPaused(activity)
    sut.onActivityCreated(activity2, fixture.bundle)
    val ttfdSpan2 = sut.ttfdSpanMap[activity2]
    sut.onActivityResumed(activity2)
    sut.onActivityStopped(activity)
    sut.onActivityDestroyed(activity)
    assertNotNull(ttfdSpan2)
    // The old ttfd is finished and the new one is running
    assertTrue(ttfdSpan.isFinished)
    assertFalse(ttfdSpan2.isFinished)
  }

  @Test
  fun `starts new trace if performance is disabled and trace ID generation is enabled`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = null
    fixture.options.isEnableAutoTraceIdGeneration = true

    val argumentCaptor: ArgumentCaptor<ScopeCallback> =
      ArgumentCaptor.forClass(ScopeCallback::class.java)
    val scope = Scope(fixture.options)
    val propagationContextAtStart = scope.propagationContext
    whenever(fixture.scopes.configureScope(argumentCaptor.capture())).thenAnswer {
      argumentCaptor.value.run(scope)
    }

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    // once for the screen, and once for the tracing propagation context
    verify(fixture.scopes, times(2)).configureScope(any())
    assertNotSame(propagationContextAtStart, scope.propagationContext)
  }

  @Test
  fun `does not start a new trace if performance is disabled and trace ID generation is disabled`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = null
    fixture.options.isEnableAutoTraceIdGeneration = false

    val argumentCaptor: ArgumentCaptor<ScopeCallback> =
      ArgumentCaptor.forClass(ScopeCallback::class.java)
    val scope = Scope(fixture.options)
    val propagationContextAtStart = scope.propagationContext
    whenever(fixture.scopes.configureScope(argumentCaptor.capture())).thenAnswer {
      argumentCaptor.value.run(scope)
    }

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    // once for the screen
    verify(fixture.scopes).configureScope(any())
    assertSame(propagationContextAtStart, scope.propagationContext)
  }

  @Test
  fun `sets the activity as the current screen`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = null

    val argumentCaptor: ArgumentCaptor<ScopeCallback> =
      ArgumentCaptor.forClass(ScopeCallback::class.java)
    val scope = mock<IScope>()
    whenever(fixture.scopes.configureScope(argumentCaptor.capture())).thenAnswer {
      argumentCaptor.value.run(scope)
    }

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    // once for the screen, and once for the tracing propagation context
    verify(fixture.scopes, times(2)).configureScope(any())
    verify(scope).setScreen(any())
  }

  @Test
  fun `does not start another new trace if one has already been started but does after activity was destroyed`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()
    fixture.options.tracesSampleRate = null

    val argumentCaptor: ArgumentCaptor<ScopeCallback> =
      ArgumentCaptor.forClass(ScopeCallback::class.java)
    val scope = Scope(fixture.options)
    val propagationContextAtStart = scope.propagationContext
    whenever(fixture.scopes.configureScope(argumentCaptor.capture())).thenAnswer {
      argumentCaptor.value.run(scope)
    }

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, fixture.bundle)

    // once for the screen, and once for the tracing propagation context
    verify(fixture.scopes, times(2)).configureScope(any())

    val propagationContextAfterNewTrace = scope.propagationContext
    assertNotSame(propagationContextAtStart, propagationContextAfterNewTrace)

    clearInvocations(fixture.scopes)
    sut.onActivityCreated(activity, fixture.bundle)

    // once for the screen, but not for the tracing propagation context
    verify(fixture.scopes).configureScope(any())
    assertSame(propagationContextAfterNewTrace, scope.propagationContext)

    sut.onActivityDestroyed(activity)

    clearInvocations(fixture.scopes)
    sut.onActivityCreated(activity, fixture.bundle)
    // once for the screen, and once for the tracing propagation context
    verify(fixture.scopes, times(2)).configureScope(any())
    assertNotSame(propagationContextAfterNewTrace, scope.propagationContext)
  }

  @Test
  fun `when transaction is finished, sets frame metrics`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val activity = mock<Activity>()
    sut.onActivityCreated(activity, fixture.bundle)

    fixture.transaction.forceFinish(OK, false, null)
    verify(fixture.activityFramesTracker).setMetrics(activity, fixture.transaction.eventId)
  }

  @Test
  fun `When sentry is initialized mid activity lifecycle, last paused time should be used in favor of app start time`() {
    val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
    val now = SentryNanotimeDate(Date(1234), 456)

    fixture.options.tracesSampleRate = 1.0
    fixture.options.dateProvider = SentryDateProvider { now }
    sut.register(fixture.scopes, fixture.options)

    // usually done by SentryPerformanceProvider
    val startDate = SentryNanotimeDate(Date(5678), 910)
    setAppStartTime(startDate)
    AppStartMetrics.getInstance().appStartType = AppStartType.COLD

    // when activity is paused without being created
    // indicating a delayed SDK init
    val oldActivity = mock<Activity>()
    sut.onActivityPrePaused(oldActivity)
    sut.onActivityPaused(oldActivity)

    // and the next activity is created
    val newActivity = mock<Activity>()
    sut.onActivityCreated(newActivity, null)

    // then the transaction should start with the paused time
    assertEquals(now.nanoTimestamp(), fixture.transaction.startDate.nanoTimestamp())
  }

  @Test
  fun `On activity preCreated onCreate span is started`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    sut.register(fixture.scopes, fixture.options)

    val date = SentryNanotimeDate(Date(1), 0)
    setAppStartTime(date)

    assertTrue(sut.activitySpanHelpers.isEmpty())

    val activity = mock<Activity>()
    // Activity onPreCreate date will be used
    sut.onActivityPreCreated(activity, fixture.bundle)

    assertFalse(sut.activitySpanHelpers.isEmpty())
    assertNotNull(sut.activitySpanHelpers[activity]!!.onCreateStartTimestamp)
  }

  @Test
  fun `Creates activity lifecycle spans`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    val appStartDate = SentryNanotimeDate(Date(1), 0)
    val startDate = SentryNanotimeDate(Date(2), 0)
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    fixture.options.dateProvider = SentryDateProvider { startDate }
    setAppStartTime(appStartDate)

    sut.register(fixture.scopes, fixture.options)
    assertTrue(sut.activitySpanHelpers.isEmpty())

    sut.onActivityPreCreated(activity, null)

    assertFalse(sut.activitySpanHelpers.isEmpty())
    val helper = sut.activitySpanHelpers.values.first()
    assertNotNull(helper.onCreateStartTimestamp)
    assertEquals(
      startDate.nanoTimestamp(),
      sut.getProperty<SentryDate>("lastPausedTime").nanoTimestamp(),
    )

    sut.onActivityCreated(activity, null)

    sut.onActivityPostCreated(activity, null)
    assertTrue(helper.onCreateSpan!!.isFinished)

    sut.onActivityPreStarted(activity)
    assertNotNull(helper.onStartStartTimestamp)

    sut.onActivityStarted(activity)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())

    sut.onActivityPostStarted(activity)
    assertTrue(helper.onStartSpan!!.isFinished)
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `Creates activity lifecycle spans even when no app start span is available`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    val startDate = SentryNanotimeDate(Date(2), 0)
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    fixture.options.dateProvider = SentryDateProvider { startDate }
    // Don't set app start time, so there's no app start span
    // setAppStartTime(appStartDate)

    sut.register(fixture.scopes, fixture.options)
    assertTrue(sut.activitySpanHelpers.isEmpty())

    sut.onActivityPreCreated(activity, null)

    assertFalse(sut.activitySpanHelpers.isEmpty())
    val helper = sut.activitySpanHelpers.values.first()
    assertNotNull(helper.onCreateStartTimestamp)

    sut.onActivityCreated(activity, null)
    assertNull(sut.appStartSpan)

    sut.onActivityPostCreated(activity, null)
    assertTrue(helper.onCreateSpan!!.isFinished)

    sut.onActivityPreStarted(activity)
    assertNotNull(helper.onStartStartTimestamp)

    sut.onActivityStarted(activity)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())

    sut.onActivityPostStarted(activity)
    assertTrue(helper.onStartSpan!!.isFinished)
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `Save activity lifecycle spans in AppStartMetrics onPostSarted`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    setAppStartTime()

    sut.register(fixture.scopes, fixture.options)
    assertTrue(sut.activitySpanHelpers.isEmpty())

    sut.onActivityPreCreated(activity, null)
    sut.onActivityCreated(activity, null)
    sut.onActivityPostCreated(activity, null)
    sut.onActivityPreStarted(activity)
    sut.onActivityStarted(activity)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
    sut.onActivityPostStarted(activity)
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `Creates activity lifecycle spans on API lower than 29`() {
    val sut = fixture.getSut(apiVersion = Build.VERSION_CODES.P)
    fixture.options.tracesSampleRate = 1.0
    val appStartDate = SentryNanotimeDate(Date(1), 0)
    val startDate = SentryNanotimeDate(Date(2), 0)
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    fixture.options.dateProvider = SentryDateProvider { startDate }
    setAppStartTime(appStartDate)

    sut.register(fixture.scopes, fixture.options)
    assertTrue(sut.activitySpanHelpers.isEmpty())

    sut.onActivityCreated(activity, null)

    assertFalse(sut.activitySpanHelpers.isEmpty())
    val helper = sut.activitySpanHelpers.values.first()
    assertNotNull(helper.onCreateStartTimestamp)
    assertEquals(
      startDate.nanoTimestamp(),
      sut.getProperty<SentryDate>("lastPausedTime").nanoTimestamp(),
    )
    assertNotNull(sut.appStartSpan)

    sut.onActivityStarted(activity)
    assertTrue(helper.onCreateSpan!!.isFinished)
    assertNotNull(helper.onStartStartTimestamp)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())

    sut.onActivityResumed(activity)
    assertTrue(helper.onStartSpan!!.isFinished)
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `Save activity lifecycle spans in AppStartMetrics onResumed on API lower than 29`() {
    val sut = fixture.getSut(apiVersion = Build.VERSION_CODES.P)
    fixture.options.tracesSampleRate = 1.0
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    setAppStartTime()

    sut.register(fixture.scopes, fixture.options)
    assertTrue(sut.activitySpanHelpers.isEmpty())

    sut.onActivityCreated(activity, null)
    sut.onActivityStarted(activity)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
    sut.onActivityResumed(activity)
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `Does not add activity lifecycle spans when firstActivityCreated is true`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    val appStartDate = SentryNanotimeDate(Date(1), 0)
    val startDate = SentryNanotimeDate(Date(2), 0)
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    fixture.options.dateProvider = SentryDateProvider { startDate }
    setAppStartTime(appStartDate)
    sut.register(fixture.scopes, fixture.options)
    sut.setFirstActivityCreated(true)

    sut.onActivityPreCreated(activity, null)
    sut.onActivityCreated(activity, null)
    sut.onActivityPostCreated(activity, null)
    sut.onActivityPreStarted(activity)
    sut.onActivityStarted(activity)
    sut.onActivityPostStarted(activity)
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
  }

  @Test
  fun `When firstActivityCreated is false and app start span has stopped, restart app start to current date`() {
    val sut = fixture.getSut()
    fixture.options.tracesSampleRate = 1.0
    val appStartDate = SentryNanotimeDate(Date(1), 0)
    val appStartMetrics = AppStartMetrics.getInstance()
    val activity = mock<Activity>()
    setAppStartTime(appStartDate)
    // Let's pretend app start started and finished
    appStartMetrics.appStartTimeSpan.stop()
    sut.register(fixture.scopes, fixture.options)

    assertEquals(0, sut.getProperty<SentryDate>("lastPausedTime").nanoTimestamp())

    // An Activity (the first) is created after app start has finished
    sut.onActivityPreCreated(activity, null)
    // lastPausedUptimeMillis is set to current SystemClock.uptimeMillis()
    val lastUptimeMillis = sut.getProperty<SentryDate>("lastPausedTime")
    assertNotEquals(0, lastUptimeMillis.nanoTimestamp())
  }

  private fun SentryTracer.isFinishing() =
    getProperty<Any>("finishStatus").getProperty<Boolean>("isFinishing")

  private fun runFirstDraw(view: View) {
    // Removes OnDrawListener in the next OnGlobalLayout after onDraw
    view.viewTreeObserver.dispatchOnDraw()
    view.viewTreeObserver.dispatchOnGlobalLayout()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun setAppStartTime(
    date: SentryDate = SentryNanotimeDate(Date(1), 0),
    stopDate: SentryDate? = null,
  ) {
    // set by SentryPerformanceProvider so forcing it here
    AppStartMetrics.getInstance().appStartType = AppStartType.COLD
    AppStartMetrics.getInstance().isAppLaunchedInForeground = true

    val sdkAppStartTimeSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
    val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
    val millis = DateUtils.nanosToMillis(date.nanoTimestamp().toDouble()).toLong()
    val stopMillis = DateUtils.nanosToMillis(stopDate?.nanoTimestamp()?.toDouble() ?: 0.0).toLong()

    sdkAppStartTimeSpan.setStartedAt(millis)
    sdkAppStartTimeSpan.setStartUnixTimeMs(millis)
    sdkAppStartTimeSpan.setStoppedAt(stopMillis)

    appStartTimeSpan.setStartedAt(millis)
    appStartTimeSpan.setStartUnixTimeMs(millis)
    appStartTimeSpan.setStoppedAt(stopMillis)
    if (stopDate != null) {
      AppStartMetrics.getInstance().onActivityCreated(mock(), mock())
    }
  }
}
