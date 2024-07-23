package io.sentry.android.replay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplayCacheTest.Fixture
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class ReplayIntegrationTest {
    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions()
        val hub = mock<IHub>()

        fun getSut(
            context: Context,
            sessionSampleRate: Double = 1.0,
            errorSampleRate: Double = 1.0,
            recorderProvider: (() -> Recorder)? = null,
            replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null,
            recorderConfigProvider: ((configChanged: Boolean) -> ScreenshotRecorderConfig)? = null,
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance()
        ): ReplayIntegration {
            options.run {
                experimental.sessionReplay.errorSampleRate = errorSampleRate
                experimental.sessionReplay.sessionSampleRate = sessionSampleRate
            }
            return ReplayIntegration(
                context,
                dateProvider,
                recorderProvider,
                recorderConfigProvider = recorderConfigProvider,
                replayCacheProvider = null,
                replayCaptureStrategyProvider = replayCaptureStrategyProvider
            )
        }
    }

    private val fixture = Fixture()
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

    @Test
    @Config(sdk = [24])
    fun `when API is below 26, does not register`() {
        val replay = fixture.getSut(context)

        replay.register(fixture.hub, fixture.options)

        assertFalse(replay.isEnabled.get())
    }

    @Test
    fun `when no sample rate is set, does not register`() {
        val replay = fixture.getSut(context, 0.0, 0.0)

        replay.register(fixture.hub, fixture.options)

        assertFalse(replay.isEnabled.get())
    }

    @Test
    fun `registers the integration`() {
        var recorderCreated = false
        val replay = fixture.getSut(context, recorderProvider = {
            recorderCreated = true
            mock()
        })

        replay.register(fixture.hub, fixture.options)

        assertTrue(replay.isEnabled.get())
        assertEquals(1, fixture.options.scopeObservers.size)
        assertTrue(recorderCreated)
        assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("Replay"))
    }

    @Test
    fun `when disabled start does nothing`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.start()

        verify(captureStrategy, never()).start(any(), any(), any(), any())
    }

    @Test
    fun `start sets isRecording to true`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        assertTrue(replay.isRecording)
    }

    @Test
    fun `starting two times does nothing`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.start()

        verify(captureStrategy, times(1)).start(any(), eq(0), argThat { this != SentryId.EMPTY_ID }, eq(true))
    }

    @Test
    fun `does not start replay when session is not sampled`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, errorSampleRate = 0.0, sessionSampleRate = 0.0, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(captureStrategy, never()).start(any(), eq(0), argThat { this != SentryId.EMPTY_ID }, eq(true))
    }

    @Test
    fun `still starts replay when errorsSampleRate is set`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, sessionSampleRate = 0.0, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(captureStrategy, times(1)).start(any(), eq(0), argThat { this != SentryId.EMPTY_ID }, eq(true))
    }

    @Test
    fun `calls recorder start`() {
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(recorder).start(any())
    }

    @Test
    fun `resume does not resume when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.resume()

        verify(captureStrategy, never()).resume()
    }

    @Test
    fun `resume resumes capture strategy and recorder`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.resume()

        verify(captureStrategy).resume()
        verify(recorder).resume()
    }

    @Test
    fun `sendReplayForEvent does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        replay.sendReplayForEvent(event, Hint())

        verify(captureStrategy, never()).sendReplayForEvent(any(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `sendReplayForEvent does nothing for non errored events`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        val event = SentryEvent()
        replay.sendReplayForEvent(event, Hint())

        verify(captureStrategy, never()).sendReplayForEvent(any(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `sendReplayForEvent does nothing when currentReplayId is not set`() {
        val captureStrategy = mock<CaptureStrategy> {
            whenever(mock.currentReplayId).thenReturn(SentryId.EMPTY_ID)
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        replay.sendReplayForEvent(event, Hint())

        verify(captureStrategy, never()).sendReplayForEvent(any(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `sendReplayForEvent calls and converts strategy`() {
        val captureStrategy = mock<CaptureStrategy> {
            whenever(mock.currentReplayId).thenReturn(SentryId())
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        val id = SentryId()
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        event.eventId = id
        val hint = Hint()
        replay.sendReplayForEvent(event, hint)

        verify(captureStrategy).sendReplayForEvent(eq(false), eq(id.toString()), eq(hint), any())
        verify(captureStrategy).convert()
    }

    @Test
    fun `pause does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.pause()

        verify(captureStrategy, never()).pause()
    }

    @Test
    fun `pause calls strategy and recorder`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.pause()

        verify(captureStrategy).pause()
        verify(recorder).pause()
    }

    @Test
    fun `stop does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.stop()

        verify(captureStrategy, never()).stop()
        verify(recorder, never()).stop()
    }

    @Test
    fun `stop calls stop for recorder and strategy and sets recording to false`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.stop()

        verify(captureStrategy).stop()
        verify(recorder).stop()
        assertFalse(replay.isRecording)
    }

    @Test
    fun `close cleans up resources`() {
        val recorder = mock<Recorder>()
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })
        replay.register(fixture.hub, fixture.options)
        replay.start()

        replay.close()

        verify(recorder).stop()
        verify(recorder).close()
        verify(captureStrategy).stop()
        verify(captureStrategy).close()
        assertFalse(replay.isRecording())
    }

    @Test
    fun `onConfigurationChanged does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.onConfigurationChanged(mock())

        verify(captureStrategy, never()).onConfigurationChanged(any())
        verify(recorder, never()).stop()
    }

    @Test
    fun `onConfigurationChanged stops and restarts recorder with a new recorder config`() {
        var configChanged = false
        val recorderConfig = mock<ScreenshotRecorderConfig>()
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            recorderConfigProvider = { configChanged = it; recorderConfig }
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onConfigurationChanged(mock())

        verify(recorder).stop()
        verify(captureStrategy).onConfigurationChanged(eq(recorderConfig))
        verify(recorder, times(2)).start(eq(recorderConfig))
        assertTrue(configChanged)
    }
}
