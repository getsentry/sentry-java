package io.sentry.android.core

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryExceptionFactory
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.SpanId
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.cache.EnvelopeCache
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.test.createTestScopes
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InternalSentrySdkTest {

  private lateinit var context: Context

  class Fixture {
    val capturedEnvelopes = mutableListOf<SentryEnvelope>()
    lateinit var options: SentryOptions

    fun init(context: Context) {
      initForTest(context) { options ->
        this@Fixture.options = options
        options.dsn = "https://key@host/proj"
        options.setTransportFactory { _, _ ->
          object : ITransport {
            override fun close(isRestarting: Boolean) {
              // no-op
            }

            override fun close() {
              // no-op
            }

            override fun send(envelope: SentryEnvelope, hint: Hint) {
              capturedEnvelopes.add(envelope)
            }

            override fun flush(timeoutMillis: Long) {
              // no-op
            }

            override fun getRateLimiter(): RateLimiter? {
              return null
            }
          }
        }
      }

      capturedEnvelopes.clear()
    }

    fun captureEnvelopeWithEvent(
      event: SentryEvent = SentryEvent(),
      maybeStartNewSession: Boolean = false,
    ) {
      // create an envelope with session data
      val options = Sentry.getCurrentScopes().options
      val eventId = SentryId()
      val header = SentryEnvelopeHeader(eventId)
      val eventItem = SentryEnvelopeItem.fromEvent(options.serializer, event)

      val envelope = SentryEnvelope(header, listOf(eventItem))

      // serialize to byte array
      val outputStream = ByteArrayOutputStream()
      options.serializer.serialize(envelope, outputStream)
      val data = outputStream.toByteArray()

      InternalSentrySdk.captureEnvelope(data, maybeStartNewSession)
    }

    fun captureEnvelopeNonTerminatingWithEvent(event: SentryEvent = SentryEvent()) {
      val options = Sentry.getCurrentScopes().options
      val eventId = SentryId()
      val header = SentryEnvelopeHeader(eventId)
      val eventItem = SentryEnvelopeItem.fromEvent(options.serializer, event)

      val envelope = SentryEnvelope(header, listOf(eventItem))

      val outputStream = ByteArrayOutputStream()
      options.serializer.serialize(envelope, outputStream)
      val data = outputStream.toByteArray()

      InternalSentrySdk.captureEnvelopeNonTerminating(data)
    }

    fun createSentryEventWithUnhandledException(): SentryEvent {
      return SentryEvent(RuntimeException()).apply {
        val mechanism = Mechanism()
        mechanism.isHandled = false

        val factory = SentryExceptionFactory(mock())
        val sentryExceptions =
          factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
        exceptions = sentryExceptions
      }
    }

    fun mockFinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set
      metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(
        30
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.applicationOnCreateTimeSpan.setStoppedAt(40)

      val activityLifecycleSpan = ActivityLifecycleTimeSpan()
      activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
      activityLifecycleSpan.onCreate.setStartedAt(
        50
      ) // Can't be 0, as that's the default value if not set
      activityLifecycleSpan.onCreate.setStartUnixTimeMs(
        50
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      activityLifecycleSpan.onCreate.setStoppedAt(60)

      activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
      activityLifecycleSpan.onStart.setStartedAt(
        70
      ) // Can't be 0, as that's the default value if not set
      activityLifecycleSpan.onStart.setStartUnixTimeMs(
        70
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      activityLifecycleSpan.onStart.setStoppedAt(80)
      metrics.addActivityLifecycleTimeSpans(activityLifecycleSpan)

      AppStartMetrics.onContentProviderCreate(mock<ContentProvider>())
      metrics.contentProviderOnCreateTimeSpans[0].description = "Test Content Provider created"
      metrics.contentProviderOnCreateTimeSpans[0].setStartedAt(90)
      metrics.contentProviderOnCreateTimeSpans[0].setStartUnixTimeMs(90)
      metrics.contentProviderOnCreateTimeSpans[0].setStoppedAt(100)

      metrics.appStartProfiler = mock()
      metrics.appStartSamplingDecision = mock()
    }

    fun mockMinimumFinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set
      metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(
        30
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.applicationOnCreateTimeSpan.setStoppedAt(40)
    }

    fun mockUnfinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set

      val activityLifecycleSpan =
        ActivityLifecycleTimeSpan() // Expect the created spans are not started nor stopped
      activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
      activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
      metrics.addActivityLifecycleTimeSpans(activityLifecycleSpan)
    }
  }

  @BeforeTest
  fun `set up`() {
    Sentry.close()
    context = ApplicationProvider.getApplicationContext()
    DeviceInfoUtil.resetInstance()
  }

  @Test
  fun `current scope returns null when scopes is no-op`() {
    Sentry.setCurrentScopes(createTestScopes(enabled = false))
    val scope = InternalSentrySdk.getCurrentScope()
    assertNull(scope)
  }

  @Test
  fun `current scope returns obj when scopes is active`() {
    val fixture = Fixture()
    fixture.init(context)
    val scope = InternalSentrySdk.getCurrentScope()
    assertNotNull(scope)
  }

  @Test
  fun `current scope returns a copy of the scope`() {
    val fixture = Fixture()
    fixture.init(context)
    Sentry.addBreadcrumb("test")
    Sentry.configureScope(ScopeType.CURRENT) { scope ->
      scope.addBreadcrumb(Breadcrumb("currentBreadcrumb"))
    }
    Sentry.configureScope(ScopeType.ISOLATION) { scope ->
      scope.addBreadcrumb(Breadcrumb("isolationBreadcrumb"))
    }
    Sentry.configureScope(ScopeType.GLOBAL) { scope ->
      scope.addBreadcrumb(Breadcrumb("globalBreadcrumb"))
    }

    // when the clone is modified
    val clonedScope = InternalSentrySdk.getCurrentScope()!!
    clonedScope.clearBreadcrumbs()

    // then modifications should not be reflected
    Sentry.configureScope { scope -> assertEquals(3, scope.breadcrumbs.size) }

    // Ensure we don't interfere with other tests
    Sentry.configureScope(ScopeType.GLOBAL) { scope -> scope.clear() }
  }

  @Test
  fun `serializeScope correctly creates top level map`() {
    val options = SentryAndroidOptions()
    val scope = Scope(options)

    scope.user = User().apply { name = "John" }
    scope.addBreadcrumb(Breadcrumb.ui("ui.click", "button_login"))
    scope.contexts.setApp(App().apply { appName = "Example App" })
    scope.setTag("variant", "yellow")

    val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)

    assertTrue(serializedScope.containsKey("user"))
    assertTrue(serializedScope.containsKey("contexts"))
    assertTrue((serializedScope["contexts"] as Map<*, *>).containsKey("device"))

    assertTrue(serializedScope.containsKey("tags"))
    assertTrue(serializedScope.containsKey("extras"))
    assertTrue(serializedScope.containsKey("fingerprint"))
    assertTrue(serializedScope.containsKey("level"))
    assertTrue(serializedScope.containsKey("breadcrumbs"))
  }

  @Test
  fun `serializeScope returns empty map in case scope is null`() {
    val options = SentryAndroidOptions()
    val serializedScope = InternalSentrySdk.serializeScope(context, options, null)
    assertTrue(serializedScope.isEmpty())
  }

  @Test
  fun `serializeScope returns empty map in case scope serialization fails`() {
    val options = SentryAndroidOptions()
    val scope = mock<IScope>()

    whenever(scope.contexts).thenReturn(Contexts())
    whenever(scope.user).thenThrow(IllegalStateException("something is off"))

    val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
    assertTrue(serializedScope.isEmpty())
  }

  @Test
  fun `serializeScope provides fallback user if none is set`() {
    val options = SentryAndroidOptions()
    val scope = Scope(options)
    scope.user = null

    val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
    assertTrue((serializedScope["user"] as Map<*, *>).containsKey("id"))
  }

  @Test
  fun `serializeScope does not override user-id`() {
    val options = SentryAndroidOptions()
    val scope = Scope(options)
    scope.user = User().apply { id = "abc" }

    val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
    assertEquals("abc", (serializedScope["user"] as Map<*, *>)["id"])
  }

  @Test
  fun `serializeScope provides fallback app data if none is set`() {
    val options = SentryAndroidOptions()
    val scope = Scope(options)
    scope.setContexts("app", null as Any?)

    val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
    assertTrue(
      ((serializedScope["contexts"] as Map<*, *>)["app"] as Map<*, *>).containsKey("app_name")
    )
  }

  @Test
  fun `captureEnvelope fails if payload is invalid`() {
    assertNull(InternalSentrySdk.captureEnvelope(ByteArray(8), false))
  }

  @Test
  fun `captureEnvelope correctly captures the original envelope data`() {
    val fixture = Fixture()
    fixture.init(context)

    // when capture envelope is called
    fixture.captureEnvelopeWithEvent()

    // then one envelope should be captured
    val capturedEnvelopes = fixture.capturedEnvelopes
    assertEquals(1, capturedEnvelopes.size)

    val capturedEnvelope = capturedEnvelopes.first()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // and it should contain the original event / attachment
    assertEquals(1, capturedEnvelopeItems.size)
    assertEquals(SentryItemType.Event, capturedEnvelopeItems[0].header.type)
  }

  @Test
  fun `captureEnvelope correctly enriches the envelope with session data and does not start new session`() {
    val fixture = Fixture()
    fixture.init(context)

    // keep reference for current session for later assertions
    // we need to get the reference now as it will be removed from the scope
    val sessionRef = AtomicReference<Session>()
    Sentry.configureScope { scope -> sessionRef.set(scope.session) }

    // when capture envelope is called with an crashed event
    fixture.captureEnvelopeWithEvent(fixture.createSentryEventWithUnhandledException())

    val capturedEnvelope = fixture.capturedEnvelopes.first()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // then it should contain the original event + session
    assertEquals(2, capturedEnvelopeItems.size)
    assertEquals(SentryItemType.Event, capturedEnvelopeItems[0].header.type)
    assertEquals(SentryItemType.Session, capturedEnvelopeItems[1].header.type)

    // and then the sent session should be marked as crashed
    val capturedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(capturedEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Crashed, capturedSession.status)

    assertEquals(Session.State.Crashed, sessionRef.get().status)
    assertEquals(capturedSession.sessionId, sessionRef.get().sessionId)
  }

  @Test
  fun `captureEnvelope starts new session when enabled`() {
    val fixture = Fixture()
    fixture.init(context)

    // when capture envelope is called with an crashed event
    fixture.captureEnvelopeWithEvent(fixture.createSentryEventWithUnhandledException(), true)

    val scopeRef = AtomicReference<IScope>()
    Sentry.configureScope { scope -> scopeRef.set(scope) }

    // first envelope is the new session start
    val capturedStartSessionEnvelope = fixture.capturedEnvelopes.first()
    val capturedNewSessionStart =
      fixture.options.serializer.deserialize(
        InputStreamReader(
          ByteArrayInputStream(capturedStartSessionEnvelope.items.toList()[0].data)
        ),
        Session::class.java,
      )!!
    assertEquals(capturedNewSessionStart.sessionId, scopeRef.get().session!!.sessionId)
    assertEquals(Session.State.Ok, capturedNewSessionStart.status)

    val capturedEnvelope = fixture.capturedEnvelopes.last()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // there should be two envelopes session start and captured crash
    assertEquals(2, fixture.capturedEnvelopes.size)

    // then it should contain the original event + session
    assertEquals(2, capturedEnvelopeItems.size)
    assertEquals(SentryItemType.Event, capturedEnvelopeItems[0].header.type)
    assertEquals(SentryItemType.Session, capturedEnvelopeItems[1].header.type)

    // and then the sent session should be marked as crashed
    val capturedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(capturedEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Crashed, capturedSession.status)

    // and the local session should be a new session
    assertNotEquals(capturedSession.sessionId, scopeRef.get().session!!.sessionId)
  }

  @Test
  fun `captureEnvelopeNonTerminating keeps the session Ok and marks it pending unhandled`() {
    val fixture = Fixture()
    fixture.init(context)

    val originalSid = AtomicReference<String>()
    Sentry.configureScope { scope -> originalSid.set(scope.session!!.sessionId) }

    // when capture envelope is called with an unhandled event through the non-terminating API
    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createSentryEventWithUnhandledException()
    )

    // then only the original event envelope is captured, without a session item
    assertEquals(1, fixture.capturedEnvelopes.size)
    val capturedEnvelopeItems = fixture.capturedEnvelopes.first().items.toList()
    assertEquals(1, capturedEnvelopeItems.size)
    assertEquals(SentryItemType.Event, capturedEnvelopeItems[0].header.type)

    // and the session stays alive on the scope, same id, marked pending unhandled
    val scopeSession = AtomicReference<Session>()
    Sentry.configureScope { scope -> scopeSession.set(scope.session) }
    assertEquals(Session.State.Ok, scopeSession.get().status)
    assertTrue(scopeSession.get().isPendingUnhandled)
    assertEquals(originalSid.get(), scopeSession.get().sessionId)

    // and it is persisted so pending survives process death
    val sessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val persistedSession =
      fixture.options.serializer.deserialize(sessionFile.reader(), Session::class.java)!!
    assertEquals(Session.State.Ok, persistedSession.status)
    assertTrue(persistedSession.isPendingUnhandled)
    assertEquals(originalSid.get(), persistedSession.sessionId)
  }

  @Test
  fun `captureEnvelopeNonTerminating then endSession finalizes the session as unhandled`() {
    val fixture = Fixture()
    fixture.init(context)

    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createSentryEventWithUnhandledException()
    )
    fixture.capturedEnvelopes.clear()

    // when the session is ended by normal lifecycle
    Sentry.endSession()

    // then the ended session is captured as unhandled
    val sessionItems =
      fixture.capturedEnvelopes
        .flatMap { it.items.toList() }
        .filter {
          it.header.type == SentryItemType.Session
        }
    assertEquals(1, sessionItems.size)
    val endedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(sessionItems[0].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Unhandled, endedSession.status)
  }

  @Test
  fun `captureEnvelopeNonTerminating then a crash finalizes old session and starts a new one`() {
    val fixture = Fixture()
    fixture.init(context)

    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createSentryEventWithUnhandledException()
    )
    val pendingSession = AtomicReference<Session>()
    Sentry.configureScope { scope -> pendingSession.set(scope.session) }
    val oldSid = pendingSession.get().sessionId
    assertTrue(pendingSession.get().isPendingUnhandled)
    fixture.capturedEnvelopes.clear()

    // when a subsequent hard crash is captured through the existing terminating API
    fixture.captureEnvelopeWithEvent(fixture.createSentryEventWithUnhandledException(), true)

    // then the crash envelope contains the finalized old session
    assertEquals(2, fixture.capturedEnvelopes.size)
    val crashEnvelopeItems = fixture.capturedEnvelopes.last().items.toList()
    assertEquals(2, crashEnvelopeItems.size)
    assertEquals(SentryItemType.Event, crashEnvelopeItems[0].header.type)
    assertEquals(SentryItemType.Session, crashEnvelopeItems[1].header.type)
    val crashedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(crashEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Crashed, crashedSession.status)
    assertFalse(crashedSession.isPendingUnhandled)
    assertEquals(oldSid, crashedSession.sessionId)

    // and a new Ok session with a different id is active
    val activeSession = AtomicReference<Session>()
    Sentry.configureScope { scope -> activeSession.set(scope.session) }
    assertEquals(Session.State.Ok, activeSession.get().status)
    assertFalse(activeSession.get().isPendingUnhandled)
    assertNotEquals(oldSid, activeSession.get().sessionId)
  }

  @Test
  fun `getAppStartMeasurement returns correct serialized data from the app start instance`() {
    Fixture().mockFinishedAppStart()

    val serializedAppStart = InternalSentrySdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(5, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])

    val actualAppSpan = actualSpans[1] as Map<*, *>
    assertEquals("Application created", actualAppSpan["description"])
    assertEquals(30.toLong(), actualAppSpan["start_timestamp_ms"])
    assertEquals(40.toLong(), actualAppSpan["end_timestamp_ms"])

    val actualContentProviderSpan = actualSpans[2] as Map<*, *>
    assertEquals("Test Content Provider created", actualContentProviderSpan["description"])
    assertEquals(90.toLong(), actualContentProviderSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualContentProviderSpan["end_timestamp_ms"])

    val actualActivityOnCreateSpan = actualSpans[3] as Map<*, *>
    assertEquals("Test Activity Lifecycle onCreate", actualActivityOnCreateSpan["description"])
    assertEquals(50.toLong(), actualActivityOnCreateSpan["start_timestamp_ms"])
    assertEquals(60.toLong(), actualActivityOnCreateSpan["end_timestamp_ms"])

    val actualActivityOnStartSpan = actualSpans[4] as Map<*, *>
    assertEquals("Test Activity Lifecycle onStart", actualActivityOnStartSpan["description"])
    assertEquals(70.toLong(), actualActivityOnStartSpan["start_timestamp_ms"])
    assertEquals(80.toLong(), actualActivityOnStartSpan["end_timestamp_ms"])
  }

  @Test
  fun `getAppStartMeasurement returns correct serialized data from the minimum app start instance`() {
    Fixture().mockMinimumFinishedAppStart()

    val serializedAppStart = InternalSentrySdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(2, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])

    val actualAppSpan = actualSpans[1] as Map<*, *>
    assertEquals("Application created", actualAppSpan["description"])
    assertEquals(30.toLong(), actualAppSpan["start_timestamp_ms"])
    assertEquals(40.toLong(), actualAppSpan["end_timestamp_ms"])
  }

  @Test
  fun `getAppStartMeasurement returns only stopped spans in serialized data`() {
    Fixture().mockUnfinishedAppStart()

    val serializedAppStart = InternalSentrySdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(1, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])
  }

  @Test
  fun `setTrace sets correct propagation context`() {
    val fixture = Fixture()
    fixture.init(context)

    val traceId = "771a43a4192642f0b136d5159a501700"
    val spanId = "771a43a4192642f0"
    val sampleRate = 0.5
    val sampleRand = 0.3

    InternalSentrySdk.setTrace(traceId, spanId, sampleRate, sampleRand)

    Sentry.configureScope { scope ->
      val propagationContext = scope.propagationContext
      assertEquals(SentryId(traceId), propagationContext.traceId)
      assertEquals(SpanId(spanId), propagationContext.parentSpanId)
      assertEquals(sampleRate, propagationContext.baggage.sampleRate!!, 0.0001)
      assertEquals(sampleRand, propagationContext.baggage.sampleRand!!, 0.0001)
    }
  }
}
