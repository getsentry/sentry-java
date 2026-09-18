package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SentryNavOptionsTest {

  @Test
  fun `accepts positive max captured backstack entries`() {
    val options = SentryNavOptions(maxCapturedBackStackEntries = 1)

    assertThat(options.maxCapturedBackStackEntries).isEqualTo(1)
  }

  @Test
  fun `accepts zero max captured backstack entries`() {
    val options = SentryNavOptions(maxCapturedBackStackEntries = 0)

    assertThat(options.maxCapturedBackStackEntries).isEqualTo(0)
  }

  @Test
  fun `rejects negative max captured backstack entries`() {
    val exception =
      assertFailsWith<IllegalArgumentException> {
        SentryNavOptions(maxCapturedBackStackEntries = -1)
      }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("maxCapturedBackStackEntries must be non-negative, was -1")
  }

  @Test
  fun `equal instances share the same hash code`() {
    val first = SentryNavOptions()
    val second = SentryNavOptions()

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `equals and hash code include every property`() {
    val base = SentryNavOptions()
    val instanceFields =
      SentryNavOptions::class
        .java
        .declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }

    assertThat(propertyMutators.keys).containsExactlyElementsIn(instanceFields)

    propertyMutators.forEach { (propertyName, mutate) ->
      val changed = mutate(base)

      assertThat(changed).isNotEqualTo(base)
      assertThat(changed.hashCode()).isNotEqualTo(base.hashCode())
      assertThat(propertyName).isIn(instanceFields)
    }
  }

  private companion object {
    val propertyMutators =
      mapOf<String, (SentryNavOptions) -> SentryNavOptions>(
        "enableNavigationBreadcrumbs" to
          { options ->
            SentryNavOptions(
              enableNavigationBreadcrumbs = !options.enableNavigationBreadcrumbs,
              enableNavigationTransactions = options.enableNavigationTransactions,
              captureBackStack = options.captureBackStack,
              maxCapturedBackStackEntries = options.maxCapturedBackStackEntries,
            )
          },
        "enableNavigationTransactions" to
          { options ->
            SentryNavOptions(
              enableNavigationBreadcrumbs = options.enableNavigationBreadcrumbs,
              enableNavigationTransactions = !options.enableNavigationTransactions,
              captureBackStack = options.captureBackStack,
              maxCapturedBackStackEntries = options.maxCapturedBackStackEntries,
            )
          },
        "captureBackStack" to
          { options ->
            SentryNavOptions(
              enableNavigationBreadcrumbs = options.enableNavigationBreadcrumbs,
              enableNavigationTransactions = options.enableNavigationTransactions,
              captureBackStack = !options.captureBackStack,
              maxCapturedBackStackEntries = options.maxCapturedBackStackEntries,
            )
          },
        "maxCapturedBackStackEntries" to
          { options ->
            SentryNavOptions(
              enableNavigationBreadcrumbs = options.enableNavigationBreadcrumbs,
              enableNavigationTransactions = options.enableNavigationTransactions,
              captureBackStack = options.captureBackStack,
              maxCapturedBackStackEntries = options.maxCapturedBackStackEntries + 1,
            )
          },
      )
  }
}
