package io.sentry.compose.navigation3

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteResolversTest {

  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  @Test
  fun `getName returns null when no name extractor is configured`() {
    val sut = RouteResolvers<HomeRoute>(nameExtractor = null, argumentsExtractor = null)

    assertNull(sut.getName(HomeRoute()))
  }

  @Test
  fun `getArguments returns null when no arguments extractor is configured`() {
    val sut = RouteResolvers<HomeRoute>(nameExtractor = null, argumentsExtractor = null)

    assertNull(sut.getArguments(HomeRoute()))
  }

  @Test
  fun `getName delegates to the configured extractor`() {
    val route = ProfileRoute("123")
    val sut =
      RouteResolvers<ProfileRoute>(
        nameExtractor = { entry -> "profile-${entry.userId}" },
        argumentsExtractor = null,
      )

    assertEquals("profile-123", sut.getName(route))
  }

  @Test
  fun `getArguments delegates to the configured extractor`() {
    val route = ProfileRoute("123")
    val sut =
      RouteResolvers<ProfileRoute>(
        nameExtractor = null,
        argumentsExtractor = { entry -> mapOf("userId" to entry.userId) },
      )

    assertThat(sut.getArguments(route)).isEqualTo(mapOf("userId" to "123"))
  }

  @Test
  fun `getName hides extractor reads from snapshot observation`() {
    val routeName = mutableStateOf("home")
    val sut =
      RouteResolvers<HomeRoute>(
        nameExtractor = { routeName.value },
        argumentsExtractor = null,
      )

    assertEquals(0, observeReads { sut.getName(HomeRoute()) })
  }

  @Test
  fun `getArguments hides extractor reads from snapshot observation`() {
    val argumentValue = mutableStateOf("123")
    val sut =
      RouteResolvers<HomeRoute>(
        nameExtractor = null,
        argumentsExtractor = { mapOf("userId" to argumentValue.value) },
      )

    assertEquals(0, observeReads { sut.getArguments(HomeRoute()) })
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
