package io.sentry.spring.jakarta.webflux

import io.sentry.IHub
import io.sentry.IScopes
import io.sentry.NoOpScopes
import io.sentry.Sentry
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ReactorUtilsTest {

    @BeforeTest
    fun setup() {
        Hooks.enableAutomaticContextPropagation()
    }

    @AfterTest
    fun teardown() {
        Sentry.setCurrentScopes(NoOpScopes.getInstance())
    }

    @Test
    fun `propagates hub inside mono`() {
        val hubToUse = mock<IScopes>()
        var hubInside: IScopes? = null
        val mono = ReactorUtils.withSentryHub(
            Mono.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentScopes()
                    it
                },
            hubToUse
        )

        assertEquals("hello", mono.block())
        assertSame(hubToUse, hubInside)
    }

    @Test
    fun `propagates hub inside flux`() {
        val hubToUse = mock<IScopes>()
        var hubInside: IScopes? = null
        val flux = ReactorUtils.withSentryHub(
            Flux.just("hello")
                .publishOn(Schedulers.boundedElastic())
                .map { it ->
                    hubInside = Sentry.getCurrentScopes()
                    it
                },
            hubToUse
        )

        assertEquals("hello", flux.blockFirst())
        assertSame(hubToUse, hubInside)
    }

    @Test
    fun `without reactive utils hub is not propagated to mono`() {
        val hubToUse = mock<IScopes>()
        var hubInside: IScopes? = null
        val mono = Mono.just("hello")
            .publishOn(Schedulers.boundedElastic())
            .map { it ->
                hubInside = Sentry.getCurrentScopes()
                it
            }

        assertEquals("hello", mono.block())
        assertNotSame(hubToUse, hubInside)
    }

    @Test
    fun `without reactive utils hub is not propagated to flux`() {
        val hubToUse = mock<IScopes>()
        var hubInside: IScopes? = null
        val flux = Flux.just("hello")
            .publishOn(Schedulers.boundedElastic())
            .map { it ->
                hubInside = Sentry.getCurrentScopes()
                it
            }

        assertEquals("hello", flux.blockFirst())
        assertNotSame(hubToUse, hubInside)
    }

    @Test
    fun `clones hub for mono`() {
        val mockScopes = mock<IScopes>()
        whenever(mockScopes.clone()).thenReturn(mock<IHub>())
        Sentry.setCurrentScopes(mockScopes)
        ReactorUtils.withSentry(Mono.just("hello")).block()

        verify(mockScopes).clone()
    }

    @Test
    fun `clones hub for flux`() {
        val mockScopes = mock<IScopes>()
        whenever(mockScopes.clone()).thenReturn(mock<IHub>())
        Sentry.setCurrentScopes(mockScopes)
        ReactorUtils.withSentry(Flux.just("hello")).blockFirst()

        verify(mockScopes).clone()
    }
}
