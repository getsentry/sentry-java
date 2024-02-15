package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.ILogger
import io.sentry.ReplayRecording
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import org.mockito.kotlin.mock

class ReplayRecordingSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = ReplayRecording().apply {
            segmentId = 0
            payload = listOf(

            )
        }
    }
    private val fixture = Fixture()
}
