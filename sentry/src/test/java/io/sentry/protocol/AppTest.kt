package io.sentry.protocol

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class AppTest {
    @Test
    fun `copying app wont have the same references`() {
        val app = App()
        app.appBuild = "app build"
        app.appIdentifier = "app identifier"
        app.appName = "app name"
        app.appStartTime = Date()
        app.appVersion = "app version"
        app.buildType = "build type"
        app.deviceAppHash = "device app hash"
        app.permissions = mapOf(Pair("internet", "granted"))
        app.viewNames = listOf("MainActivity")
        app.inForeground = true
        app.startType = "cold"
        val unknown = mapOf(Pair("unknown", "unknown"))
        app.unknown = unknown

        val clone = App(app)

        assertNotNull(clone)
        assertNotSame(app, clone)
        assertNotSame(app.appStartTime, clone.appStartTime)
        assertNotSame(app.permissions, clone.permissions)
        assertNotSame(app.viewNames, clone.viewNames)

        assertNotSame(app.unknown, clone.unknown)
    }

    @Test
    fun `copying app will have the same values`() {
        val app = App()
        app.appBuild = "app build"
        app.appIdentifier = "app identifier"
        app.appName = "app name"
        val date = Date()
        app.appStartTime = date
        app.appVersion = "app version"
        app.buildType = "build type"
        app.deviceAppHash = "device app hash"
        app.permissions = mapOf(Pair("internet", "granted"))
        app.viewNames = listOf("MainActivity")
        app.inForeground = true
        app.startType = "cold"
        val unknown = mapOf(Pair("unknown", "unknown"))
        app.unknown = unknown

        val clone = App(app)

        assertEquals("app build", clone.appBuild)
        assertEquals("app identifier", clone.appIdentifier)
        assertEquals("app name", clone.appName)
        assertNotNull(clone.appStartTime) {
            assertEquals(date.time, it.time)
        }
        assertEquals("app version", clone.appVersion)
        assertEquals("build type", clone.buildType)
        assertEquals("device app hash", clone.deviceAppHash)
        assertEquals(mapOf(Pair("internet", "granted")), clone.permissions)
        assertEquals(listOf("MainActivity"), clone.viewNames)

        assertEquals(true, clone.inForeground)
        assertEquals("cold", clone.startType)
        assertNotNull(clone.unknown) {
            assertEquals("unknown", it["unknown"])
        }
    }
}
