package io.sentry.android.ndk

import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NativeModuleListLoader
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugImagesLoaderTest {
    private class Fixture {
        val nativeLoader = mock<NativeModuleListLoader>()
        val options = SentryAndroidOptions()

        fun getSut(): DebugImagesLoader {
            return DebugImagesLoader(options, nativeLoader)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `get images returns image list`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf())

        assertNotNull(sut.loadDebugImages())
        verify(fixture.nativeLoader).loadModuleList()
    }

    @Test
    fun `get images returns cached list if already called`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf())
        assertNotNull(sut.loadDebugImages())

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(io.sentry.ndk.DebugImage()))
        assertTrue(sut.loadDebugImages()!!.isEmpty())
    }

    @Test
    fun `get images cache values`() {
        val sut = fixture.getSut()
        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf())

        sut.loadDebugImages()

        assertNotNull(sut.cachedDebugImages)
    }

    @Test
    fun `clear images`() {
        val sut = fixture.getSut()

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf())
        sut.loadDebugImages()

        assertNotNull(sut.cachedDebugImages)

        sut.clearDebugImages()

        assertNull(sut.cachedDebugImages)
    }

    @Test
    fun `clear images list do not throw if there is an error`() {
        val sut = fixture.getSut()

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf())
        sut.loadDebugImages()

        whenever(fixture.nativeLoader.clearModuleList()).thenThrow(RuntimeException())
        sut.clearDebugImages()

        assertNull(sut.cachedDebugImages)
    }
}
