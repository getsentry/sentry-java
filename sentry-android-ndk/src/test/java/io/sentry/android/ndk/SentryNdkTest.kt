package io.sentry.android.ndk

import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NdkOptions
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("UnstableApiUsage")
class SentryNdkTest {
    class Fixture {
        var capturedOptions: NdkOptions? = null

        fun getSut(
            options: SentryAndroidOptions =
                SentryAndroidOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    cacheDirPath = "/cache"
                },
            closure: () -> Unit,
        ) {
            Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { utils ->
                utils
                    .`when`<Any> {
                        io.sentry.ndk.SentryNdk
                            .init(any<NdkOptions>())
                    }.doAnswer {
                        capturedOptions = it.arguments[0] as NdkOptions
                    }
                SentryNdk.init(options)
                closure.invoke()
            }
        }
    }

    val fixture = Fixture()

    @Test
    fun `SentryNdk calls NDK init`() {
        fixture.getSut {
            assertNotNull(fixture.capturedOptions)
        }
    }

    @Test
    fun `SentryNdk propagates null tracesSampleRate`() {
        fixture.getSut(
            options =
                SentryAndroidOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    cacheDirPath = "/cache"
                    tracesSampleRate = null
                },
        ) {
            assertNotNull(fixture.capturedOptions)
            assertEquals(0.0f, fixture.capturedOptions!!.tracesSampleRate, 0.0001f)
        }
    }

    @Test
    fun `SentryNdk propagates non-null tracesSampleRate`() {
        fixture.getSut(
            options =
                SentryAndroidOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    cacheDirPath = "/cache"
                    tracesSampleRate = 0.75
                },
        ) {
            assertNotNull(fixture.capturedOptions)
            assertEquals(0.75f, fixture.capturedOptions!!.tracesSampleRate, 0.0001f)
        }
    }
}
