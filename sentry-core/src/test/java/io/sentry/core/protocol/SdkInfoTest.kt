package io.sentry.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdkInfoTest {

    @Test
    fun `SdkInfo creates object with given values`() {
        val sdkInfo = SdkInfo.createSdkInfo("test", "1.2.3")
        assertEquals("test", sdkInfo.sdkName)
        assertEquals(1, sdkInfo.versionMajor)
        assertEquals(2, sdkInfo.versionMinor)
        assertEquals(3, sdkInfo.versionPatchlevel)
    }

    @Test
    fun `SdkInfo creates object with given values but suffix`() {
        val sdkInfo = SdkInfo.createSdkInfo("test", "1.2.3-SNAPSHOT")
        assertEquals("test", sdkInfo.sdkName)
        assertEquals(1, sdkInfo.versionMajor)
        assertEquals(2, sdkInfo.versionMinor)
        assertEquals(3, sdkInfo.versionPatchlevel)
    }

    @Test
    fun `SdkInfo creates object with given values but patch`() {
        val sdkInfo = SdkInfo.createSdkInfo("test", "1.2")
        assertEquals("test", sdkInfo.sdkName)
        assertEquals(1, sdkInfo.versionMajor)
        assertEquals(2, sdkInfo.versionMinor)
        assertNull(sdkInfo.versionPatchlevel)
    }

    @Test
    fun `SdkInfo creates object with given values but minor`() {
        val sdkInfo = SdkInfo.createSdkInfo("test", "1")
        assertEquals("test", sdkInfo.sdkName)
        assertEquals(1, sdkInfo.versionMajor)
        assertNull(sdkInfo.versionMinor)
        assertNull(sdkInfo.versionPatchlevel)
    }

    @Test
    fun `SdkInfo creates object with given values but no version`() {
        val sdkInfo = SdkInfo.createSdkInfo("test", "")
        assertEquals("test", sdkInfo.sdkName)
        assertNull(sdkInfo.versionMajor)
        assertNull(sdkInfo.versionMinor)
        assertNull(sdkInfo.versionPatchlevel)
    }
}
