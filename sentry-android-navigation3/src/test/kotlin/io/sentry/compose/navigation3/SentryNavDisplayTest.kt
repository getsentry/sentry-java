package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.Contexts
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryNavDisplayTest {

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

  @Composable
  private fun <T : Any> TestSentryNavDisplay(
    backStack: List<T>,
    scopes: IScopes,
    options: SentryNav3Options = SentryNav3Options(),
    nameExtractor: ((T) -> String)? = null,
    argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
  ) {
    SentryNavDisplay(
      backStack = backStack,
      scopes = scopes,
      options = options,
      nameExtractor = nameExtractor,
      argumentsExtractor = argumentsExtractor,
      entryProvider = { key -> NavEntry(key) {} },
    )
  }

  @Test
  fun `initial backstack fires breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      TestSentryNavDisplay(backStack = backStack, scopes = fixture.scopes)
    }

    composeRule.waitForIdle()

    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `initial destination body is composed before navigation transaction is bound`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<ISpan?> = mutableListOf()

    composeRule.setContent {
      SentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        entryProvider =
          entryProvider {
            entry<HomeScreen> { observedSpans.add(fixture.scopes.getSpan()) }
          },
      )
    }
    composeRule.waitForIdle()

    assertThat(observedSpans.first()).isNull()
  }

  @Test
  fun `destination effects after push see navigation transaction`() {
    val fixture: RealScopeTestScopes = createRealScopeTestScopes()
    val backStack: SnapshotStateList<Any> = mutableStateListOf(HomeScreen())
    val observedSpans: MutableList<Pair<String, ISpan?>> = mutableListOf()

    composeRule.setContent {
      SentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        entryProvider =
          entryProvider {
            entry<HomeScreen> {}
            entry<ProfileScreen> {
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

    val effectLabels: List<String> = listOf("disposable-effect", "side-effect", "launched-effect")
    for (label: String in effectLabels) {
      val span: ISpan? = observedSpans.last { it.first == label }.second
      assertEquals("/ProfileScreen", (span as SentryTracer).name)
    }
  }

  @Test
  fun `pushing new key triggers additional breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      TestSentryNavDisplay(backStack = backStack, scopes = fixture.scopes)
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
        TestSentryNavDisplay(backStack = backStack, scopes = fixture.scopes)
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
  fun `inline lambdas do not cause state holder churn on recomposition`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val recomposeTrigger = mutableStateOf(0)
    var destinationCompositions = 0

    composeRule.setContent {
      @Suppress("UNUSED_EXPRESSION") recomposeTrigger.value

      SentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = { key -> key::class.simpleName ?: "unknown" },
        argumentsExtractor = { _ -> emptyMap() },
        entryProvider = { key -> NavEntry(key) { destinationCompositions++ } },
      )
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    assertThat(destinationCompositions).isEqualTo(1)

    recomposeTrigger.value = 1
    composeRule.waitForIdle()
    recomposeTrigger.value = 2
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    assertThat(destinationCompositions).isEqualTo(1)
  }

  @Test
  fun `updated nameExtractor is used for subsequent navigations`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val useCustomName = mutableStateOf(false)

    composeRule.setContent {
      TestSentryNavDisplay(
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
      TestSentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        options =
          SentryNav3Options().apply {
            enableNavigationBreadcrumbs = breadcrumbsEnabled.value
          },
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
      TestSentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        options = SentryNav3Options().apply { this.captureBackStack = captureBackStack.value },
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
      TestSentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        options =
          SentryNav3Options().apply {
            enableNavigationTransactions = transactionsEnabled.value
          },
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
      TestSentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        options =
          SentryNav3Options().apply {
            this.maxCapturedBackStackEntries = maxCapturedBackStackEntries.value
          },
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
  fun `NavBackStack triggers navigation instrumentation`() {
    val fixture = createScopes()
    val backStack = NavBackStack<TestNavKey>(TestNavKey("home"))

    composeRule.setContent {
      TestSentryNavDisplay(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = { it.name },
      )
    }
    composeRule.waitForIdle()

    backStack.add(TestNavKey("profile"))
    composeRule.waitForIdle()

    val transactionCaptor = argumentCaptor<TransactionContext>()
    verify(fixture.scopes, times(2))
      .startTransaction(transactionCaptor.capture(), any<TransactionOptions>())
    assertThat(transactionCaptor.lastValue.name).isEqualTo("/profile")
  }

  @Test
  fun `immutable backstack replacements preserve navigation history`() {
    val fixture = createScopes()
    val backStack = mutableStateOf<List<Any>>(listOf(HomeScreen()))

    composeRule.setContent {
      TestSentryNavDisplay(backStack = backStack.value, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    val profile = ProfileScreen("123")
    backStack.value = backStack.value + profile
    composeRule.waitForIdle()

    backStack.value = listOf(HomeScreen("replacement"), profile)
    composeRule.waitForIdle()

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(breadcrumbCaptor.capture(), any())
    assertThat(breadcrumbCaptor.lastValue.data["from"]).isEqualTo("/HomeScreen")
    assertThat(breadcrumbCaptor.lastValue.data["to"]).isEqualTo("/ProfileScreen")
    verify(fixture.scopes, times(2))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }
}

private data class TestNavKey(val name: String) : NavKey
