package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class BackStackKeyTest {

  private data class HomeScreen(val dummy: String = "")

  private data class ProfileScreen(val userId: String)

  @Test
  fun `keys are equal when entry identity and order are equal`() {
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    val first = BackStackKey(listOf(home, profile))
    val second = BackStackKey(listOf(home, profile))

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `keys are not equal when entries are equal by value but not by identity`() {
    val first = BackStackKey(listOf(ProfileScreen("123")))
    val second = BackStackKey(listOf(ProfileScreen("123")))

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `keys are not equal when entry order changes`() {
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    val first = BackStackKey(listOf(home, profile))
    val second = BackStackKey(listOf(profile, home))

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `keys are not equal when stack size changes`() {
    val home = HomeScreen()

    val first = BackStackKey(listOf(home))
    val second = BackStackKey(listOf(home, ProfileScreen("123")))

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `equals does not call entry equals`() {
    val entry = ExplodingEqualityKey()

    val first = BackStackKey(listOf(entry))
    val second = BackStackKey(listOf(entry))

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun `hash code does not call entry hash code`() {
    val entry = ExplodingEqualityKey()

    val first = BackStackKey(listOf(entry))
    val second = BackStackKey(listOf(entry))

    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  private class ExplodingEqualityKey {

    override fun equals(other: Any?): Boolean = error("equals boom")

    override fun hashCode(): Int = error("hashCode boom")
  }
}
