package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.App
import io.sentry.protocol.Browser
import io.sentry.protocol.DebugImage
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.Device
import io.sentry.protocol.Gpu
import io.sentry.protocol.Mechanism
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.Request
import io.sentry.protocol.SdkInfo
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryPackage
import io.sentry.protocol.SentryRuntime
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.SentryThread
import io.sentry.protocol.User
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test serialization/deserialization for all classes implementing JsonUnknown
 */
@RunWith(Parameterized::class)
class JsonUnknownSerializationTest(
    private val jsonUnknown: JsonUnknown,
    private val jsonSerializable: JsonSerializable,
    private val deserializer: (JsonObjectReader, ILogger) -> JsonUnknown
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            val app = givenJsonUnknown(App())
            val breadcrumb = givenJsonUnknown(Breadcrumb())
            val browser = givenJsonUnknown(Browser())
            val debugImage = givenJsonUnknown(DebugImage())
            val debugMeta = givenJsonUnknown(DebugMeta())
            val device = givenJsonUnknown(Device())
            val gpu = givenJsonUnknown(Gpu())
            val mechanism = givenJsonUnknown(Mechanism())
            val operatingSystem = givenJsonUnknown(OperatingSystem())
            val request = givenJsonUnknown(Request())
            val sdkInfo = givenJsonUnknown(SdkInfo())
            val sentryException = givenJsonUnknown(SentryException())
            val sentryPackage = givenJsonUnknown(SentryPackage("b59a1949-9950-4203-b394-ddd8d02c9633", "3d7790f3-7f32-43f7-b82f-9f5bc85205a8"))
            val sentryRuntime = givenJsonUnknown(SentryRuntime())
            val sentryStackFrame = givenJsonUnknown(SentryStackFrame())
            val sentryStackTrace = givenJsonUnknown(SentryStackTrace())
            val sentryThread = givenJsonUnknown(SentryThread())
            val skdVersion = givenJsonUnknown(SdkVersion("3e934135-3f2b-49bc-8756-9f025b55143e", "3e31738e-4106-42d0-8be2-4a3a1bc648d3"))
            val spanContext = givenJsonUnknown(SpanContext("c2fb8fee2e2b49758bcb67cda0f713c7"))
            val user = givenJsonUnknown(User())
            val userFeedback = givenJsonUnknown(UserFeedback(SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")))

            // Same instance for first and second param, so we cann access both JsonUnknown and
            // JsonSerializable in the test method. Third param is the method reference, so we
            // don't have to deal with generics.
            return listOf(
                arrayOf(app, app, App.Deserializer()::deserialize),
                arrayOf(breadcrumb, breadcrumb, Breadcrumb.Deserializer()::deserialize),
                arrayOf(browser, browser, Browser.Deserializer()::deserialize),
                arrayOf(debugImage, debugImage, DebugImage.Deserializer()::deserialize),
                arrayOf(debugMeta, debugMeta, DebugMeta.Deserializer()::deserialize),
                arrayOf(device, device, Device.Deserializer()::deserialize),
                arrayOf(gpu, gpu, Gpu.Deserializer()::deserialize),
                arrayOf(mechanism, mechanism, Mechanism.Deserializer()::deserialize),
                arrayOf(operatingSystem, operatingSystem, OperatingSystem.Deserializer()::deserialize),
                arrayOf(request, user, Request.Deserializer()::deserialize),
                arrayOf(sdkInfo, sdkInfo, SdkInfo.Deserializer()::deserialize),
                arrayOf(sentryException, sentryException, SentryException.Deserializer()::deserialize),
                arrayOf(sentryPackage, sentryPackage, SentryPackage.Deserializer()::deserialize),
                arrayOf(sentryRuntime, sentryRuntime, SentryRuntime.Deserializer()::deserialize),
                arrayOf(sentryStackFrame, sentryStackFrame, SentryStackFrame.Deserializer()::deserialize),
                arrayOf(sentryStackTrace, sentryStackTrace, SentryStackTrace.Deserializer()::deserialize),
                arrayOf(sentryThread, sentryThread, SentryThread.Deserializer()::deserialize),
                arrayOf(skdVersion, skdVersion, SdkVersion.Deserializer()::deserialize),
                arrayOf(spanContext, spanContext, SpanContext.Deserializer()::deserialize),
                arrayOf(user, user, User.Deserializer()::deserialize),
                arrayOf(userFeedback, userFeedback, UserFeedback.Deserializer()::deserialize)
            )
        }

        private fun <T : JsonUnknown> givenJsonUnknown(jsonUnknown: T): T {
            return jsonUnknown.apply {
                unknown = mapOf(
                    "fixture-key" to "fixture-value"
                )
            }
        }
    }

    @Test
    fun `serializing and deserialize app`() {
        val sut = jsonSerializable

        val serialized = serialize(sut)
        val reader = JsonObjectReader(StringReader(serialized))
        val logger = mock<ILogger>()
        val deserialized = deserializer(reader, logger)

        assertEquals(jsonUnknown.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for app`() {
        val writer: JsonObjectWriter = mock()
        whenever(writer.name(any())).thenReturn(writer)

        val logger: ILogger = mock()
        val sut = jsonSerializable

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // Helper

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt)
        val logger = mock<ILogger>()
        jsonSerializable.serialize(jsonWrt, logger)
        return wrt.toString()
    }
}
