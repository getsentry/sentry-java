package io.sentry
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MultiplexedTransportFactoryTest {
    private class Fixture {
        val dsn1 = "https://d4d82fc1c2c4032a83f3a29aa3a3aff@fake-sentry.io:65535/2147483647"
        var dsn2 = "https://007508298f9048729e434faa9ae4f49@fake-sentry.io:65535/1234567890"
        var transportFactory = mock<ITransportFactory>()
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsn1
        }

        fun getSUT(): MultiplexedTransportFactory {
            return MultiplexedTransportFactory(transportFactory, listOf(dsn1, dsn2))
        }
    }

    private val fixture = Fixture()

    @Test
    fun `create transport`() {
        val requestDetailsResolver = RequestDetailsResolver(fixture.sentryOptions)

        val transport = fixture.getSUT()
            .create(fixture.sentryOptions, requestDetailsResolver.resolve())

        assertNotNull(transport)

        val captor = argumentCaptor<RequestDetails>()
        verify(fixture.transportFactory, times(2))
            .create(eq(fixture.sentryOptions), captor.capture())

        assertEquals("/api/2147483647/envelope/", captor.firstValue.url.path)
        assertEquals("/api/1234567890/envelope/", captor.secondValue.url.path)
        assertContains(
            captor.firstValue.headers["X-Sentry-Auth"].toString(),
            "sentry_key=d4d82fc1c2c4032a83f3a29aa3a3aff"
        )
        assertContains(
            captor.secondValue.headers["X-Sentry-Auth"].toString(),
            "sentry_key=007508298f9048729e434faa9ae4f49"
        )
    }
}
