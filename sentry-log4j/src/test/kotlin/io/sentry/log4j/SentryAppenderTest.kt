package io.sentry.log4j

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyArray
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ILogger
import io.sentry.core.ISentryClient
import io.sentry.core.Sentry
import io.sentry.core.SentryCoreFieldsProxy
import io.sentry.core.SentryOptions
import org.apache.log4j.Logger
import org.mockito.stubbing.Answer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryAppenderTest {

    private val fixture = object {

        val logger: ILogger = mock()
        val client: ISentryClient = mock()
        var dsn = "https://key@host/id"
        var cacheDir = "/"

        fun init(conf: (SentryOptions) -> Unit) {
            Sentry.close()
            Sentry.init {
                it.setLogger(logger)
                it.dsn = dsn
                it.cacheDirPath = cacheDir
                conf(it)
            }
            Sentry.bindClient(client)
            val logger = Logger.getRootLogger()
            logger.removeAllAppenders()
            logger.addAppender(SentryAppender())
        }
    }

    @Test
    fun `recovers from recursive reporting`() {
        val logger = Logger.getRootLogger()

        // configure Sentry to log its internal messages using Log4j and SentryAppender - this is an illegal
        // configuration and we actually test here that we can recover from that...
        val sentryLoggerCount = AtomicInteger(0)
        val sentryLogger = Answer() {
            if (Sentry.isEnabled()) {
                sentryLoggerCount.incrementAndGet()
                logger.error("Sentry is logging something!")
            }
        }
        whenever(fixture.logger.log(any(), any(), isA<Throwable>())).thenAnswer(sentryLogger)
        whenever(fixture.logger.log(any(), any(), anyArray<Any>())).thenAnswer(sentryLogger)
        whenever(fixture.logger.log(any(), any(), isA<Throwable>(), anyArray<Any>())).thenAnswer(sentryLogger)

        // simulate failures is Sentry to force logging during logging
        whenever(fixture.client.captureEvent(any(), any(), anyOrNull())).thenThrow(RuntimeException())

        fixture.init {
            // activate Sentry logging for the above setup to come into effect
            it.isDebug = true
        }

        val t = Thread{
            logger.info("Well, hello!")
        }

        t.start()

        // hopefully, 10s is enough for the above to execute in any environment.. Therefore, if the join throws an
        // exception, we know the logger entered an infinite loop and we're in trouble...
        t.join(10000)

        verify(fixture.client).captureEvent(any(), any(), isNull())
        assertTrue("The logger should have logged some sentry messages") { sentryLoggerCount.get() > 1 }
    }

    @Ignore
    @Test
    fun `does not capture if Sentry is disabled`() {
        // not sure how to test this, because it is impossible to get a disabled hub with a configured client (or
        // anything, really)
    }

    @Test
    fun `doesn't capture location for simple messages`() {
        val logger = Logger.getRootLogger()

        fixture.init {}

        logger.info("Well, hello!")

        verify(fixture.client).captureEvent(argThat {
            "Well, hello!" == message.formatted && SentryCoreFieldsProxy.throwableFrom(this) == null
        }, any(), isNull())
    }

    @Test
    fun `captures exceptions`() {
        val logger = Logger.getRootLogger()

        fixture.init {}

        val ex = Exception()
        logger.info("Whoops!", ex)

        verify(fixture.client).captureEvent(argThat {
            "Whoops!" == message.formatted && SentryCoreFieldsProxy.throwableFrom(this) == ex
        }, any(), isNull())
    }
}
