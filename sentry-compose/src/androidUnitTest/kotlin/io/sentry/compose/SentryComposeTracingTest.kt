package io.sentry.compose

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.TransactionOptions
import org.junit.After
import org.junit.Before
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
class SentryComposeTracingTest {
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

  @get:Rule(order = 2) val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    Sentry.close()
    Sentry.init(
      { options ->
        options.dsn = "http://key@localhost/proj"
        options.setTracesSampleRate(1.0)
        options.setTransportFactory(NoOpTransportFactory.getInstance())
        options.integrations.clear()
        options.shutdownTimeoutMillis = 0
      },
      true,
    )
  }

  @After
  fun tearDown() {
    Sentry.close()
  }

  @Test
  fun `traced spans use a new root after the previous root finishes`() {
    val tag = mutableStateOf("first")
    val firstTransaction = startTransaction("first")
    composeRule.setContent {
      key(tag.value) {
        SentryTraced(
          tag = tag.value,
          modifier = Modifier.fillMaxSize(),
          enableUserInteractionTracing = false,
        ) {}
      }
    }
    composeRule.waitForIdle()
    drawContent()
    assertTraceHierarchy(firstTransaction, "first")

    firstTransaction.finish()
    val secondTransaction = startTransaction("second")
    composeRule.runOnUiThread { tag.value = "second" }
    composeRule.waitForIdle()
    drawContent()

    assertTraceHierarchy(secondTransaction, "second")
    assertThat(firstTransaction.spans.map { it.description }).doesNotContain("second")
  }

  @Test
  fun `first composition without a root does not prevent later tracing`() {
    val tag = mutableStateOf("without-root")
    composeRule.setContent {
      key(tag.value) {
        SentryTraced(
          tag = tag.value,
          modifier = Modifier.fillMaxSize(),
          enableUserInteractionTracing = false,
        ) {}
      }
    }
    composeRule.waitForIdle()
    drawContent()

    val transaction = startTransaction("with-root")
    composeRule.runOnUiThread { tag.value = "with-root" }
    composeRule.waitForIdle()
    drawContent()

    assertTraceHierarchy(transaction, "with-root")
  }

  @Test
  fun `retained traced node uses the current root after a root change`() {
    val tag = mutableStateOf("first")
    val firstTransaction = startTransaction("first")
    composeRule.setContent {
      SentryTraced(
        tag = tag.value,
        modifier = Modifier.fillMaxSize(),
        enableUserInteractionTracing = false,
      ) {}
    }
    composeRule.waitForIdle()
    drawContent()
    firstTransaction.finish()

    val secondTransaction = startTransaction("second")
    composeRule.runOnUiThread { tag.value = "second" }
    composeRule.waitForIdle()

    val compositionParent =
      secondTransaction.spans.single { it.operation == "ui.compose.composition" }
    val compositionSpans =
      secondTransaction.spans.filter {
        it.operation == "ui.compose" && it.description == "second"
      }
    assertThat(compositionSpans).isNotEmpty()
    compositionSpans.forEach { span ->
      assertThat(span.parentSpanId).isEqualTo(compositionParent.spanContext.spanId)
    }
    assertThat(firstTransaction.spans.map { it.description }).doesNotContain("second")
  }

  @Test
  fun `rendering uses the root active when the node is drawn`() {
    val placeContent = mutableStateOf(false)
    val compositionTransaction = startTransaction("composition")
    composeRule.setContent {
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
    composeRule.waitForIdle()

    assertThat(compositionTransaction.spans.map { it.operation }).contains("ui.compose.composition")
    assertThat(compositionTransaction.spans.map { it.operation })
      .doesNotContain("ui.compose.rendering")
    compositionTransaction.finish()

    val renderingTransaction = startTransaction("rendering")
    composeRule.runOnUiThread { placeContent.value = true }
    composeRule.waitForIdle()
    drawContent()

    val renderingParent =
      renderingTransaction.spans.single { it.operation == "ui.compose.rendering" }
    val renderSpan = renderingTransaction.spans.single { it.operation == "ui.render" }
    assertThat(renderingParent.parentSpanId).isEqualTo(renderingTransaction.spanContext.spanId)
    assertThat(renderSpan.parentSpanId).isEqualTo(renderingParent.spanContext.spanId)
    assertThat(renderSpan.spanContext.origin).isEqualTo("auto.ui.jetpack_compose")
  }

  @Test
  fun `nested traced nodes share the transaction parents`() {
    val transaction = startTransaction("nested")
    composeRule.setContent {
      SentryTraced(
        tag = "outer",
        modifier = Modifier.fillMaxSize(),
        enableUserInteractionTracing = false,
      ) {
        SentryTraced(
          tag = "inner",
          modifier = Modifier.fillMaxSize(),
          enableUserInteractionTracing = false,
        ) {}
      }
    }
    composeRule.waitForIdle()
    drawContent()

    assertTraceHierarchy(transaction, "outer", "inner")
  }

  private fun startTransaction(name: String): SentryTracer =
    Sentry.startTransaction(
      name,
      "test",
      TransactionOptions().apply { isBindToScope = true },
    ) as SentryTracer

  private fun drawContent() {
    composeRule.runOnUiThread {
      val rootView = composeRule.activity.findViewById<View>(android.R.id.content)
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

  private fun assertTraceHierarchy(transaction: SentryTracer, vararg tags: String) {
    val compositionParent = transaction.spans.single { it.operation == "ui.compose.composition" }
    val renderingParent = transaction.spans.single { it.operation == "ui.compose.rendering" }
    val compositionSpans = transaction.spans.filter { it.operation == "ui.compose" }
    val renderingSpans = transaction.spans.filter { it.operation == "ui.render" }

    assertThat(compositionParent.description).isEqualTo("Jetpack Compose Initial Composition")
    assertThat(renderingParent.description).isEqualTo("Jetpack Compose Initial Render")
    assertThat(compositionParent.parentSpanId).isEqualTo(transaction.spanContext.spanId)
    assertThat(renderingParent.parentSpanId).isEqualTo(transaction.spanContext.spanId)
    assertThat(compositionSpans.map(Span::getDescription)).containsAtLeastElementsIn(tags.asList())
    assertThat(renderingSpans.map(Span::getDescription)).containsAtLeastElementsIn(tags.asList())

    val tracedSpans = listOf(compositionParent, renderingParent) + compositionSpans + renderingSpans
    tracedSpans.forEach { span ->
      assertThat(span.spanContext.origin).isEqualTo("auto.ui.jetpack_compose")
    }
    compositionSpans.forEach { span ->
      assertThat(span.parentSpanId).isEqualTo(compositionParent.spanContext.spanId)
    }
    renderingSpans.forEach { span ->
      assertThat(span.parentSpanId).isEqualTo(renderingParent.spanContext.spanId)
    }
  }
}
