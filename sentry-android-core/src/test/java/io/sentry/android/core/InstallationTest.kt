package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallationTest {
  private lateinit var context: Context

  @BeforeTest
  fun `set up`() {
    // class has shared state, lets clean it
    Installation.deviceId = null
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `Generate ID and create temp file`() {
    val file = Files.createTempFile("tes", "te").toFile()
    assertNotNull(Installation.writeInstallationFile(file))
    file.deleteOnExit()
  }

  @Test
  fun `Read generated id from file`() {
    val file = Files.createTempFile("tes", "te").toFile()
    val id = Installation.writeInstallationFile(file)
    assertEquals(id, Installation.readInstallationFile(file))
    file.deleteOnExit()
  }

  @Test
  fun `Generate and read id, do not throw exception`() {
    assertNotNull(Installation.id(context))
    File(context.filesDir, "INSTALLATION").deleteOnExit()
  }
}
