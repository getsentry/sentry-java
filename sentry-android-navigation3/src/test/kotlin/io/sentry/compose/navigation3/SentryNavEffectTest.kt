package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.NoOpTransportFactory
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.compose.SentryTraced
import io.sentry.protocol.Contexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryNavEffectTest {

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

  class TestScopes {
    val scopes = mock<IScopes>()
    val scope = mock<IScope>()
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/proj"
        setTracesSampleRate(1.0)
        isEnableScreenTracking = true
      }
    val transactions = mutableListOf<SentryTracer>()

    init {
      whenever(scopes.options).thenReturn(options)
      whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
        .thenAnswer {
          val ctx = it.arguments[0] as TransactionContext
          SentryTracer(ctx, scopes).also { t -> transactions.add(t) }
        }
      whenever(scopes.configureScope(any())).thenAnswer {
        (it.arguments[0] as ScopeCallback).run(scope)
      }
      whenever(scope.contexts).thenReturn(Contexts())
    }
  }

  class RealScopeTestScopes {
    val scopes = mock<IScopes>()
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/proj"
        setTracesSampleRate(1.0)
        isEnableScreenTracking = true
      }
    val scope = Scope(options)
    val transactions = mutableListOf<SentryTracer>()

    init {
      whenever(scopes.options).thenReturn(options)
      whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
        .thenAnswer {
          val ctx = it.arguments[0] as TransactionContext
          SentryTracer(ctx, scopes).also { t -> transactions.add(t) }
        }
      whenever(scopes.configureScope(any())).thenAnswer {
        (it.arguments[0] as ScopeCallback).run(scope)
      }
      whenever(scopes.getSpan()).thenAnswer { scope.span }
    }
  }

  private fun createScopes(): TestScopes = TestScopes()

  private fun createRealScopeTestScopes(): RealScopeTestScopes = RealScopeTestScopes()

  @Test
  fun `initial backstack fires breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }

    composeRule.waitForIdle()

    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `destination launched effect after SentryNavEffect sees navigation transaction`() {
    val fixture = createRealScopeTestScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val observedSpans = mutableListOf<ISpan?>()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      val currentTop = backStack.last()
      LaunchedEffect(currentTop) { observedSpans.add(fixture.scopes.getSpan()) }
    }
    composeRule.waitForIdle()

    assertEquals(1, observedSpans.size)
    assertEquals("/HomeScreen", (observedSpans[0] as SentryTracer).name)

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    assertEquals(2, observedSpans.size)
    assertEquals("/ProfileScreen", (observedSpans[1] as SentryTracer).name)
  }

  @Test
  fun `nav display initial destination body sees navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<ISpan?> = mutableListOf()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      NavDisplay(
        backStack = backStack,
        entryProvider =
          entryProvider { entry<HomeScreen> { observedSpans.add(fixture.scopes.getSpan()) } },
      )
    }
    composeRule.waitForIdle()

    assertEquals("/HomeScreen", (observedSpans.last() as SentryTracer).name)
  }

  @Test
  fun `synchronous content after SentryNavEffect after push sees navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<ISpan?> = mutableListOf()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      val currentTop: Any = backStack.last()
      if (currentTop is ProfileScreen) {
        observedSpans.add(fixture.scopes.getSpan())
      }
    }
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    assertEquals("/ProfileScreen", (observedSpans.single() as SentryTracer).name)
  }

  @Test
  fun `nav display destination body after push sees navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<Pair<String, ISpan?>> = mutableListOf()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      NavDisplay(
        backStack = backStack,
        entryProvider =
          entryProvider {
            entry<HomeScreen> { observedSpans.add("home" to fixture.scopes.getSpan()) }
            entry<ProfileScreen> { observedSpans.add("profile" to fixture.scopes.getSpan()) }
          },
      )
    }
    composeRule.waitForIdle()
    observedSpans.clear()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    val profileSpan: ISpan? = observedSpans.last { it.first == "profile" }.second
    assertEquals("/ProfileScreen", (profileSpan as SentryTracer).name)
  }

  @Test
  fun `nav display destination body after push attaches synchronous child span to navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      NavDisplay(
        backStack = backStack,
        entryProvider =
          entryProvider {
            entry<HomeScreen> {}
            entry<ProfileScreen> {
              val span: ISpan? = fixture.scopes.getSpan()?.startChild("test.sync-work")
              span?.finish()
            }
          },
      )
    }
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    val profileTransaction: SentryTracer = fixture.transactions.last { it.name == "/ProfileScreen" }
    val childSpans: List<Span> = profileTransaction.spans
    assertTrue(childSpans.any { span: Span -> span.operation == "test.sync-work" })
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `SentryTraced spans after push attach to navigation transaction`() {
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val showContent = mutableStateOf(true)
    val appContext = ApplicationProvider.getApplicationContext<Application>()

    SentryAndroid.init(appContext) { options ->
      options.dsn = "http://key@localhost/proj"
      options.setTracesSampleRate(1.0)
      options.setTransportFactory(NoOpTransportFactory.getInstance())
      options.integrations.clear()
      options.shutdownTimeoutMillis = 0
    }

    try {
      composeRule.setContent {
        if (showContent.value) {
          SentryNavEffect(backStack = backStack)

          NavDisplay(
            backStack = backStack,
            entryProvider =
              entryProvider {
                entry<HomeScreen> {}
                entry<ProfileScreen> {
                  SentryTraced(
                    tag = "profile",
                    modifier = Modifier.fillMaxSize(),
                    enableUserInteractionTracing = false,
                  ) {}
                }
              },
          )
        }
      }
      composeRule.waitForIdle()

      backStack.add(ProfileScreen("123"))
      composeRule.waitForIdle()
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

      var activeTransaction: SentryTracer? = null
      Sentry.configureScope { scope -> activeTransaction = scope.transaction as? SentryTracer }
      val transaction = activeTransaction
      assertThat(transaction).isNotNull()
      requireNotNull(transaction)

      assertThat(transaction.name).isEqualTo("/ProfileScreen")
      assertThat(transaction.spans.map { "${it.operation}:${it.description}" })
        .containsAtLeast(
          "ui.compose.composition:Jetpack Compose Initial Composition",
          "ui.compose:profile",
          "ui.compose.rendering:Jetpack Compose Initial Render",
          "ui.render:profile",
        )
      val compositionParent = transaction.spans.last { it.operation == "ui.compose.composition" }
      val renderingParent = transaction.spans.last { it.operation == "ui.compose.rendering" }
      val compositionSpans =
        transaction.spans.filter { it.operation == "ui.compose" && it.description == "profile" }
      val renderingSpans =
        transaction.spans.filter { it.operation == "ui.render" && it.description == "profile" }

      assertThat(compositionParent.parentSpanId).isEqualTo(transaction.spanContext.spanId)
      assertThat(renderingParent.parentSpanId).isEqualTo(transaction.spanContext.spanId)
      assertThat(compositionSpans).isNotEmpty()
      compositionSpans.forEach { span ->
        assertThat(span.parentSpanId).isEqualTo(compositionParent.spanContext.spanId)
      }
      assertThat(renderingSpans).isNotEmpty()
      renderingSpans.forEach { span ->
        assertThat(span.parentSpanId).isEqualTo(renderingParent.spanContext.spanId)
      }
    } finally {
      showContent.value = false
      composeRule.waitForIdle()
      Sentry.close()
    }
  }

  @Test
  fun `nav display destination effects after push see navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<Pair<String, ISpan?>> = mutableListOf()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      NavDisplay(
        backStack = backStack,
        entryProvider =
          entryProvider {
            entry<HomeScreen> {}
            entry<ProfileScreen> {
              remember {
                fixture.scopes.getSpan().also { span: ISpan? ->
                  observedSpans.add("remember" to span)
                }
              }

              DisposableEffect(Unit) {
                observedSpans.add("disposable-effect" to fixture.scopes.getSpan())
                onDispose {}
              }

              SideEffect { observedSpans.add("side-effect" to fixture.scopes.getSpan()) }

              LaunchedEffect(Unit) {
                observedSpans.add("launched-effect" to fixture.scopes.getSpan())
              }
            }
          },
      )
    }
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    val expectedLabels: List<String> =
      listOf("remember", "disposable-effect", "side-effect", "launched-effect")
    for (label: String in expectedLabels) {
      val span: ISpan? = observedSpans.last { it.first == label }.second
      assertEquals("/ProfileScreen", (span as SentryTracer).name)
    }
  }

  @Test
  fun `pushing new key triggers additional breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    verify(fixture.scopes, times(2)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(2))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `removal from composition finishes active transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val showEffect = mutableStateOf(true)

    composeRule.setContent {
      if (showEffect.value) {
        SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
      }
    }
    composeRule.waitForIdle()

    assertEquals(1, fixture.transactions.size)
    assertEquals(false, fixture.transactions[0].isFinished)

    showEffect.value = false
    composeRule.waitForIdle()

    assertEquals(true, fixture.transactions[0].isFinished)
  }

  @Test
  fun `equal inline options skip unrelated recomposition work`() {
    val fixture = createScopes()
    val backStack =
      mutableStateListOf<Any>(
        HomeScreen(),
        ProfileScreen("123"),
        SettingsScreen("privacy"),
      )
    val recomposeTrigger = mutableStateOf(0)
    var nameExtractorCalls = 0
    var argumentsExtractorCalls = 0
    val nameExtractor: (Any) -> String = { key ->
      nameExtractorCalls++
      key::class.simpleName ?: "unknown"
    }
    val argumentsExtractor: (Any) -> Map<String, Any?> = {
      argumentsExtractorCalls++
      emptyMap()
    }

    composeRule.setContent {
      @Suppress("UNUSED_EXPRESSION") recomposeTrigger.value

      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNavOptions(maxCapturedBackStackEntries = 10),
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
      )
    }
    composeRule.waitForIdle()

    assertThat(nameExtractorCalls).isEqualTo(3)
    assertThat(argumentsExtractorCalls).isEqualTo(3)

    recomposeTrigger.value = 1
    composeRule.waitForIdle()
    recomposeTrigger.value = 2
    composeRule.waitForIdle()

    assertThat(nameExtractorCalls).isEqualTo(3)
    assertThat(argumentsExtractorCalls).isEqualTo(3)
  }

  @Test
  fun `state read by stable extractor refreshes current screen`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val routeName = mutableStateOf("home")
    var nameExtractorCalls = 0
    val nameExtractor: (Any) -> String = {
      nameExtractorCalls++
      routeName.value
    }

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = nameExtractor,
      )
    }
    composeRule.waitForIdle()

    routeName.value = "renamed-home"
    composeRule.waitForIdle()

    assertThat(nameExtractorCalls).isEqualTo(2)
    verify(fixture.scope).screen = "/home"
    verify(fixture.scope).screen = "/renamed-home"
    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `updated nameExtractor is used for subsequent navigations`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val useCustomName = mutableStateOf(false)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor =
          if (useCustomName.value) {
            { "custom" }
          } else null,
      )
    }
    composeRule.waitForIdle()

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes).addBreadcrumb(breadcrumbCaptor.capture(), any())
    assertEquals("/HomeScreen", breadcrumbCaptor.lastValue.data["to"])

    useCustomName.value = true
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    verify(fixture.scopes, times(2)).addBreadcrumb(breadcrumbCaptor.capture(), any())
    assertEquals("/custom", breadcrumbCaptor.lastValue.data["to"])
  }

  @Test
  fun `changing enableNavigationBreadcrumbs to false stops breadcrumbs`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val breadcrumbsEnabled = mutableStateOf(true)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNavOptions(enableNavigationBreadcrumbs = breadcrumbsEnabled.value),
      )
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())

    breadcrumbsEnabled.value = false
    composeRule.waitForIdle()

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `changing captureBackStack to false clears navigation context`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val captureBackStack = mutableStateOf(true)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNavOptions(captureBackStack = captureBackStack.value),
      )
    }
    composeRule.waitForIdle()

    verify(fixture.scope).setContexts(any<String>(), any<Any>())

    captureBackStack.value = false
    composeRule.waitForIdle()

    verify(fixture.scope, atLeastOnce()).removeContexts("navigation")
  }

  @Test
  fun `changing enableNavigationTransactions to false stops transactions`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val transactionsEnabled = mutableStateOf(true)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNavOptions(enableNavigationTransactions = transactionsEnabled.value),
      )
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())

    transactionsEnabled.value = false
    composeRule.waitForIdle()
    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `changing maxCapturedBackStackEntries updates navigation context`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen(), ProfileScreen("123"))
    val maxCapturedBackStackEntries = mutableStateOf(10)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNavOptions(maxCapturedBackStackEntries = maxCapturedBackStackEntries.value),
      )
    }
    composeRule.waitForIdle()

    maxCapturedBackStackEntries.value = 1
    composeRule.waitForIdle()

    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope, times(2)).setContexts(any<String>(), valueCaptor.capture())
    @Suppress("UNCHECKED_CAST") val navigation = valueCaptor.lastValue as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") val stack = navigation["backstack"] as List<Map<String, Any?>>

    assertEquals(1, stack.size)
    assertEquals("/ProfileScreen", stack[0]["route"])
  }

  @Test
  fun `empty backstack does not trigger breadcrumb`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }
}
