package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryReplayOptionsTest {

    @Test
    fun `uses medium quality as default`() {
        val replayOptions = SentryReplayOptions()

        assertEquals(SentryReplayOptions.SentryReplayQuality.MEDIUM, replayOptions.quality)
        assertEquals(40_000, replayOptions.bitRate)
        assertEquals(1.0f, replayOptions.sizeScale)
    }

    @Test
    fun `low quality`() {
        val replayOptions = SentryReplayOptions().apply { quality = SentryReplayOptions.SentryReplayQuality.LOW }

        assertEquals(20_000, replayOptions.bitRate)
        assertEquals(0.8f, replayOptions.sizeScale)
    }

    @Test
    fun `high quality`() {
        val replayOptions = SentryReplayOptions().apply { quality = SentryReplayOptions.SentryReplayQuality.HIGH }

        assertEquals(60_000, replayOptions.bitRate)
        assertEquals(1.0f, replayOptions.sizeScale)
    }
}
