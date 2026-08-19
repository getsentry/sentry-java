package io.sentry.android.buddy.ui.userflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.model.FlowAction
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class BuddyFlowActionsTest {
  @Test
  fun `non executable flow actions are rendered disabled`() {
    val models =
      listOf(flowAction(actionableForSeer = false))
        .toPermaActionModels(
          context = RuntimeEnvironment.getApplication(),
          recordingJson = "{}",
          onExecuteFlowAction = { _, _ -> },
          onOpenUrl = { _, _ -> },
        )

    assertThat(models.single().enabled).isFalse()
  }

  @Test
  fun `Seer flow actions execute through the bridge`() {
    val executed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val context = RuntimeEnvironment.getApplication()
    val models =
      listOf(flowAction(actionableForSeer = true))
        .toPermaActionModels(
          context = context,
          recordingJson = "{}",
          onExecuteFlowAction = { _, actionId -> executed += actionId },
          onOpenUrl = { _, url -> opened += url },
        )

    models.single().onClick()

    assertThat(models.single().enabled).isTrue()
    assertThat(executed).containsExactly("generate-dashboard")
    assertThat(opened).isEmpty()
  }

  @Test
  fun `flow actions with links open directly`() {
    val executed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val models =
      listOf(flowAction(link = "https://sentry.io/dashboards/1"))
        .toPermaActionModels(
          context = RuntimeEnvironment.getApplication(),
          recordingJson = "{}",
          onExecuteFlowAction = { _, actionId -> executed += actionId },
          onOpenUrl = { _, url -> opened += url },
        )

    models.single().onClick()

    assertThat(models.single().enabled).isTrue()
    assertThat(opened).containsExactly("https://sentry.io/dashboards/1")
    assertThat(executed).isEmpty()
  }

  private fun flowAction(
    actionableForSeer: Boolean = false,
    link: String? = null,
  ): FlowAction =
    FlowAction(
      id = "generate-dashboard",
      actionLabel = "Dashboard",
      description = "Draft a dashboard.",
      actionableForSeer = actionableForSeer,
      link = link,
    )
}
