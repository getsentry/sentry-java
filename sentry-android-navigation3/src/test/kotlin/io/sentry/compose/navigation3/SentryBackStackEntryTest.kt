package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Modifier
import org.junit.Test

class SentryBackStackEntryTest {

  @Test
  fun `equals follows value semantics`() {
    val entry = SentryBackStackEntry("profile", mapOf("userId" to "123"))
    val structurallyEqual = SentryBackStackEntry("profile", mapOf("userId" to "123"))
    val structurallyDifferent = SentryBackStackEntry("settings")

    assertThat(entry).isEqualTo(entry)
    assertThat(entry).isEqualTo(structurallyEqual)
    assertThat(entry).isNotEqualTo(structurallyDifferent)
    assertThat(entry).isNotEqualTo(null)
    assertThat(entry).isNotEqualTo("profile")
  }

  @Test
  fun `equals includes every property`() {
    val base = SentryBackStackEntry("profile", mapOf("userId" to "123"))
    val instanceFields =
      SentryBackStackEntry::class
        .java
        .declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }

    assertThat(propertyMutators.keys).containsExactlyElementsIn(instanceFields)

    propertyMutators.forEach { (propertyName, mutate) ->
      val changed = mutate(base)

      assertThat(changed).isNotEqualTo(base)
      assertThat(propertyName).isIn(instanceFields)
    }
  }

  @Test
  fun `equal instances share the same hash code`() {
    val first = SentryBackStackEntry("profile", mapOf("userId" to "123"))
    val second = SentryBackStackEntry("profile", mapOf("userId" to "123"))

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `hash code includes every property`() {
    val base = SentryBackStackEntry("profile", mapOf("userId" to "123"))
    val instanceFields =
      SentryBackStackEntry::class
        .java
        .declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }

    assertThat(propertyMutators.keys).containsExactlyElementsIn(instanceFields)

    propertyMutators.forEach { (propertyName, mutate) ->
      val changed = mutate(base)

      assertThat(changed.hashCode()).isNotEqualTo(base.hashCode())
      assertThat(propertyName).isIn(instanceFields)
    }
  }

  @Test
  fun `toString includes the name but redacts arguments`() {
    val info = SentryBackStackEntry("profile", mapOf("userId" to "123"))

    assertThat(info.toString()).isEqualTo("SentryBackStackEntry(name=profile)")
    assertThat(info.toString()).doesNotContain("userId")
    assertThat(info.toString()).doesNotContain("123")
  }

  private companion object {
    val propertyMutators =
      mapOf<String, (SentryBackStackEntry) -> SentryBackStackEntry>(
        "name" to { entry -> SentryBackStackEntry("settings", entry.arguments) },
        "arguments" to
          { entry ->
            SentryBackStackEntry(entry.name, mapOf("userId" to "456"))
          },
      )
  }
}
