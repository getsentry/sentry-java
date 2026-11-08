package io.sentry.compose.navigation3.internal.backstack

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import org.junit.Test

class ForwardingBackStackEntryMapperTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String)

  @Test
  fun `mapper returns name and arguments when present`() {
    val screen = ProfileScreen("123")
    val sut =
      ForwardingBackStackEntryMapper(
        {
          BackStackEntryMapper<ProfileScreen> { entry ->
            SentryBackStackEntry("profile-${entry.userId}", mapOf("userId" to entry.userId))
          }
        },
      )

    assertThat(sut.map(screen))
      .isEqualTo(SentryBackStackEntry("profile-123", mapOf("userId" to "123")))
  }

  @Test
  fun `map() - forwards to the mapper current when map is called`() {
    var mapper = BackStackEntryMapper<HomeScreen> { SentryBackStackEntry("first") }
    val sut = ForwardingBackStackEntryMapper { mapper }
    val first = sut.map(HomeScreen())
    mapper = BackStackEntryMapper { SentryBackStackEntry("second") }

    val second = sut.map(HomeScreen())

    assertThat(first).isEqualTo(SentryBackStackEntry("first"))
    assertThat(second).isEqualTo(SentryBackStackEntry("second"))
  }

  @Test
  fun `mapper forwards null results`() {
    val sut = ForwardingBackStackEntryMapper<HomeScreen> { BackStackEntryMapper { null } }
    assertThat(sut.map(HomeScreen())).isNull()
  }

  @Test
  fun `mapper hides reads from snapshot observation`() {
    val name = mutableStateOf("home")
    val sut = ForwardingBackStackEntryMapper {
      BackStackEntryMapper<HomeScreen> { SentryBackStackEntry(name.value) }
    }

    assertThat(observeReads { sut.map(HomeScreen()) }).isEqualTo(0)
  }

  private fun observeReads(block: () -> Unit): Int {
    var reads = 0
    val snapshot = Snapshot.takeSnapshot(readObserver = { reads++ })
    try {
      snapshot.enter(block)
    } finally {
      snapshot.dispose()
    }
    return reads
  }
}
