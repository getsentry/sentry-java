package io.sentry.android.buddy.ui.common

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.model.ActionStatus
import io.sentry.android.buddy.model.RecommendationAction
import kotlin.test.Test

class BuddyRecommendationActionsTest {
  @Test
  fun `an action with a link opens it instead of asking for execution`() {
    val executed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val models =
      listOf(action(id = "act-1", link = "https://sentry.io/dashboards/1"))
        .toActionModels(onExecute = { executed += it }, onOpenLink = { opened += it })

    models.single().onClick()

    assertThat(opened).containsExactly("https://sentry.io/dashboards/1")
    assertThat(executed).isEmpty()
  }

  @Test
  fun `an action without a link asks for execution`() {
    val executed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val models =
      listOf(action(id = "act-1"))
        .toActionModels(onExecute = { executed += it }, onOpenLink = { opened += it })

    models.single().onClick()

    assertThat(executed).containsExactly("act-1")
    assertThat(opened).isEmpty()
  }

  @Test
  fun `every action keeps its own label and identity`() {
    val models =
      listOf(action(id = "act-1", label = "Open the dashboard", link = "https://sentry.io/d/1"))
        .plus(action(id = "act-2", label = "Add the spans"))
        .toActionModels(onExecute = {}, onOpenLink = {})

    assertThat(models.map { it.id }).containsExactly("act-1", "act-2").inOrder()
    assertThat(models.map { it.label })
      .containsExactly("Open the dashboard", "Add the spans")
      .inOrder()
  }

  @Test
  fun `executed actions are not rendered as executable buttons`() {
    val models =
      listOf(action(id = "act-1", status = ActionStatus.EXECUTED))
        .toActionModels(onExecute = {}, onOpenLink = {})

    assertThat(models).isEmpty()
  }

  private fun action(
    id: String,
    label: String = "Do the thing",
    link: String? = null,
    status: ActionStatus = ActionStatus.OPEN,
  ): RecommendationAction =
    RecommendationAction(
      id = id,
      actionLabel = label,
      actionableForSeer = link == null,
      description = "…",
      link = link,
      status = status,
    )
}
