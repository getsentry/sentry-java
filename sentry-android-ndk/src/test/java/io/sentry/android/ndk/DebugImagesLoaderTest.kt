package io.sentry.android.ndk

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.SentryOptions
import io.sentry.protocol.DebugImage
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugImagesLoaderTest {
    private class Fixture {
        val nativeLoader = mock<IModuleListLoader>()
        val options = SentryOptions()

        fun getSut(ndk: Boolean = true): DebugImagesLoader {
            options.isEnableNdk = ndk
            return DebugImagesLoader(options, nativeLoader)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `get images returns null if ndk is disabled`() {
        val sut = fixture.getSut(false)

        assertNull(sut.debugImages)
        verify(fixture.nativeLoader, never()).moduleList
    }

    @Test
    fun `get images returns image list if ndk is enabled`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf())

        assertNotNull(sut.debugImages)
        verify(fixture.nativeLoader).moduleList
    }

    @Test
    fun `get images returns cached list if alreadu called`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf())
        assertNotNull(sut.debugImages)

        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf(DebugImage()))
        assertTrue(sut.debugImages!!.isEmpty())
    }

    @Test
    fun `get images cache values if ndk is enabled`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf())

        sut.debugImages

        assertNotNull(sut.cachedDebugImages)
    }

    @Test
    fun `clear images returns if ndk is disabled`() {
        val sut = fixture.getSut(false)

        sut.clearDebugImages()

        verify(fixture.nativeLoader, never()).clearModuleList()
    }

    @Test
    fun `clear images if ndk is enabled`() {
        val sut = fixture.getSut()

        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf())
        sut.debugImages

        assertNotNull(sut.cachedDebugImages)

        sut.clearDebugImages()

        assertNull(sut.cachedDebugImages)
    }

    @Test
    fun `clear images list do not throw if there is an error`() {
        val sut = fixture.getSut()

        whenever(fixture.nativeLoader.moduleList).thenReturn(arrayOf())
        sut.debugImages

        whenever(fixture.nativeLoader.clearModuleList()).thenThrow(RuntimeException())
        sut.clearDebugImages()

        assertNull(sut.cachedDebugImages)
    }
}
