package io.sentry.internal.modules

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourcesModulesLoaderTest {

    class Fixture {
        val logger = mock<ILogger>()
        val classLoader = mock<ClassLoader>()

        fun getSut(
            fileName: String = "sentry-external-modules.txt",
            content: String? = null
        ): ResourcesModulesLoader {
            if (content != null) {
                whenever(classLoader.getResourceAsStream(fileName)).thenReturn(
                    content.byteInputStream(Charset.defaultCharset())
                )
            }
            return ResourcesModulesLoader(logger, classLoader)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `reads modules from resources into map`() {
        val sut = fixture.getSut(
            content =
            """
            com.squareup.okhttp3:okhttp:3.14.9
            com.squareup.okio:okio:1.17.2
            """.trimIndent()
        )

        assertEquals(
            mapOf(
                "com.squareup.okhttp3:okhttp" to "3.14.9",
                "com.squareup.okio:okio" to "1.17.2"
            ),
            sut.orLoadModules
        )
    }

    @Test
    fun `caches modules after first read`() {
        val sut = fixture.getSut(
            content =
            """
            com.squareup.okhttp3:okhttp:3.14.9
            com.squareup.okio:okio:1.17.2
            """.trimIndent()
        )

        // first, call method to get modules cached
        sut.orLoadModules

        // then call it second time
        assertEquals(
            mapOf(
                "com.squareup.okhttp3:okhttp" to "3.14.9",
                "com.squareup.okio:okio" to "1.17.2"
            ),
            sut.orLoadModules
        )
        // the classloader only called once when there's no in-memory cache
        verify(fixture.classLoader, times(1)).getResourceAsStream(any())
    }

    @Test
    fun `when file does not exist, returns empty map`() {
        val sut = fixture.getSut()

        assertEquals(emptyMap(), sut.orLoadModules)
    }

    @Test
    fun `when content is malformed, swallows exception and returns empty map`() {
        val sut = fixture.getSut(
            content =
            """
            com.squareup.okhttp3;3.14.9
            """.trimIndent()
        )

        assertEquals(emptyMap(), sut.orLoadModules)
    }
}
