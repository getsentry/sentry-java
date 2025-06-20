package io.sentry.config

import io.sentry.NoOpLogger
import java.io.IOException
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClasspathPropertiesLoaderTest {
  private class Fixture {
    val classLoader = mock<ClassLoader>()
    lateinit var loader: ClasspathPropertiesLoader

    fun getSut(
      fileName: String = "sentry.properties",
      content: String? = null,
      throws: Boolean = false,
    ): ClasspathPropertiesLoader {
      loader = ClasspathPropertiesLoader(fileName, classLoader, NoOpLogger.getInstance())
      if (content != null) {
        whenever(classLoader.getResourceAsStream(fileName))
          .thenReturn(content.byteInputStream(Charset.defaultCharset()))
      }
      if (throws) {
        whenever(classLoader.getResourceAsStream(fileName)).thenAnswer { throw IOException() }
      }
      return loader
    }
  }

  private val fixture = Fixture()

  @Test
  fun `loads properties from classpath`() {
    val sut = fixture.getSut(content = "dsn=some-dsn")
    val properties = sut.load()
    assertNotNull(properties)
    assertEquals(1, properties.size)
    assertEquals("some-dsn", properties["dsn"])
  }

  @Test
  fun `returns null if properties not found on the classpath`() {
    val sut = fixture.getSut()
    val properties = sut.load()
    assertNull(properties)
  }

  @Test
  fun `returns null if opening file throws an exception`() {
    val sut = fixture.getSut(throws = true)
    val properties = sut.load()
    assertNull(properties)
  }

  @Test
  fun `returns null if property not found in file on the classpath`() {
    val sut = fixture.getSut(content = "dsn=some-dsn")
    val properties = sut.load()
    assertNotNull(properties)
    assertNull(properties["sample.rate"])
  }
}
