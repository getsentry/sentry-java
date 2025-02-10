package io.sentry.reactor

import io.micrometer.context.ContextRegistry
import io.sentry.IScopes
import io.sentry.NoOpScopes
import io.sentry.Sentry
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
// import org.mockito.kotlin.any
// import org.mockito.kotlin.verify
// import org.mockito.kotlin.whenever
// import kotlin.test.assertSame

class SentryReactorUtilsTest {

    @BeforeTest
    fun setup() {
        println("setUp")
        ContextRegistry.getInstance().registerThreadLocalAccessor(SentryReactorThreadLocalAccessor())
        Hooks.enableAutomaticContextPropagation()
    }

    @AfterTest
    fun teardown() {
        println("teardown")
        Sentry.setCurrentScopes(NoOpScopes.getInstance())
    }

    // @Test
    // fun `propagates scopes inside mono`() {
    //     val scopesToUse = mock<IScopes>()
    //     var scopesInside: IScopes? = null
    //     val mono = SentryReactorUtils.withSentryScopes(
    //         Mono.just("hello")
    //             .publishOn(Schedulers.boundedElastic())
    //             .map { it ->
    //                 scopesInside = Sentry.getCurrentScopes()
    //                 it
    //             },
    //         scopesToUse
    //     )

    //     assertEquals("hello", mono.block())
    //     assertSame(scopesToUse, scopesInside)
    // }

    // @Test
    // fun `propagates scopes inside flux`() {
    //     val scopesToUse = mock<IScopes>()
    //     var scopesInside: IScopes? = null
    //     val flux = SentryReactorUtils.withSentryScopes(
    //         Flux.just("hello")
    //             .publishOn(Schedulers.boundedElastic())
    //             .map { it ->
    //                 scopesInside = Sentry.getCurrentScopes()
    //                 it
    //             },
    //         scopesToUse
    //     )

    //     assertEquals("hello", flux.blockFirst())
    //     assertSame(scopesToUse, scopesInside)
    // }

    @Test
    fun `without reactive utils scopes is not propagated to mono`() {
        val scopesToUse = mock<IScopes>()
        var scopesInside: IScopes? = null
        val mono = Mono.just("hello")
            .publishOn(Schedulers.boundedElastic())
            .map { it ->
                scopesInside = Sentry.getCurrentScopes()
                it
            }

        assertEquals("hello", mono.block())
        assertNotSame(scopesToUse, scopesInside)
    }

    @Test
    fun `without reactive utils scopes is not propagated to flux`() {
        val scopesToUse = mock<IScopes>()
        var scopesInside: IScopes? = null
        val flux = Flux.just("hello")
            .publishOn(Schedulers.boundedElastic())
            .map { it ->
                scopesInside = Sentry.getCurrentScopes()
                it
            }

        assertEquals("hello", flux.blockFirst())
        assertNotSame(scopesToUse, scopesInside)
    }

    // @Test
    // fun `clones scopes for mono`() {
    //     val mockScopes = mock<IScopes>()
    //     whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    //     Sentry.setCurrentScopes(mockScopes)
    //     SentryReactorUtils.withSentry(Mono.just("hello")).block()

    //     verify(mockScopes).forkedCurrentScope(any())
    // }

    // @Test
    // fun `clones scopes for flux`() {
    //     val mockScopes = mock<IScopes>()
    //     whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    //     Sentry.setCurrentScopes(mockScopes)
    //     SentryReactorUtils.withSentry(Flux.just("hello")).blockFirst()

    //     verify(mockScopes).forkedCurrentScope(any())
    // }
}
