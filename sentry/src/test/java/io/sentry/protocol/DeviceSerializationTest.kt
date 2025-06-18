package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import java.util.TimeZone
import kotlin.test.assertEquals

class DeviceSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            Device().apply {
                name = "83f1de77-fdb0-470e-8249-8f5c5d894ec4"
                manufacturer = "e21b2405-e378-4a0b-ad2c-4822d97cd38c"
                brand = "1abbd13e-d1ca-4d81-bd1b-24aa2c339cf9"
                family = "67a4b8ea-6c38-4c33-8579-7697f538685c"
                model = "d6ca2f35-bcc5-4dd3-ad64-7c3b585e02fd"
                modelId = "d3f133bd-b0a2-4aa4-9eed-875eba93652e"
                archs =
                    arrayOf(
                        "856e5da3-774c-4663-a830-d19f0b7dbb5b",
                        "b345bd5a-90a5-4301-a5a2-6c102d7589b6",
                        "fd7ed64e-a591-49e0-8dc1-578234356d23",
                        "8cec4101-0305-480b-91ee-f3c007f668c3",
                        "22583b9b-195e-49bf-bfe8-825ae3a346f2",
                        "8675b7aa-5b94-42d0-bc14-72ea1bb7112e",
                    )
                batteryLevel = 0.45770407f
                isCharging = false
                isOnline = true
                orientation = Device.DeviceOrientation.PORTRAIT
                isSimulator = true
                memorySize = -6712323365568152393
                freeMemory = -953384122080236886
                usableMemory = -8999512249221323968
                isLowMemory = false
                storageSize = -3227905175393990709
                freeStorage = -3749039933924297357
                externalStorageSize = -7739608324159255302
                externalFreeStorage = -1562576688560812557
                screenWidthPixels = 1101873181
                screenHeightPixels = 1902392170
                screenDensity = 0.9829039f
                screenDpi = -2092079070
                bootTime = DateUtils.getDateTime("2004-11-04T08:38:00.000Z")
                timezone = TimeZone.getTimeZone("Europe/Vienna")
                id = "e0fa5c8d-83f5-4e70-bc60-1e82ad30e196"
                connectionType = "9ceb3a6c-5292-4ed9-8665-5732495e8ed4"
                batteryTemperature = 0.14775127f
                cpuDescription = "cpu0"
                processorCount = 4
                processorFrequency = 800.0
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/device.json")
        val actual = serializeToString(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/device.json")
        val actual = deserialize(expectedJson)
        val actualJson = serializeToString(actual)
        assertEquals(expectedJson, actualJson)
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serializeToString(jsonSerializable: JsonSerializable): String =
        this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        serialize(jsonWrt)
        return wrt.toString()
    }

    private fun deserialize(json: String): Device {
        val reader = JsonObjectReader(StringReader(json))
        return Device.Deserializer().deserialize(reader, fixture.logger)
    }
}
