package io.sentry.internal.debugmeta

import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.util.DebugMetaPropertiesApplier
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URL
import java.nio.charset.Charset
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResourcesDebugMetaLoaderTest {

    class Fixture {
        val logger = mock<ILogger>()
        val classLoader = mock<ClassLoader>()

        fun getSut(
            fileName: String = "sentry-debug-meta.properties",
            content: List<String>? = null
        ): ResourcesDebugMetaLoader {
            if (content != null) {
                val mockUrlMap = content.map { content ->
                    Pair(mock<URL>(), content)
                }
                whenever(classLoader.getResources(fileName)).thenReturn(
                    Collections.enumeration(mockUrlMap.map { it.first })
                )

                mockUrlMap.forEach {
                    whenever(it.first.openStream()).thenReturn(
                        it.second.byteInputStream(Charset.defaultCharset())
                    )
                }
            } else {
                whenever(classLoader.getResources(fileName)).thenReturn(
                    Collections.emptyEnumeration()
                )
            }
            return ResourcesDebugMetaLoader(logger, classLoader)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `reads from assets into properties and can be applied to options`() {
        val sut = fixture.getSut(
            content = listOf(
                """
                #Generated by sentry-maven-plugin
                #Wed May 17 15:33:34 CEST 2023
                io.sentry.ProguardUuids=34077988-a0e5-4839-9618-7400e1616d1b
                io.sentry.bundle-ids=88ba82db-cd26-4c09-8b31-21461d286b68
                io.sentry.build-tool=maven
                """.trimIndent()
            )
        )

        val options = SentryOptions()

        assertNotNull(sut.loadDebugMeta()) {
            DebugMetaPropertiesApplier.applyToOptions(options, it)
        }

        assertEquals(options.bundleIds, setOf("88ba82db-cd26-4c09-8b31-21461d286b68"))
        assertEquals(options.proguardUuid, "34077988-a0e5-4839-9618-7400e1616d1b")
    }

    @Test
    fun `reads multiple bundle IDs from assets into properties and can be applied to options`() {
        val sut = fixture.getSut(
            content = listOf(
                """
                #Generated by sentry-maven-plugin
                #Wed May 17 15:33:34 CEST 2023
                io.sentry.ProguardUuids=34077988-a0e5-4839-9618-7400e1616d1b
                io.sentry.bundle-ids=88ba82db-cd26-4c09-8b31-21461d286b68,8d11a44a-facd-46c1-a49b-87d256227101
                io.sentry.build-tool=maven
                """.trimIndent()
            )
        )

        val options = SentryOptions()

        assertNotNull(sut.loadDebugMeta()) {
            DebugMetaPropertiesApplier.applyToOptions(options, it)
        }

        assertEquals(options.bundleIds, setOf("88ba82db-cd26-4c09-8b31-21461d286b68", "8d11a44a-facd-46c1-a49b-87d256227101"))
        assertEquals(options.proguardUuid, "34077988-a0e5-4839-9618-7400e1616d1b")
    }

    @Test
    fun `reads multiple bundle IDs from multiple resource into properties and can be applied to options`() {
        val sut = fixture.getSut(
            content = listOf(
                """
                #Generated by sentry-maven-plugin
                #Wed May 17 15:33:34 CEST 2023
                io.sentry.ProguardUuids=7b1fae93-63fb-43ff-a70a-608dc5005970
                io.sentry.bundle-ids=88ba82db-cd26-4c09-8b31-21461d286b68,8d11a44a-facd-46c1-a49b-87d256227101
                io.sentry.build-tool=maven
                """.trimIndent(),
                """
                #Generated by sentry-maven-plugin
                #Wed May 17 15:33:34 CEST 2023
                io.sentry.ProguardUuids=37c90685-32a1-40db-9019-a2f0b05674cb
                io.sentry.bundle-ids=13e16819-accf-48da-a82d-f6ec94af9948
                io.sentry.build-tool=maven
                """.trimIndent()
            )
        )

        val options = SentryOptions()

        assertNotNull(sut.loadDebugMeta()) {
            DebugMetaPropertiesApplier.applyToOptions(options, it)
        }

        assertEquals(options.bundleIds, setOf("88ba82db-cd26-4c09-8b31-21461d286b68", "8d11a44a-facd-46c1-a49b-87d256227101", "13e16819-accf-48da-a82d-f6ec94af9948"))
        assertEquals(options.proguardUuid, "7b1fae93-63fb-43ff-a70a-608dc5005970")
    }

    @Test
    fun `when file does not exist, returns null`() {
        val sut = fixture.getSut()

        assertNull(sut.loadDebugMeta())
    }
}
