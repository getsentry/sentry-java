package io.sentry.reactor

class SentryReactorUtilsTest {

    // @BeforeTest
    // fun setup() {
    //    Hooks.enableAutomaticContextPropagation()
    // }

    // @AfterTest
    // fun teardown() {
    //    Sentry.setCurrentScopes(NoOpScopes.getInstance())
    // }

    // // @Test
    // fun `propagates scopes inside mono`() {
    //    val scopesToUse = mock<IScopes>()
    //    var scopesInside: IScopes? = null
    //    val mono = SentryReactorUtils.withSentryScopes(
    //        Mono.just("hello")
    //            .publishOn(Schedulers.boundedElastic())
    //            .map { it ->
    //                scopesInside = Sentry.getCurrentScopes()
    //                it
    //            },
    //        scopesToUse
    //    )

    //    assertEquals("hello", mono.block())
    //    assertSame(scopesToUse, scopesInside)
    // }

    // // @Test
    // fun `propagates scopes inside flux`() {
    //    val scopesToUse = mock<IScopes>()
    //    var scopesInside: IScopes? = null
    //    val flux = SentryReactorUtils.withSentryScopes(
    //        Flux.just("hello")
    //            .publishOn(Schedulers.boundedElastic())
    //            .map { it ->
    //                scopesInside = Sentry.getCurrentScopes()
    //                it
    //            },
    //        scopesToUse
    //    )

    //    assertEquals("hello", flux.blockFirst())
    //    assertSame(scopesToUse, scopesInside)
    // }

    // // @Test
    // fun `without reactive utils scopes is not propagated to mono`() {
    //    val scopesToUse = mock<IScopes>()
    //    var scopesInside: IScopes? = null
    //    val mono = Mono.just("hello")
    //        .publishOn(Schedulers.boundedElastic())
    //        .map { it ->
    //            scopesInside = Sentry.getCurrentScopes()
    //            it
    //        }

    //    assertEquals("hello", mono.block())
    //    assertNotSame(scopesToUse, scopesInside)
    // }

    // // @Test
    // fun `without reactive utils scopes is not propagated to flux`() {
    //    val scopesToUse = mock<IScopes>()
    //    var scopesInside: IScopes? = null
    //    val flux = Flux.just("hello")
    //        .publishOn(Schedulers.boundedElastic())
    //        .map { it ->
    //            scopesInside = Sentry.getCurrentScopes()
    //            it
    //        }

    //    assertEquals("hello", flux.blockFirst())
    //    assertNotSame(scopesToUse, scopesInside)
    // }

    // // @Test
    // fun `clones scopes for mono`() {
    //    val mockScopes = mock<IScopes>()
    //    whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    //    Sentry.setCurrentScopes(mockScopes)
    //    SentryReactorUtils.withSentry(Mono.just("hello")).block()

    //    verify(mockScopes).forkedCurrentScope(any())
    // }

    // // @Test
    // fun `clones scopes for flux`() {
    //    val mockScopes = mock<IScopes>()
    //    whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    //    Sentry.setCurrentScopes(mockScopes)
    //    SentryReactorUtils.withSentry(Flux.just("hello")).blockFirst()

    //    verify(mockScopes).forkedCurrentScope(any())
    // }
}
