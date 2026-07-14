package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class KeyValueCollectionBehaviorTest {
  @Test
  fun `off has no terms`() {
    val behavior = KeyValueCollectionBehavior.off()

    assertThat(behavior.mode).isEqualTo(KeyValueCollectionBehavior.Mode.OFF)
    assertThat(behavior.terms).isEmpty()
  }

  @Test
  fun `deny list stores terms in order`() {
    val behavior = KeyValueCollectionBehavior.denyList("token", "session")

    assertThat(behavior.mode).isEqualTo(KeyValueCollectionBehavior.Mode.DENY_LIST)
    assertThat(behavior.terms).containsExactly("token", "session").inOrder()
  }

  @Test
  fun `allow list can be empty`() {
    val behavior = KeyValueCollectionBehavior.allowList()

    assertThat(behavior.mode).isEqualTo(KeyValueCollectionBehavior.Mode.ALLOW_LIST)
    assertThat(behavior.terms).isEmpty()
  }

  @Test
  fun `terms are copied and immutable`() {
    val terms = arrayOf("token")
    val behavior = KeyValueCollectionBehavior.denyList(*terms)

    terms[0] = "password"

    assertThat(behavior.terms).containsExactly("token")
  }

  @Test
  fun `equal behaviors have equal hash codes`() {
    val first = KeyValueCollectionBehavior.allowList("language", "theme")
    val second = KeyValueCollectionBehavior.allowList("language", "theme")

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }
}
