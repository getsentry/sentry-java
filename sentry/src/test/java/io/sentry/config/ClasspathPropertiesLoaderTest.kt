package io.sentry.config

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.NoOpLogger
import java.io.IOException
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClasspathPropertiesLoaderTest {
    private class Fixture(fileName: String = "sentry.properties", content: String? = null, throws: Boolean = false) {
        val classLoader = mock<ClassLoader>()
        val loader = ClasspathPropertiesLoader(fileName, classLoader, NoOpLogger.getInstance())

        init {
            if (content != null) {
                whenever(classLoader.getResourceAsStream(fileName)).thenReturn(content.byteInputStream(Charset.defaultCharset()))
            }
            if (throws) {
                whenever(classLoader.getResourceAsStream(fileName)).thenAnswer { throw IOException() }
            }
        }
    }

    @Test
    fun `loads properties from classpath`() {
        val fixture = Fixture(content = "dsn=some-dsn")
        val properties = fixture.loader.load()
        assertNotNull(properties)
        assertEquals(1, properties.size)
        assertEquals("some-dsn", properties["dsn"])
    }

    @Test
    fun `returns null if properties not found on the classpath`() {
        val fixture = Fixture()
        val properties = fixture.loader.load()
        assertNull(properties)
    }

    @Test
    fun `returns null if opening file throws an exception`() {
        val fixture = Fixture(throws = true)
        val properties = fixture.loader.load()
        assertNull(properties)
    }

    @Test
    fun `returns null if property not found in file on the classpath`() {
        val fixture = Fixture(content = "dsn=some-dsn")
        val properties = fixture.loader.load()
        assertNotNull(properties)
        assertNull(properties["sample.rate"])
    }
}
