package io.sentry.spring.reactive

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class SentryReactiveHubContextHolderTest {

    @Test
    fun `Configure subscriber context`() {
        val adapter = mock<SentryReactiveHubAdapter>()

        Mono.just("")
            .flatMap { SentryReactiveHubContextHolder.getHubContext() }
            .subscriberContext(SentryReactiveHubContextHolder.withSentryHub(adapter))
            .`as` { StepVerifier.create(it) }
            .expectNext(adapter)
            .verifyComplete()
    }
}
