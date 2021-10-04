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
        val unknown = mapOf(Pair("unknown", "unknown"))
        app.setUnknown(unknown)

        val clone = App(app)

        assertNotNull(clone)
        assertNotSame(app, clone)
        assertNotSame(app.appStartTime, clone.appStartTime)

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
        val unknown = mapOf(Pair("unknown", "unknown"))
        app.setUnknown(unknown)

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
        assertNotNull(clone.unknown) {
            assertEquals("unknown", it["unknown"])
        }
    }
}
