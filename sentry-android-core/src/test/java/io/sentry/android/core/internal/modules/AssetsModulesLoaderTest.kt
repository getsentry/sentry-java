package io.sentry.android.core.internal.modules

import android.content.Context
import android.content.res.AssetManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import java.io.FileNotFoundException
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetsModulesLoaderTest {

    class Fixture {
        val context = mock<Context>()
        val assets = mock<AssetManager>()
        val logger = mock<ILogger>()

        fun getSut(
            fileName: String = "sentry-external-modules.txt",
            content: String? = null,
            throws: Boolean = false
        ): AssetsModulesLoader {
            if (content != null) {
                whenever(assets.open(fileName)).thenReturn(
                    content.byteInputStream(Charset.defaultCharset())
                )
            }
            if (throws) {
                whenever(assets.open(fileName)).thenThrow(FileNotFoundException())
            }
            whenever(context.assets).thenReturn(assets)
            return AssetsModulesLoader(context, logger)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `reads modules from assets into map`() {
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
        // the context only called once when there's no in-memory cache
        verify(fixture.context, times(1)).assets
    }

    @Test
    fun `when file does not exist, swallows exception and returns empty map`() {
        val sut = fixture.getSut(throws = true)

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
