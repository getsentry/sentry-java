package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryReplayOptionsTest {

    @Test
    fun `uses medium quality as default`() {
        val replayOptions = SentryReplayOptions(true, null)

        assertEquals(SentryReplayOptions.SentryReplayQuality.MEDIUM, replayOptions.quality)
        assertEquals(75_000, replayOptions.quality.bitRate)
        assertEquals(1.0f, replayOptions.quality.sizeScale)
    }

    @Test
    fun `low quality`() {
        val replayOptions = SentryReplayOptions(true, null).apply { quality = SentryReplayOptions.SentryReplayQuality.LOW }

        assertEquals(50_000, replayOptions.quality.bitRate)
        assertEquals(0.8f, replayOptions.quality.sizeScale)
    }

    @Test
    fun `high quality`() {
        val replayOptions = SentryReplayOptions(true, null).apply { quality = SentryReplayOptions.SentryReplayQuality.HIGH }

        assertEquals(100_000, replayOptions.quality.bitRate)
        assertEquals(1.0f, replayOptions.quality.sizeScale)
    }
}
