package io.sentry.compose

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.TransactionOptions
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SentryTracedTest {

  // workaround for robolectric tests with composeRule taken
  // from https://github.com/robolectric/robolectric/pull/4736#issuecomment-1831034882
  @get:Rule(order = 1)
  val addActivityToRobolectricRule =
    object : TestWatcher() {
      override fun starting(description: Description?) {
        super.starting(description)
        val appContext: Application = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(appContext.packageManager)
          .addActivityIfNotPresent(
            ComponentName(appContext.packageName, ComponentActivity::class.java.name)
          )
      }
    }

  @get:Rule(order = 2) val rule = createAndroidComposeRule<ComponentActivity>()

  @After
  fun tearDown() {
    rule.runOnUiThread { Sentry.close() }
    Sentry.close()
  }

  @Test
  fun `records a composition span for the initial composition`() {
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent { SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp)) } }
    tx.waitForSpanCount(OP_COMPOSE, 1)

    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(tx.countSpans(OP_COMPOSE)).isEqualTo(1)
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)
  }

  @Test
  fun `falls back to the current transaction when no owner span is provided`() {
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent { SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp)) } }

    tx.waitForSpanCount(OP_COMPOSE, 1)

    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(tx.countSpans(OP_COMPOSE)).isEqualTo(1)
  }

  @Test
  fun `renders content without spans when no live owner span is available`() {
    rule.runOnUiThread { Sentry.close() }

    rule.setContent {
      SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp).testTag("content")) }
    }
    rule.waitForIdle()

    rule.onNodeWithTag("content").assertExists()
  }

  @Test
  fun `renders content without spans when the current transaction is finished`() {
    val tx = initSentryAndStartTransaction("tx")
    rule.runOnUiThread { tx.finish() }

    rule.setContent {
      SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp).testTag("content")) }
    }
    rule.waitForIdle()

    rule.onNodeWithTag("content").assertExists()
    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(0)
    assertThat(tx.countSpans(OP_COMPOSE)).isEqualTo(0)
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)
    assertThat(tx.countSpans(OP_RENDER)).isEqualTo(0)
  }

  @Test
  fun `sibling traced composables with the same owner share the composition parent`() {
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent {
      Column {
        SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp)) }
        SentryTraced(tag = "add_to_cart_button") { Box(Modifier.size(1.dp)) }
      }
    }

    tx.waitForSpanCount(OP_COMPOSE, 2)

    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)
  }

  @Test
  fun `sibling traced composables with the same owner share the render parent`() {
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent {
      Column {
        SentryTraced(tag = "product_info") { Box(Modifier.size(1.dp)) }
        SentryTraced(tag = "add_to_cart_button") { Box(Modifier.size(1.dp)) }
      }
    }
    tx.waitForSpanCount(OP_COMPOSE, 2)

    drawContent()
    tx.waitForSpanCount(OP_RENDER, 2)

    val renderParent = tx.singleSpan(OP_PARENT_RENDER)
    val renderSpans = tx.spans.filter { it.operation == OP_RENDER }
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(1)
    assertThat(renderSpans).hasSize(2)
    assertThat(renderSpans.map { it.parentSpanId })
      .containsExactly(
        renderParent.spanContext.spanId,
        renderParent.spanContext.spanId,
      )
  }

  @Test
  fun `records at most one composition span and one render span per owner span`() {
    var step by mutableStateOf(0)
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent {
      val currentStep = step
      SentryTraced(tag = "product_info") {
        Box(Modifier.size((currentStep + 1).dp).testTag("content-$currentStep"))
      }
    }
    tx.waitForSpanCount(OP_COMPOSE, 1)
    drawContent()
    tx.waitForSpanCount(OP_RENDER, 1)

    rule.runOnIdle { step = 1 }
    rule.waitForIdle()
    drawContent()
    rule.waitForIdle()

    rule.onNodeWithTag("content-1").assertExists()
    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(tx.countSpans(OP_COMPOSE)).isEqualTo(1)
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(1)
    assertThat(tx.countSpans(OP_RENDER)).isEqualTo(1)
  }

  @Test
  fun `preserves content state after tracing completes`() {
    var step by mutableStateOf(0)
    var rememberedInstanceCount = 0
    var disposeCount = 0
    var rememberedState: Any? = null
    val tx = initSentryAndStartTransaction("tx")

    rule.setContent {
      val currentStep = step
      SentryTraced(tag = "product_info") {
        val state = remember {
          rememberedInstanceCount++
          Any()
        }
        DisposableEffect(Unit) { onDispose { disposeCount++ } }
        rememberedState = state
        Box(Modifier.size((currentStep + 1).dp).testTag("content-$currentStep"))
      }
    }
    tx.waitForSpanCount(OP_COMPOSE, 1)
    drawContent()
    tx.waitForSpanCount(OP_RENDER, 1)
    val firstRememberedState = rememberedState

    rule.runOnIdle { step = 1 }
    rule.waitForIdle()
    drawContent()
    rule.waitForIdle()

    rule.onNodeWithTag("content-1").assertExists()
    assertThat(rememberedInstanceCount).isEqualTo(1)
    assertThat(disposeCount).isEqualTo(0)
    assertThat(rememberedState).isSameInstanceAs(firstRememberedState)
  }

  @Test
  fun `starts recording once an owner span becomes available`() {
    var step by mutableStateOf(0)

    rule.runOnUiThread { Sentry.close() }
    rule.setContent {
      val currentStep = step
      key(currentStep) {
        SentryTraced(tag = "late-transaction-$currentStep") { Box(Modifier.size(1.dp)) }
      }
    }
    rule.waitForIdle()

    val tx = initSentryAndStartTransaction("tx")
    rule.runOnIdle { step = 1 }
    rule.waitForIdle()

    tx.waitForSpanCount(OP_COMPOSE, 1)

    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(tx.countSpans(OP_COMPOSE)).isEqualTo(1)
  }

  @Test
  fun `records spans under replacement owner after previous owner finishes`() {
    var step by mutableStateOf(0)
    val firstTx = initSentryAndStartTransaction("first-tx")

    rule.setContent {
      val currentStep = step
      key(currentStep) {
        SentryTraced(tag = "transaction-$currentStep") { Box(Modifier.size(1.dp)) }
      }
    }
    firstTx.waitForSpanCount(OP_COMPOSE, 1)

    lateinit var secondTx: ITransaction
    rule.runOnUiThread {
      firstTx.finish()
      secondTx = startBoundTransaction("second-tx")
    }
    rule.runOnIdle { step = 1 }
    rule.waitForIdle()

    secondTx.waitForSpanCount(OP_COMPOSE, 1)

    assertThat(firstTx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_COMPOSE)).isEqualTo(1)
  }

  @Test
  fun `records a new span group when the owner span changes for the same composable node`() {
    var step by mutableStateOf(0)
    val firstTx = initSentryAndStartTransaction("first-tx")

    rule.setContent {
      val currentStep = step
      SentryTraced(tag = "transaction", modifier = Modifier.testTag("content-$currentStep")) {
        Box(Modifier.size((currentStep + 1).dp))
      }
    }
    firstTx.waitForSpanCount(OP_COMPOSE, 1)
    drawContent()
    firstTx.waitForSpanCount(OP_RENDER, 1)

    lateinit var secondTx: ITransaction
    rule.runOnUiThread {
      firstTx.finish()
      secondTx = startBoundTransaction("second-tx")
    }
    rule.runOnIdle { step = 1 }
    rule.waitForIdle()

    secondTx.waitForSpanCount(OP_COMPOSE, 1)
    drawContent()
    secondTx.waitForSpanCount(OP_RENDER, 1)

    rule.onNodeWithTag("content-1").assertExists()
    assertThat(firstTx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(firstTx.countSpans(OP_PARENT_RENDER)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_COMPOSE)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_PARENT_RENDER)).isEqualTo(1)
    assertThat(secondTx.countSpans(OP_RENDER)).isEqualTo(1)
  }

  @Test
  fun `failed composition does not emit parent spans`() {
    val tx = initSentryAndStartTransaction("tx")

    assertFailsWith<IllegalStateException> {
      rule.setContent { SentryTraced(tag = "throws") { error("boom") } }
    }

    assertThat(tx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(0)
    assertThat(tx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)
  }

  @Test
  fun `render spans use the owner captured during composition`() {
    val placeContent = mutableStateOf(false)
    val compositionTx = initSentryAndStartTransaction("composition-tx")

    rule.setContent {
      Layout(
        content = {
          SentryTraced(
            tag = "traced",
            modifier = Modifier.fillMaxSize(),
            enableUserInteractionTracing = false,
          ) {}
        }
      ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
          if (placeContent.value) {
            placeable.place(0, 0)
          }
        }
      }
    }
    rule.waitForIdle()

    assertThat(compositionTx.countSpans(OP_PARENT_COMPOSITION)).isEqualTo(1)
    assertThat(compositionTx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)

    lateinit var renderingTx: ITransaction
    rule.runOnUiThread {
      renderingTx = startBoundTransaction("rendering-tx")
      placeContent.value = true
    }
    rule.waitForIdle()
    drawContent()

    val renderingParent = compositionTx.spans.single { it.operation == OP_PARENT_RENDER }
    val renderSpan = compositionTx.spans.single { it.operation == OP_RENDER }
    assertThat(renderingParent.parentSpanId).isEqualTo(compositionTx.spanContext.spanId)
    assertThat(renderSpan.parentSpanId).isEqualTo(renderingParent.spanContext.spanId)
    assertThat(renderSpan.spanContext.origin).isEqualTo(OP_TRACE_ORIGIN)
    assertThat(renderingTx.countSpans(OP_PARENT_RENDER)).isEqualTo(0)
    assertThat(renderingTx.countSpans(OP_RENDER)).isEqualTo(0)
  }

  private fun initSentryAndStartTransaction(name: String): ITransaction {
    lateinit var tx: ITransaction
    rule.runOnUiThread {
      Sentry.init(
        { options: SentryOptions ->
          options.dsn = "https://key@sentry.io/proj"
          options.tracesSampleRate = 1.0
        },
        true,
      )
      tx = startBoundTransaction(name)
      var boundTransaction: ITransaction? = null
      Sentry.configureScope { boundTransaction = it.transaction }
      assertThat(boundTransaction).isSameInstanceAs(tx)
    }
    return tx
  }

  private fun startBoundTransaction(name: String): ITransaction {
    val transaction =
      Sentry.startTransaction(name, "test", TransactionOptions().apply { isBindToScope = true })
    Sentry.configureScope { it.setTransaction(transaction) }
    return transaction
  }

  private fun ITransaction.waitForSpanCount(operation: String, count: Int) {
    rule.waitUntil(timeoutMillis = 5_000) { countSpans(operation) >= count }
  }

  private fun ITransaction.countSpans(operation: String): Int = spans.count {
    it.operation == operation
  }

  private fun ITransaction.singleSpan(operation: String): ISpan = spans.single {
    it.operation == operation
  }

  private fun drawContent() {
    rule.runOnUiThread {
      val rootView = rule.activity.findViewById<View>(android.R.id.content)
      val bitmap =
        Bitmap.createBitmap(
          rootView.width.coerceAtLeast(1),
          rootView.height.coerceAtLeast(1),
          Bitmap.Config.ARGB_8888,
        )
      try {
        rootView.draw(Canvas(bitmap))
      } finally {
        bitmap.recycle()
      }
    }
  }

  private companion object {
    private const val OP_PARENT_COMPOSITION = "ui.compose.composition"
    private const val OP_COMPOSE = "ui.compose"
    private const val OP_PARENT_RENDER = "ui.compose.rendering"
    private const val OP_RENDER = "ui.render"
    private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"
  }
}
