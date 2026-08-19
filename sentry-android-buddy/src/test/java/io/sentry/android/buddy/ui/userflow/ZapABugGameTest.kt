package io.sentry.android.buddy.ui.userflow

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.test.Test

private const val TEST_HOLE_COUNT = 9

class ZapABugGameTest {
  @Test
  fun `zapping a bug tracks the targeted cell for the laser`() {
    val board =
      ZapBoard(
        holes =
          List(TEST_HOLE_COUNT) { index -> if (index == 4) ZapHole.Bug(0L) else ZapHole.Empty },
        nextSpawnAtMs = Long.MAX_VALUE,
      )

    val zapped = board.zap(index = 4, nowMs = 120L)

    assertThat(zapped.hits).isEqualTo(1)
    assertThat(zapped.misses).isEqualTo(0)
    assertThat(zapped.lastZapIndex).isEqualTo(4)
    assertThat(zapped.lastZapAtMs).isEqualTo(120L)
    assertThat(zapped.holes[4]).isEqualTo(ZapHole.Zapped(120L))
  }

  @Test
  fun `advance clears the laser target when the flash window expires`() {
    val board =
      ZapBoard(
        holes =
          List(TEST_HOLE_COUNT) { index ->
            if (index == 2) ZapHole.Zapped(100L) else ZapHole.Empty
          },
        nextSpawnAtMs = Long.MAX_VALUE,
        lastZapAtMs = 100L,
        lastZapIndex = 2,
      )

    val advanced = board.advance(nowMs = 301L, random = Random(1))

    assertThat(advanced.lastZapAtMs).isNull()
    assertThat(advanced.lastZapIndex).isNull()
    assertThat(advanced.holes[2]).isEqualTo(ZapHole.Empty)
  }

  @Test
  fun `advance counts bugs that time out as misses`() {
    val board =
      ZapBoard(
        holes =
          List(TEST_HOLE_COUNT) { index -> if (index == 3) ZapHole.Bug(0L) else ZapHole.Empty },
        nextSpawnAtMs = Long.MAX_VALUE,
      )

    val advanced = board.advance(nowMs = 1_401L, random = Random(1))

    assertThat(advanced.hits).isEqualTo(0)
    assertThat(advanced.misses).isEqualTo(1)
    assertThat(advanced.holes[3]).isEqualTo(ZapHole.Empty)
  }
}
