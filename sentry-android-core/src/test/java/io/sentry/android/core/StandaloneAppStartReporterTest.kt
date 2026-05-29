package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import io.sentry.SentryTracer
import io.sentry.SpanContext
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.android.core.StandaloneAppStartReporter.APP_START_SCREEN_DATA
import io.sentry.android.core.StandaloneAppStartReporter.STANDALONE_APP_START_OP
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.protocol.SentryId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class StandaloneAppStartReporterTest {

  private val traceOrigin = "auto.ui.activity"

  private val scopes = mock<IScopes>()
  private val firstUiLoadCoordinator = FirstUiLoadCoordinator()
  private val createdTransactions = mutableListOf<SentryTracer>()
  private val capturedContexts = argumentCaptor<TransactionContext>()
  private val capturedOptions = argumentCaptor<TransactionOptions>()

  private fun getSut(): StandaloneAppStartReporter {
    whenever(scopes.options)
      .thenReturn(SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" })
    whenever(scopes.startTransaction(capturedContexts.capture(), capturedOptions.capture()))
      .thenAnswer {
        val t = SentryTracer(capturedContexts.lastValue, scopes, capturedOptions.lastValue)
        createdTransactions.add(t)
        return@thenAnswer t
      }
    return StandaloneAppStartReporter(scopes, traceOrigin, firstUiLoadCoordinator)
  }

  @BeforeTest
  fun setup() {
    AppStartMetrics.getInstance().clear()
  }

  @AfterTest
  fun teardown() {
    AppStartMetrics.getInstance().clear()
  }

  // region classification: the single source of truth for "what kind of app start is this"

  @Test
  fun `isStandaloneAppStart is true only for the app start operation`() {
    assertTrue(
      StandaloneAppStartReporter.isStandaloneAppStart(SpanContext(STANDALONE_APP_START_OP))
    )
    assertFalse(StandaloneAppStartReporter.isStandaloneAppStart(SpanContext("ui.load")))
    assertFalse(StandaloneAppStartReporter.isStandaloneAppStart(null))
  }

  @Test
  fun `isHeadlessAppStart is true for a standalone app start without the screen marker`() {
    assertTrue(StandaloneAppStartReporter.isHeadlessAppStart(SpanContext(STANDALONE_APP_START_OP)))
  }

  @Test
  fun `isHeadlessAppStart is false for an activity app start carrying the screen marker`() {
    val context = SpanContext(STANDALONE_APP_START_OP)
    context.setData(APP_START_SCREEN_DATA, "MainActivity")
    assertFalse(StandaloneAppStartReporter.isHeadlessAppStart(context))
  }

  @Test
  fun `isHeadlessAppStart is false for non standalone transactions`() {
    assertFalse(StandaloneAppStartReporter.isHeadlessAppStart(SpanContext("ui.load")))
    assertFalse(StandaloneAppStartReporter.isHeadlessAppStart(null))
  }

  // endregion

  // region activity-launch path

  @Test
  fun `onFirstUiLoadTransactionStarted emits an unbound App Start transaction sharing the ui load trace id`() {
    val sut = getSut()
    val traceId = SentryId()
    val appStartTime = AndroidDateUtils.getCurrentSentryDateTime()
    val uiLoadTransaction =
      SentryTracer(
        TransactionContext(
          traceId,
          "MainActivity",
          io.sentry.protocol.TransactionNameSource.COMPONENT,
          ActivityLifecycleIntegration.UI_LOAD_OP,
          null,
        ),
        scopes,
        TransactionOptions(),
      )

    val plan = sut.planFirstUiLoad("MainActivity", null, true)
    sut.onFirstUiLoadTransactionStarted(uiLoadTransaction, plan, appStartTime, "MainActivity", null)

    val transaction = createdTransactions.single()
    val context = capturedContexts.allValues.single()
    val options = capturedOptions.allValues.single()
    assertEquals(STANDALONE_APP_START_OP, context.operation)
    assertEquals("App Start", context.name)
    assertEquals(traceId, context.traceId)
    assertFalse(options.isBindToScope)
    assertEquals(appStartTime, options.startTimestamp)
    assertEquals(traceOrigin, options.origin)
    assertEquals("MainActivity", transaction.getData(APP_START_SCREEN_DATA))
    assertSame(transaction, sut.getAppStartTransaction())
  }

  @Test
  fun `onFirstFrameDrawn finishes the activity transaction at the given date`() {
    val sut = getSut()
    val appStartTime = AndroidDateUtils.getCurrentSentryDateTime()
    val traceId = SentryId()
    val uiLoadTransaction =
      SentryTracer(
        TransactionContext(
          traceId,
          "MainActivity",
          io.sentry.protocol.TransactionNameSource.COMPONENT,
          ActivityLifecycleIntegration.UI_LOAD_OP,
          null,
        ),
        scopes,
        TransactionOptions(),
      )
    val plan = sut.planFirstUiLoad("MainActivity", null, true)
    sut.onFirstUiLoadTransactionStarted(uiLoadTransaction, plan, appStartTime, "MainActivity", null)

    val endDate = AndroidDateUtils.getCurrentSentryDateTime()
    sut.onFirstFrameDrawn(endDate)

    val transaction = createdTransactions.single()
    assertTrue(transaction.isFinished)
    assertEquals(SpanStatus.OK, transaction.status)
  }

  @Test
  fun `onActivityDestroyed cancels a running app start transaction and clears it`() {
    val sut = getSut()
    val uiLoadTransaction =
      SentryTracer(
        TransactionContext(
          SentryId(),
          "MainActivity",
          io.sentry.protocol.TransactionNameSource.COMPONENT,
          ActivityLifecycleIntegration.UI_LOAD_OP,
          null,
        ),
        scopes,
        TransactionOptions(),
      )
    val plan = sut.planFirstUiLoad("MainActivity", null, true)
    sut.onFirstUiLoadTransactionStarted(
      uiLoadTransaction,
      plan,
      AndroidDateUtils.getCurrentSentryDateTime(),
      "MainActivity",
      null,
    )

    sut.onActivityDestroyed()

    val transaction = createdTransactions.single()
    assertTrue(transaction.isFinished)
    assertEquals(SpanStatus.CANCELLED, transaction.status)
    assertNull(sut.getAppStartTransaction())
  }

  @Test
  fun `planFirstUiLoad reuses headless trace id and skips sibling app start`() {
    val sut = getSut()
    val storedTraceId = SentryId()
    sut.setReusableTraceId(storedTraceId)

    val plan = sut.planFirstUiLoad("MainActivity", null, true)

    assertEquals(storedTraceId, plan.transactionContext.traceId)
    assertEquals(ActivityLifecycleIntegration.UI_LOAD_OP, plan.transactionContext.operation)
    assertNull(sut.getReusableTraceId())
    val uiLoadTransaction = SentryTracer(plan.transactionContext, scopes, TransactionOptions())
    sut.onFirstUiLoadTransactionStarted(
      uiLoadTransaction,
      plan,
      AndroidDateUtils.getCurrentSentryDateTime(),
      "MainActivity",
      null,
    )
    verify(scopes, never()).startTransaction(any(), any<TransactionOptions>())
  }

  @Test
  fun `planFirstUiLoad for later activities does not consume reusable trace id`() {
    val sut = getSut()
    val storedTraceId = SentryId()
    sut.setReusableTraceId(storedTraceId)

    val plan = sut.planFirstUiLoad("SecondActivity", null, false)

    assertEquals(storedTraceId, sut.getReusableTraceId())
    assertEquals(ActivityLifecycleIntegration.UI_LOAD_OP, plan.transactionContext.operation)
    assertFalse(plan.shouldEmitSiblingAppStart())
  }

  // endregion

  // region headless path

  @Test
  fun `onMainIdleNoActivity emits a finished App Start transaction and stashes its trace id`() {
    val sut = getSut()
    AppStartMetrics.getInstance().appStartTimeSpan.apply {
      setStartedAt(100)
      setStartUnixTimeMs(100)
      setStoppedAt(200)
    }

    sut.onMainIdleNoActivity()

    val transaction = createdTransactions.single()
    val context = capturedContexts.allValues.single()
    assertEquals(STANDALONE_APP_START_OP, context.operation)
    assertEquals(context.traceId, transaction.spanContext.traceId)
    assertTrue(transaction.isFinished)
    assertEquals(SpanStatus.OK, transaction.status)
    assertEquals(transaction.spanContext.traceId, sut.getReusableTraceId())
    // headless starts emit no parent transaction for later lifecycle spans
    assertNull(sut.getAppStartTransaction())
  }

  @Test
  fun `onMainIdleNoActivity does nothing when the app start time span is incomplete`() {
    val sut = getSut()
    AppStartMetrics.getInstance().appStartTimeSpan.reset()
    AppStartMetrics.getInstance().sdkInitTimeSpan.reset()

    sut.onMainIdleNoActivity()

    verify(scopes, never()).startTransaction(any(), any<TransactionOptions>())
    assertNull(sut.getReusableTraceId())
  }

  // endregion

  // region listener wiring

  @Test
  fun `register installs lifecycle and idle callbacks and close removes them`() {
    val sut = getSut()

    sut.register()
    assertNotNull(mainIdleNoActivityCallback())
    assertSame(sut, firstUiLoadCoordinator.getListener())

    sut.close()
    assertNull(mainIdleNoActivityCallback())
    assertNull(firstUiLoadCoordinator.getListener())
  }

  private fun mainIdleNoActivityCallback(): AppStartMetrics.OnMainIdleNoActivityCallback? {
    val field =
      AppStartMetrics::class.java.getDeclaredField("onMainIdleNoActivityCallback").apply {
        isAccessible = true
      }
    return field.get(AppStartMetrics.getInstance()) as AppStartMetrics.OnMainIdleNoActivityCallback?
  }

  // endregion
}
