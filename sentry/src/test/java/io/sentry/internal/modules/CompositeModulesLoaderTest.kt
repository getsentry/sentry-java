package io.sentry.internal.modules

import io.sentry.ILogger
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeModulesLoaderTest {
    @Test
    fun `reads modules from multiple loaders and caches result`() {
        val logger = mock<ILogger>()
        val loader1 = mock<IModulesLoader>()
        val loader2 = mock<IModulesLoader>()

        whenever(loader1.orLoadModules).thenReturn(mapOf("spring-core" to "6.0.0"))
        whenever(loader2.orLoadModules).thenReturn(mapOf("spring-webmvc" to "6.0.2"))

        val sut = CompositeModulesLoader(listOf(loader1, loader2), logger)

        assertEquals(
            mapOf(
                "spring-core" to "6.0.0",
                "spring-webmvc" to "6.0.2",
            ),
            sut.orLoadModules,
        )

        verify(loader1).orLoadModules
        verify(loader2).orLoadModules

        assertEquals(
            mapOf(
                "spring-core" to "6.0.0",
                "spring-webmvc" to "6.0.2",
            ),
            sut.orLoadModules,
        )

        verifyNoMoreInteractions(loader1, loader2)
    }
}
