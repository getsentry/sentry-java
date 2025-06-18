package io.sentry.android.ndk

import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NativeModuleListLoader
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugImagesLoaderTest {
    private class Fixture {
        val nativeLoader = mock<NativeModuleListLoader>()
        val options = SentryAndroidOptions()

        fun getSut(): DebugImagesLoader {
            val loader = DebugImagesLoader(options, nativeLoader)
            loader.clearDebugImages()
            return loader
        }
    }

    private var fixture = Fixture()

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
    fun `find images by address`() {
        val sut = fixture.getSut()

        val image1 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x1000"
                imageSize = 0x1000L
            }

        val image2 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x2000"
                imageSize = 0x1000L
            }

        val image3 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x3000"
                imageSize = 0x1000L
            }

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(image1, image2, image3))

        val result =
            sut.loadDebugImagesForAddresses(
                setOf("0x1500", "0x2500"),
            )

        assertNotNull(result)
        assertEquals(2, result.size)
        assertTrue(result.any { it.imageAddr == image1.imageAddr })
        assertTrue(result.any { it.imageAddr == image2.imageAddr })
    }

    @Test
    fun `find images with invalid addresses are not added to the result`() {
        val sut = fixture.getSut()

        val image1 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x1000"
                imageSize = 0x1000L
            }

        val image2 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x2000"
                imageSize = 0x1000L
            }

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(image1, image2))

        val hexAddresses = setOf("0xINVALID", "0x1500")
        val result = sut.loadDebugImagesForAddresses(hexAddresses)

        assertEquals(1, result!!.size)
    }

    @Test
    fun `find images by address returns null if result is empty`() {
        val sut = fixture.getSut()

        val image1 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x1000"
                imageSize = 0x1000L
            }

        val image2 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x2000"
                imageSize = 0x1000L
            }

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(image1, image2))

        val hexAddresses = setOf("0x100", "0x10500")
        val result = sut.loadDebugImagesForAddresses(hexAddresses)

        assertNull(result)
    }

    @Test
    fun `invalid image adresses are ignored for loadDebugImagesForAddresses`() {
        val sut = fixture.getSut()

        val image1 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0xNotANumber"
                imageSize = 0x1000L
            }

        val image2 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x2000"
                imageSize = null
            }

        val image3 =
            io.sentry.ndk.DebugImage().apply {
                imageAddr = "0x5000"
                imageSize = 0x1000L
            }

        whenever(fixture.nativeLoader.loadModuleList()).thenReturn(arrayOf(image1, image2, image3))

        val hexAddresses = setOf("0x100", "0x2000", "0x2000", "0x5000")
        val result = sut.loadDebugImagesForAddresses(hexAddresses)

        assertNotNull(result)
        assertEquals(2, result.size)
    }
}
