package io.sentry.android.core.cache

import io.sentry.SentryEnvelope
import io.sentry.android.core.AppStartState
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.cache.EnvelopeCache
import io.sentry.hints.DiskFlushNotification
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.HintUtils
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidEnvelopeCacheTest {
    private class Fixture {
        private val dir: Path = Files.createTempDirectory("sentry-cache")
        val envelope = mock<SentryEnvelope> {
            whenever(it.header).thenReturn(mock())
        }
        val options = SentryAndroidOptions()
        val dateProvider = mock<ICurrentDateProvider>()
        lateinit var markerFile: File

        fun getSut(
            appStartMillis: Long? = null,
            currentTimeMillis: Long? = null
        ): AndroidEnvelopeCache {
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath
            val outboxDir = File(options.outboxPath!!)
            outboxDir.mkdirs()

            markerFile = File(outboxDir, EnvelopeCache.STARTUP_CRASH_MARKER_FILE)

            if (appStartMillis != null) {
                AppStartState.getInstance().setAppStartMillis(appStartMillis)
            }
            if (currentTimeMillis != null) {
                whenever(dateProvider.currentTimeMillis).thenReturn(currentTimeMillis)
            }

            return AndroidEnvelopeCache(options, dateProvider)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        AppStartState.getInstance().reset()
    }

    @Test
    fun `when no flush hint exists, does not write startup crash file`() {
        val cache = fixture.getSut()

        cache.store(fixture.envelope)

        assertFalse(fixture.markerFile.exists())
    }

    @Test
    fun `when startup time is null, does not write startup crash file`() {
        val cache = fixture.getSut()

        val hints = HintUtils.createWithTypeCheckHint(DiskFlushHint())
        cache.store(fixture.envelope, hints)

        assertFalse(fixture.markerFile.exists())
    }

    @Test
    fun `when time since sdk init is more than duration threshold, does not write startup crash file`() {
        val cache = fixture.getSut(appStartMillis = 1000L, currentTimeMillis = 5000L)

        val hints = HintUtils.createWithTypeCheckHint(DiskFlushHint())
        cache.store(fixture.envelope, hints)

        assertFalse(fixture.markerFile.exists())
    }

    @Test
    fun `when outbox dir is not set, does not write startup crash file`() {
        val cache = fixture.getSut(appStartMillis = 1000L, currentTimeMillis = 2000L)

        fixture.options.cacheDirPath = null

        val hints = HintUtils.createWithTypeCheckHint(DiskFlushHint())
        cache.store(fixture.envelope, hints)

        assertFalse(fixture.markerFile.exists())
    }

    @Test
    fun `when time since sdk init is less than duration threshold, writes startup crash file`() {
        val cache = fixture.getSut(appStartMillis = 1000L, currentTimeMillis = 2000L)

        val hints = HintUtils.createWithTypeCheckHint(DiskFlushHint())
        cache.store(fixture.envelope, hints)

        assertTrue(fixture.markerFile.exists())
    }

    internal class DiskFlushHint : DiskFlushNotification {
        override fun markFlushed() {}
    }
}
