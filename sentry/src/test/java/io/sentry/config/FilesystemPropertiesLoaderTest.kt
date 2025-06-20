package io.sentry.config

import io.sentry.NoOpLogger
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class FilesystemPropertiesLoaderTest {
  @get:Rule var folder = TemporaryFolder()

  @Test
  fun `returns properties when file is found`() {
    val file = folder.newFile("sentry.properties")
    file.writeText("dsn=some-dsn", Charset.defaultCharset())
    val loader = FilesystemPropertiesLoader(file.absolutePath, NoOpLogger.getInstance())
    val properties = loader.load()
    assertNotNull(properties)
    assertEquals("some-dsn", properties["dsn"])
  }

  @Test
  fun `returns null when property file not found`() {
    val loader =
      FilesystemPropertiesLoader(
        folder.root.absolutePath + "/incorrect.file",
        NoOpLogger.getInstance(),
      )
    val properties = loader.load()
    assertNull(properties)
  }

  @Test
  fun `returns null when filePath points to a folder`() {
    val file = folder.newFolder("some-directory")
    val loader = FilesystemPropertiesLoader(file.absolutePath, NoOpLogger.getInstance())
    val properties = loader.load()
    assertNull(properties)
  }
}
