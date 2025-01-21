package io.sentry.android.ndk

import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.DebugImage
import io.sentry.ndk.NativeModuleListLoader
import org.junit.Assert.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertContains
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

    @Test
    fun testFindImageByAddress() {
        val sut = fixture.getSut()

        val image1 = DebugImage()
        image1.imageAddr = "0x1000"
        image1.imageSize = 0x1000L

        val image2 = DebugImage()
        image2.imageAddr = "0x2000"
        image2.imageSize = 0x1000L

        val image3 = DebugImage()
        image3.imageAddr = "0x3000"
        image3.imageSize = 0x1000L

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(image1, image2))

        val result = sut.loadDebugImagesForAddresses(
            setOf(0x1500L, 0x2500L)
        )!!.toList()

        assertEquals(2, result.size)
        assertEquals(image1.imageAddr, result[0].imageAddr)
        assertEquals(image2.imageAddr, result[1].imageAddr)
    }
}
