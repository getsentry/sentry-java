package io.sentry.spring.jakarta.webflux

import io.sentry.IHub
import io.sentry.NoOpHub
import io.sentry.Sentry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks

class ReactorUtilsTest {

    @BeforeTest
    fun setup() {
        Hooks.enableAutomaticContextPropagation()
    }

    @AfterTest
    fun teardown() {
        Sentry.setCurrentHub(NoOpHub.getInstance());
    }

    @Test
    fun `propagates hub inside mono`() {
        val hubToUse = mock<IHub>()
        var hubInside: IHub? = null
        val mono = ReactorUtils.withSentryHub(
            Mono.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentHub()
                    it
                },
            hubToUse
        )

        assertEquals("hello", mono.block())
        assertSame(hubToUse, hubInside)
    }
    @Test
    fun `propagates hub inside flux`() {
        val hubToUse = mock<IHub>()
        var hubInside: IHub? = null
        val flux = ReactorUtils.withSentryHub(
            Flux.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentHub()
                    it
                },
            hubToUse
        )

        assertEquals("hello", flux.blockFirst())
        assertSame(hubToUse, hubInside)
    }

    @Test
    fun `without reactive utils hub is not propagated to mono`() {
        val hubToUse = mock<IHub>()
        var hubInside: IHub? = null
        val mono = Mono.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentHub()
                    it
                }

        assertEquals("hello", mono.block())
        assertNotSame(hubToUse, hubInside)
    }

    @Test
    fun `without reactive utils hub is not propagated to flux`() {
        val hubToUse = mock<IHub>()
        var hubInside: IHub? = null
        val flux = Flux.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentHub()
                    it
                }

        assertEquals("hello", flux.blockFirst())
        assertNotSame(hubToUse, hubInside)
    }

    @Test
    fun `clones hub for mono`() {
        val mockHub = mock<IHub>()
        whenever(mockHub.clone()).thenReturn(mock<IHub>())
        Sentry.setCurrentHub(mockHub)
        ReactorUtils.withSentry(Mono.just("hello")).block()

        verify(mockHub).clone()
    }

    @Test
    fun `clones hub for flux`() {
        val mockHub = mock<IHub>()
        whenever(mockHub.clone()).thenReturn(mock<IHub>())
        Sentry.setCurrentHub(mockHub)
        ReactorUtils.withSentry(Flux.just("hello")).blockFirst()

        verify(mockHub).clone()
    }
}
