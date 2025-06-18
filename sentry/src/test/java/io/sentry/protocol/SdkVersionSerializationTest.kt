package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryIntegrationPackageStorage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SdkVersionSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            SdkVersion(
                "3e934135-3f2b-49bc-8756-9f025b55143e",
                "3e31738e-4106-42d0-8be2-4a3a1bc648d3",
            ).apply {
                addPackage(
                    "b59a1949-9950-4203-b394-ddd8d02c9633",
                    "3d7790f3-7f32-43f7-b82f-9f5bc85205a8",
                )
                addIntegration("daec50ae-8729-49b5-82f7-991446745cd5")
                addIntegration("8fc94968-3499-4a2c-b4d7-ecc058d9c1b0")
            }
    }

    private val fixture = Fixture()

    @Before
    fun setup() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

    @After
    fun teardown() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/sdk_version.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/sdk_version.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserializeIgnoresStoredIntegrationsAndPackages() {
        SentryIntegrationPackageStorage.getInstance().addIntegration("testIntegration")
        SentryIntegrationPackageStorage.getInstance().addPackage("testPackage", "0.0.1")

        val expectedJson = sanitizedFile("json/sdk_version.json")
        val actual = deserialize(expectedJson)

        assertFalse(actual.integrationSet.contains("testIntegration"))
        assertNull(actual.packageSet.firstOrNull { it.name == "testIntegration" })
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): SdkVersion {
        val reader = JsonObjectReader(StringReader(json))
        return SdkVersion.Deserializer().deserialize(reader, fixture.logger)
    }
}
