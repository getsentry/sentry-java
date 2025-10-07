package io.sentry.util

import io.sentry.SentryOptions
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugMetaPropertiesApplierTest {

  @Test
  fun `applies distribution options from properties`() {
    val properties = Properties()
    properties.setProperty("io.sentry.distribution.org-slug", "test-org")
    properties.setProperty("io.sentry.distribution.project-slug", "test-project")
    properties.setProperty("io.sentry.distribution.org-auth-token", "test-token")
    properties.setProperty("io.sentry.distribution.build-configuration", "debug")

    val options = SentryOptions()
    DebugMetaPropertiesApplier.apply(options, listOf(properties))

    assertEquals("test-org", options.distribution.orgSlug)
    assertEquals("test-project", options.distribution.projectSlug)
    assertEquals("test-token", options.distribution.orgAuthToken)
    assertEquals("debug", options.distribution.buildConfiguration)
  }

  @Test
  fun `applies partial distribution options from properties`() {
    val properties = Properties()
    properties.setProperty("io.sentry.distribution.org-slug", "test-org")
    properties.setProperty("io.sentry.distribution.project-slug", "test-project")

    val options = SentryOptions()
    DebugMetaPropertiesApplier.apply(options, listOf(properties))

    assertEquals("test-org", options.distribution.orgSlug)
    assertEquals("test-project", options.distribution.projectSlug)
    assertEquals("", options.distribution.orgAuthToken)
    assertNull(options.distribution.buildConfiguration)
  }

  @Test
  fun `does not override existing distribution options`() {
    val properties = Properties()
    properties.setProperty("io.sentry.distribution.org-slug", "properties-org")
    properties.setProperty("io.sentry.distribution.project-slug", "properties-project")
    properties.setProperty("io.sentry.distribution.org-auth-token", "properties-token")
    properties.setProperty("io.sentry.distribution.build-configuration", "properties-config")

    val options = SentryOptions()
    options.distribution.orgSlug = "existing-org"
    options.distribution.projectSlug = "existing-project"
    options.distribution.orgAuthToken = "existing-token"
    options.distribution.buildConfiguration = "existing-config"

    DebugMetaPropertiesApplier.apply(options, listOf(properties))

    assertEquals("existing-org", options.distribution.orgSlug)
    assertEquals("existing-project", options.distribution.projectSlug)
    assertEquals("existing-token", options.distribution.orgAuthToken)
    assertEquals("existing-config", options.distribution.buildConfiguration)
  }

  @Test
  fun `applies distribution options from first properties file with values`() {
    val properties1 = Properties()
    val properties2 = Properties()
    properties2.setProperty("io.sentry.distribution.org-slug", "org-from-second")
    properties2.setProperty("io.sentry.distribution.project-slug", "project-from-second")

    val options = SentryOptions()
    DebugMetaPropertiesApplier.apply(options, listOf(properties1, properties2))

    assertEquals("org-from-second", options.distribution.orgSlug)
    assertEquals("project-from-second", options.distribution.projectSlug)
  }

  @Test
  fun `does nothing when properties list is empty`() {
    val options = SentryOptions()
    val originalOrgSlug = options.distribution.orgSlug
    val originalProjectSlug = options.distribution.projectSlug

    DebugMetaPropertiesApplier.apply(options, emptyList())

    assertEquals(originalOrgSlug, options.distribution.orgSlug)
    assertEquals(originalProjectSlug, options.distribution.projectSlug)
  }

  @Test
  fun `does nothing when properties list is null`() {
    val options = SentryOptions()
    val originalOrgSlug = options.distribution.orgSlug
    val originalProjectSlug = options.distribution.projectSlug

    DebugMetaPropertiesApplier.apply(options, null)

    assertEquals(originalOrgSlug, options.distribution.orgSlug)
    assertEquals(originalProjectSlug, options.distribution.projectSlug)
  }

  @Test
  fun `applies buildConfiguration only when it is the only property set`() {
    val properties = Properties()
    properties.setProperty("io.sentry.distribution.build-configuration", "debug")

    val options = SentryOptions()
    DebugMetaPropertiesApplier.apply(options, listOf(properties))

    assertEquals("debug", options.distribution.buildConfiguration)
    assertEquals("", options.distribution.orgSlug)
    assertEquals("", options.distribution.projectSlug)
    assertEquals("", options.distribution.orgAuthToken)
  }

  @Test
  fun `does not apply empty string values`() {
    val properties = Properties()
    properties.setProperty("io.sentry.distribution.org-slug", "")
    properties.setProperty("io.sentry.distribution.project-slug", "")
    properties.setProperty("io.sentry.distribution.org-auth-token", "")
    properties.setProperty("io.sentry.distribution.build-configuration", "")

    val options = SentryOptions()
    DebugMetaPropertiesApplier.apply(options, listOf(properties))

    assertEquals("", options.distribution.orgSlug)
    assertEquals("", options.distribution.projectSlug)
    assertEquals("", options.distribution.orgAuthToken)
    assertNull(options.distribution.buildConfiguration)
  }
}
