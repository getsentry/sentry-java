package io.sentry.compose.navigation3

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForwardingBackStackEntryMapperTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String)

  @Test
  fun `mapper returns name and arguments when present`() {
    val screen = ProfileScreen("123")
    val sut =
      ForwardingBackStackEntryMapper({
        BackStackEntryMapper<ProfileScreen> { entry ->
          SentryBackStackEntry("profile-${entry.userId}", mapOf("userId" to entry.userId))
        }
      })

    assertThat(sut.map(screen))
      .isEqualTo(SentryBackStackEntry("profile-123", mapOf("userId" to "123")))
  }

  @Test
  fun `mapper returns entry without allocating arguments map when arguments are omitted`() {
    val sut = ForwardingBackStackEntryMapper {
      BackStackEntryMapper<HomeScreen> { SentryBackStackEntry(it.id) }
    }

    assertThat(sut.map(HomeScreen())).isEqualTo(SentryBackStackEntry("home"))
    assertThat(sut.map(HomeScreen()).arguments).isNull()
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
