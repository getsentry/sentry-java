package io.sentry.android.core

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Hub
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryExceptionFactory
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class InternalSentrySdkTest {

    private lateinit var context: Context

    class Fixture {
        val capturedEnvelopes = mutableListOf<SentryEnvelope>()
        lateinit var options: SentryOptions

        fun init(context: Context) {
            SentryAndroid.init(context) { options ->
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

        fun captureEnvelopeWithEvent(event: SentryEvent = SentryEvent()) {
            // create an envelope with session data
            val options = Sentry.getCurrentHub().options
            val eventId = SentryId()
            val header = SentryEnvelopeHeader(eventId)
            val eventItem = SentryEnvelopeItem.fromEvent(options.serializer, event)

            val envelope = SentryEnvelope(
                header,
                listOf(
                    eventItem
                )
            )

            // serialize to byte array
            val outputStream = ByteArrayOutputStream()
            options.serializer.serialize(envelope, outputStream)
            val data = outputStream.toByteArray()

            InternalSentrySdk.captureEnvelope(data)
        }

        fun mockFinishedAppStart() {
            val metrics = AppStartMetrics.getInstance()

            metrics.appStartType = AppStartMetrics.AppStartType.WARM

            metrics.appStartTimeSpan.setStartedAt(20) // Can't be 0, as that's the default value if not set
            metrics.appStartTimeSpan.setStartUnixTimeMs(20) // The order matters, unix time must be set after started at in tests to avoid overwrite
            metrics.appStartTimeSpan.setStoppedAt(200)
            metrics.classLoadedUptimeMs = 100

            AppStartMetrics.onApplicationCreate(mock<Application>())
            metrics.applicationOnCreateTimeSpan.description = "Application created"
            metrics.applicationOnCreateTimeSpan.setStartedAt(30) // Can't be 0, as that's the default value if not set
            metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(30) // The order matters, unix time must be set after started at in tests to avoid overwrite
            metrics.applicationOnCreateTimeSpan.setStoppedAt(40)

            val activityLifecycleSpan = ActivityLifecycleTimeSpan()
            activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
            activityLifecycleSpan.onCreate.setStartedAt(50) // Can't be 0, as that's the default value if not set
            activityLifecycleSpan.onCreate.setStartUnixTimeMs(50) // The order matters, unix time must be set after started at in tests to avoid overwrite
            activityLifecycleSpan.onCreate.setStoppedAt(60)

            activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
            activityLifecycleSpan.onStart.setStartedAt(70) // Can't be 0, as that's the default value if not set
            activityLifecycleSpan.onStart.setStartUnixTimeMs(70) // The order matters, unix time must be set after started at in tests to avoid overwrite
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

            metrics.appStartTimeSpan.setStartedAt(20) // Can't be 0, as that's the default value if not set
            metrics.appStartTimeSpan.setStartUnixTimeMs(20) // The order matters, unix time must be set after started at in tests to avoid overwrite
            metrics.appStartTimeSpan.setStoppedAt(200)
            metrics.classLoadedUptimeMs = 100

            AppStartMetrics.onApplicationCreate(mock<Application>())
            metrics.applicationOnCreateTimeSpan.description = "Application created"
            metrics.applicationOnCreateTimeSpan.setStartedAt(30) // Can't be 0, as that's the default value if not set
            metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(30) // The order matters, unix time must be set after started at in tests to avoid overwrite
            metrics.applicationOnCreateTimeSpan.setStoppedAt(40)
        }

        fun mockUnfinishedAppStart() {
            val metrics = AppStartMetrics.getInstance()

            metrics.appStartType = AppStartMetrics.AppStartType.WARM

            metrics.appStartTimeSpan.setStartedAt(20) // Can't be 0, as that's the default value if not set
            metrics.appStartTimeSpan.setStartUnixTimeMs(20) // The order matters, unix time must be set after started at in tests to avoid overwrite
            metrics.appStartTimeSpan.setStoppedAt(200)
            metrics.classLoadedUptimeMs = 100

            AppStartMetrics.onApplicationCreate(mock<Application>())
            metrics.applicationOnCreateTimeSpan.description = "Application created"
            metrics.applicationOnCreateTimeSpan.setStartedAt(30) // Can't be 0, as that's the default value if not set

            val activityLifecycleSpan = ActivityLifecycleTimeSpan() // Expect the created spans are not started nor stopped
            activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
            activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
            metrics.addActivityLifecycleTimeSpans(activityLifecycleSpan)
        }
    }

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        DeviceInfoUtil.resetInstance()
    }

    @Test
    fun `current scope returns null when hub is no-op`() {
        Sentry.getCurrentHub().close()
        val scope = InternalSentrySdk.getCurrentScope()
        assertNull(scope)
    }

    @Test
    fun `current scope returns obj when hub is active`() {
        Sentry.setCurrentHub(
            Hub(
                SentryOptions().apply {
                    dsn = "https://key@uri/1234567"
                }
            )
        )
        val scope = InternalSentrySdk.getCurrentScope()
        assertNotNull(scope)
    }

    @Test
    fun `current scope returns a copy of the scope`() {
        Sentry.setCurrentHub(
            Hub(
                SentryOptions().apply {
                    dsn = "https://key@uri/1234567"
                }
            )
        )
        Sentry.addBreadcrumb("test")

        // when the clone is modified
        val clonedScope = InternalSentrySdk.getCurrentScope()!!
        clonedScope.clearBreadcrumbs()

        // then modifications should not be reflected
        Sentry.configureScope { scope ->
            assertEquals(1, scope.breadcrumbs.size)
        }
    }

    @Test
    fun `serializeScope correctly creates top level map`() {
        val options = SentryAndroidOptions()
        val scope = Scope(options)

        scope.user = User().apply {
            name = "John"
        }
        scope.addBreadcrumb(Breadcrumb.ui("ui.click", "button_login"))
        scope.contexts.setApp(
            App().apply {
                appName = "Example App"
            }
        )
        scope.setTag("variant", "yellow")

        val serializedScope = InternalSentrySdk.serializeScope(
            context,
            options,
            scope
        )

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
        scope.setContexts("app", null)

        val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
        assertTrue(((serializedScope["contexts"] as Map<*, *>)["app"] as Map<*, *>).containsKey("app_name"))
    }

    @Test
    fun `captureEnvelope fails if payload is invalid`() {
        assertNull(InternalSentrySdk.captureEnvelope(ByteArray(8)))
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
    fun `captureEnvelope correctly enriches the envelope with session data`() {
        val fixture = Fixture()
        fixture.init(context)

        // when capture envelope is called with an crashed event
        fixture.captureEnvelopeWithEvent(
            SentryEvent(RuntimeException()).apply {
                val mechanism = Mechanism()
                mechanism.isHandled = false

                val factory = SentryExceptionFactory(mock())
                val sentryExceptions = factory.getSentryExceptions(
                    ExceptionMechanismException(
                        mechanism,
                        Throwable(),
                        Thread()
                    )
                )
                exceptions = sentryExceptions
            }
        )

        val capturedEnvelope = fixture.capturedEnvelopes.first()
        val capturedEnvelopeItems = capturedEnvelope.items.toList()

        // then it should contain the original event + session
        assertEquals(2, capturedEnvelopeItems.size)
        assertEquals(SentryItemType.Event, capturedEnvelopeItems[0].header.type)
        assertEquals(SentryItemType.Session, capturedEnvelopeItems[1].header.type)

        // and then the sent session should be marked as crashed
        val capturedSession = fixture.options.serializer.deserialize(
            InputStreamReader(ByteArrayInputStream(capturedEnvelopeItems[1].data)),
            Session::class.java
        )!!
        assertEquals(Session.State.Crashed, capturedSession.status)

        // and the local session should be marked as crashed too
        val scopeRef = AtomicReference<IScope>()
        Sentry.configureScope { scope ->
            scopeRef.set(scope)
        }
        assertEquals(Session.State.Crashed, scopeRef.get().session!!.status)
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
}
