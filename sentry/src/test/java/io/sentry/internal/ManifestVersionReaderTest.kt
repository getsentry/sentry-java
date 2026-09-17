package io.sentry.internal

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.test.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ManifestVersionReaderTest {
  private class CloseTrackingInputStream(content: String) :
    ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8)) {
    var isClosed = false

    override fun close() {
      isClosed = true
      super.close()
    }
  }

  private class Fixture(contents: List<String>) {
    val classLoader = mock<ClassLoader>()
    val inputStreams = contents.map(::CloseTrackingInputStream)
    val connections = contents.map { mock<URLConnection>() }
    val urls = contents.map { mock<URL>() }

    init {
      whenever(classLoader.getResources("META-INF/MANIFEST.MF"))
        .thenReturn(Collections.enumeration(urls))
      urls.indices.forEach { index ->
        whenever(urls[index].openConnection()).thenReturn(connections[index])
        whenever(connections[index].inputStream).thenReturn(inputStreams[index])
      }
    }

    val sut = ManifestVersionReader(classLoader)
  }

  @Test
  fun `closes manifest stream and disables connection caching before opening it`() {
    val fixture = Fixture(listOf(validManifest()))

    fixture.sut.readManifestFiles()

    assertThat(fixture.inputStreams.single().isClosed).isTrue()
    inOrder(fixture.connections.single()) {
      verify(fixture.connections.single()).useCaches = false
      verify(fixture.connections.single()).inputStream
    }
  }

  @Test
  fun `closes malformed manifest stream and continues reading manifests`() {
    val fixture = Fixture(listOf("not a manifest\n", validManifest()))

    val versionInfo = fixture.sut.readOpenTelemetryVersion()

    assertThat(fixture.inputStreams.map { it.isClosed }).containsExactly(true, true).inOrder()
    assertThat(versionInfo).isNotNull()
    assertThat(versionInfo!!.sdkName).isEqualTo("sentry.java.opentelemetry.test")
    assertThat(versionInfo.sdkVersion).isEqualTo("1.2.3")
  }

  companion object {
    private fun validManifest() =
      """
      Manifest-Version: 1.0
      Sentry-Opentelemetry-SDK-Name: sentry.java.opentelemetry.test
      Implementation-Version: 1.2.3

      """
        .trimIndent()
  }
}
