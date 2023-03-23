package io.sentry.android.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.android.core.AnrV2Integration.AnrV2Hint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.exception.ExceptionMechanismException
import io.sentry.hints.DiskFlushNotification
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.HintUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class AnrV2IntegrationTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {
        lateinit var context: Context
        lateinit var shadowActivityManager: ShadowActivityManager
        lateinit var lastReportedAnrFile: File

        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        val logger = mock<ILogger>()

        fun getSut(
            dir: TemporaryFolder?,
            useImmediateExecutorService: Boolean = true,
            isAnrEnabled: Boolean = true,
            flushTimeoutMillis: Long = 0L,
            lastReportedAnrTimestamp: Long? = null,
            lastEventId: SentryId = SentryId()
        ): AnrV2Integration {
            options.run {
                setLogger(this@Fixture.logger)
                isDebug = true
                cacheDirPath = dir?.newFolder()?.absolutePath
                executorService =
                    if (useImmediateExecutorService) ImmediateExecutorService() else mock()
                this.isAnrEnabled = isAnrEnabled
                this.flushTimeoutMillis = flushTimeoutMillis
            }
            options.cacheDirPath?.let { cacheDir ->
                lastReportedAnrFile = File(cacheDir, AndroidEnvelopeCache.LAST_ANR_REPORT)
                lastReportedAnrFile.writeText(lastReportedAnrTimestamp.toString())
            }
            whenever(hub.captureEvent(any(), anyOrNull<Hint>())).thenReturn(lastEventId)

            return AnrV2Integration(context)
        }

        fun addAppExitInfo(
            reason: Int? = ApplicationExitInfo.REASON_ANR,
            timestamp: Long? = null,
            importance: Int? = null
        ) {
            val builder = ApplicationExitInfoBuilder.newBuilder()
            if (reason != null) {
                builder.setReason(reason)
            }
            if (timestamp != null) {
                builder.setTimestamp(timestamp)
            }
            if (importance != null) {
                builder.setImportance(importance)
            }
            shadowActivityManager.addApplicationExitInfo(builder.build())
        }
    }

    private val fixture = Fixture()
    private val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
    private val newTimestamp = oldTimestamp + TimeUnit.DAYS.toMillis(5)

    @BeforeTest
    fun `set up`() {
        fixture.context = ApplicationProvider.getApplicationContext()
        val activityManager =
            fixture.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        fixture.shadowActivityManager = Shadow.extract(activityManager)
    }

    @Test
    fun `when cacheDir is not set, does not process historical exits`() {
        val integration = fixture.getSut(null, useImmediateExecutorService = false)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.options.executorService, never()).submit(any())
    }

    @Test
    fun `when anr tracking is not enabled, does not process historical exits`() {
        val integration =
            fixture.getSut(tmpDir, isAnrEnabled = false, useImmediateExecutorService = false)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.options.executorService, never()).submit(any())
    }

    @Test
    fun `when historical exit list is empty, does not process historical exits`() {
        val integration = fixture.getSut(tmpDir)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when there are no ANRs in historical exits, does not capture events`() {
        val integration = fixture.getSut(tmpDir)
        fixture.addAppExitInfo(reason = null)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR is older than 90 days, does not capture events`() {
        val oldTimestamp = System.currentTimeMillis() -
            AnrV2Integration.NINETY_DAYS_THRESHOLD -
            TimeUnit.DAYS.toMillis(2)
        val integration = fixture.getSut(tmpDir)
        fixture.addAppExitInfo(timestamp = oldTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR has already been reported, does not capture events`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = oldTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR has not been reported, captures event with enriching`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub).captureEvent(
            check {
                assertEquals(newTimestamp, it.timestamp.time)
                assertEquals(SentryLevel.FATAL, it.level)
                assertTrue {
                    it.throwable is ApplicationNotResponding &&
                        it.throwable!!.message == "Background ANR"
                }
                assertTrue {
                    (it.throwableMechanism as ExceptionMechanismException).exceptionMechanism.type == "ANRv2"
                }
            },
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).shouldEnrich()
            }
        )
    }

    @Test
    fun `when latest ANR has foreground importance, does not add Background to the name`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(
            timestamp = newTimestamp,
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        )

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub).captureEvent(
            argThat {
                throwable is ApplicationNotResponding && throwable!!.message == "ANR"
            },
            anyOrNull<Hint>()
        )
    }

    @Test
    fun `waits for ANR events to be flushed on disk`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            flushTimeoutMillis = 3000L
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        whenever(fixture.hub.captureEvent(any(), any<Hint>())).thenAnswer { invocation ->
            val hint = HintUtils.getSentrySdkHint(invocation.getArgument(1))
                as DiskFlushNotification
            thread {
                Thread.sleep(1000L)
                hint.markFlushed()
            }
            SentryId()
        }

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub).captureEvent(any(), anyOrNull<Hint>())
        // shouldn't fall into timed out state, because we marked event as flushed on another thread
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out") },
            any<Any>()
        )
    }

    @Test
    fun `when latest ANR event was dropped, does not block flushing`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            lastEventId = SentryId.EMPTY_ID
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub).captureEvent(any(), anyOrNull<Hint>())
        // we do not call markFlushed, hence it should time out waiting for flush, but because
        // we drop the event, it should not even come to this if-check
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out") },
            any<Any>()
        )
    }

    @Test
    fun `historical ANRs are reported non-enriched`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub, times(2)).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                !(hint as AnrV2Hint).shouldEnrich()
            }
        )
    }

    @Test
    fun `historical ANRs are reported in reverse order to keep track of last reported ANR in a marker file`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        // robolectric uses addFirst when adding exit infos, so the last one here will be the first on the list
        fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(2))
        fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(1))
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.hub, fixture.options)

        // the order is reverse here, so the oldest ANR will be reported first to keep track of
        // last reported ANR in a marker file
        inOrder(fixture.hub) {
            verify(fixture.hub).captureEvent(
                argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(2) },
                anyOrNull<Hint>()
            )
            verify(fixture.hub).captureEvent(
                argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(1) },
                anyOrNull<Hint>()
            )
            verify(fixture.hub).captureEvent(
                argThat { timestamp.time == newTimestamp },
                anyOrNull<Hint>()
            )
        }
    }

    @Test
    fun `ANR timestamp is passed with the hint`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.hub, fixture.options)

        verify(fixture.hub).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).timestamp() == newTimestamp
            }
        )
    }
}
