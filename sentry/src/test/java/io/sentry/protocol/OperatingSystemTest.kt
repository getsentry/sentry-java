package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class OperatingSystemTest {
    @Test
    fun `copying operating system wont have the same references`() {
        val operatingSystem = OperatingSystem()
        val unknown = mapOf(Pair("unknown", "unknown"))
        operatingSystem.setUnknown(unknown)

        val clone = OperatingSystem(operatingSystem)

        assertNotNull(clone)
        assertNotSame(operatingSystem, clone)

        assertNotSame(operatingSystem.unknown, clone.unknown)
    }

    @Test
    fun `copying operating system will have the same values`() {
        val operatingSystem = OperatingSystem()
        operatingSystem.name = "name"
        operatingSystem.version = "version"
        operatingSystem.rawDescription = "raw description"
        operatingSystem.build = "build"
        operatingSystem.kernelVersion = "kernel version"
        operatingSystem.isRooted = true
        val unknown = mapOf(Pair("unknown", "unknown"))
        operatingSystem.setUnknown(unknown)

        val clone = OperatingSystem(operatingSystem)

        assertEquals("name", clone.name)
        assertEquals("version", clone.version)
        assertEquals("raw description", clone.rawDescription)
        assertEquals("build", clone.build)
        assertEquals("kernel version", clone.kernelVersion)
        assertEquals(true, clone.isRooted)
        assertNotNull(clone.unknown) {
            assertEquals("unknown", it["unknown"])
        }
    }
}
