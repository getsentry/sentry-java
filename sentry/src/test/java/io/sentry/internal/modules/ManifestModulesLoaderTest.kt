package io.sentry.internal.modules

import io.sentry.ILogger
import java.io.IOException
import java.net.URL
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class ManifestModulesLoaderTest {
  @Test
  fun `reads modules from manifest urls caches result`() {
    val logger = mock<ILogger>()
    val classLoader = mock<ClassLoader>()

    whenever(classLoader.getResources(any()))
      .thenReturn(
        Collections.enumeration(
          listOf(
            URL(
              "jar:file:/Users/sentry/.gradle/caches/modules-2/files-2.1/org.springframework.boot/spring-boot-starter-security/3.0.0/efe7ffae5c9875e2019c6a701759ea524cb331ee/spring-boot-starter-security-3.0.0.jar!/META-INF/MANIFEST.MF"
            ),
            URL(
              "jar:file:/Users/sentry/.gradle/caches/modules-2/files-2.1/org.yaml/snakeyaml/1.33/2cd0a87ff7df953f810c344bdf2fe3340b954c69/snakeyaml-1.33.jar!/META-INF/MANIFEST.MF"
            ),
            URL(
              "jar:file:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/aspectjweaver-1.9.9.1.jar!/META-INF/MANIFEST.MF"
            ),
            URL(
              "jar:file:/Users/sentry/repos/sentry-java/sentry-samples/sentry-samples-spring-boot-jakarta/build/libs/sentry-samples-spring-boot-jakarta-0.0.1-SNAPSHOT.jar!/BOOT-INF/lib/kotlin-stdlib-jdk8-1.6.10.jar!/META-INF/MANIFEST.MF"
            ),
            URL("http://sentry.io"),
            URL(
              "jar:file:/Users/sentry/repos/sentry-java/sentry-samples/sentry-samples-spring-boot-jakarta/build/libs/sentry-samples-spring-boot-jakarta-0.0.1-SNAPSHOT.jar!/BOOT-INF/lib/hello-world.jar!/META-INF/MANIFEST.MF"
            ),
          )
        )
      )

    val sut = ManifestModulesLoader(classLoader, logger)

    assertEquals(
      mapOf(
        "spring-boot-starter-security" to "3.0.0",
        "snakeyaml" to "1.33",
        "aspectjweaver" to "1.9.9.1",
        "kotlin-stdlib-jdk8" to "1.6.10",
      ),
      sut.orLoadModules,
    )

    verify(classLoader).getResources(any())

    assertEquals(
      mapOf(
        "spring-boot-starter-security" to "3.0.0",
        "snakeyaml" to "1.33",
        "aspectjweaver" to "1.9.9.1",
        "kotlin-stdlib-jdk8" to "1.6.10",
      ),
      sut.orLoadModules,
    )

    verifyNoMoreInteractions(classLoader)
  }

  @Test
  fun `reading modules from manifest returns empty map on IOException`() {
    val logger = mock<ILogger>()
    val classLoader = mock<ClassLoader>()

    whenever(classLoader.getResources(any())).thenThrow(IOException("thrown on purpose"))

    val sut = ManifestModulesLoader(classLoader, logger)

    assertEquals(emptyMap(), sut.orLoadModules)

    verify(classLoader).getResources(any())

    assertEquals(emptyMap(), sut.orLoadModules)

    verifyNoMoreInteractions(classLoader)
  }
}
