package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

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

  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  private class Fixture {
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/proj"
        setTracesSampleRate(1.0)
        isEnableScreenTracking = true
        setLogger(logger)
        idleTimeout = null
        deadlineTimeout = 0
      }
    val scope = Scope(options)
    val scopes = mock<IScopes>()
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val transactions = mutableListOf<SentryTracer>()

    init {
      whenever(scopes.options).thenReturn(options)
      whenever(scopes.getSpan()).thenAnswer { scope.span }
      doAnswer {
          (it.arguments[0] as ScopeCallback).run(scope)
          null
        }
        .whenever(scopes)
        .configureScope(any())
      doAnswer {
          val transactionContext = it.arguments[0] as TransactionContext
          val transactionOptions = it.arguments[1] as TransactionOptions
          SentryTracer(transactionContext, scopes, transactionOptions).also(transactions::add)
        }
        .whenever(scopes)
        .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
      doAnswer {
          breadcrumbs += it.arguments[0] as Breadcrumb
          null
        }
        .whenever(scopes)
        .addBreadcrumb(any<Breadcrumb>(), any<Hint>())
    }
  }

  @Test
  fun `initial composition emits Sentry data for the top entry and a back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }

    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions.single().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `pushing a new top entry emits Sentry data for that entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions).hasSize(2)
    assertThat(fixture.transactions.last().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/ProfileRoute"), mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `popping the top entry emits Sentry data for the new top entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf(HomeRoute(), ProfileRoute("123"))

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions).hasSize(2)
    assertThat(fixture.transactions.last().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `replacing the back stack emits Sentry data for the new top entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf(HomeRoute(), ProfileRoute("123"))

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      backStack.clear()
      backStack.add(ProfileRoute("999"))
      backStack.add(HomeRoute("replacement"))
    }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions).hasSize(2)
    assertThat(fixture.transactions.last().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/HomeRoute"), mapOf("route" to "/ProfileRoute")))
  }

  @Test
  fun `changing non-top entries does not re-emit top-entry Sentry data but does emit the new back stack copy`() {
    val fixture = Fixture()
    val home = HomeRoute()
    val profile = ProfileRoute("123")
    val backStack = mutableStateListOf<Any>(home, profile)

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack.add(1, HomeRoute("inserted")) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.transactions.single().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to "/ProfileRoute"),
          mapOf("route" to "/HomeRoute"),
          mapOf("route" to "/HomeRoute"),
        )
      )
  }

  @Test
  fun `unrelated recomposition does not re-emit Sentry data`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val recomposeTick = mutableIntStateOf(0)

    composeRule.setContent {
      recomposeTick.intValue
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { recomposeTick.intValue++ }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions.single().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/HomeRoute")))
  }

  /**
   * We want to make sure any composable `*Effect`s run in the nav destination can see the new nav
   * transaction, otherwise their spans will be misparented under the previous nav transaction.
   *
   * Note: Test assumes that [SentryNavEffect] is invoked before the destination composable, e.g.,
   * because `SentryNavEffect` is called before the host app invokes its `NavDisplay`.
   * `SentryNavEffect` docs contain instructions to that effect.
   */
  @Test
  fun `composable effects after navigation see the new nav transaction`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val observedTransactionNames = mutableListOf<String>()

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes)

      val currentTop = backStack.last()
      LaunchedEffect(currentTop) {
        val transaction = fixture.scopes.getSpan() as? SentryTracer
        observedTransactionNames += transaction?.name ?: "<none>"
      }
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(observedTransactionNames).containsExactly("/HomeRoute", "/ProfileRoute").inOrder()
  }

  @Test
  fun `updated name extractor is used for later navigation changes`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val nameExtractor = mutableStateOf<((Any) -> String)?>(null)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = nameExtractor.value,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      nameExtractor.value = { entry ->
        if (entry is ProfileRoute) "profile-updated" else "home-updated"
      }
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/profile-updated")
    assertThat(fixture.transactions.last().name).isEqualTo("/profile-updated")
    assertThat(fixture.scope.screen).isEqualTo("/profile-updated")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/profile-updated"), mapOf("route" to "/home-updated")))
  }

  @Test
  fun `changing the name extractor alone does not re-emit Sentry data for the current top entry`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute(), ProfileRoute("123"))
    val nameExtractor = mutableStateOf<((Any) -> String)?>(null)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = nameExtractor.value,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      nameExtractor.value = { entry ->
        if (entry is ProfileRoute) "profile-updated" else "home-updated"
      }
    }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.transactions.single().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/ProfileRoute"), mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `updated arguments extractor is used for later navigation changes`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val argumentsExtractor = mutableStateOf<((Any) -> Map<String, Any?>)?>(null)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        argumentsExtractor = argumentsExtractor.value,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      argumentsExtractor.value = { entry ->
        if (entry is ProfileRoute) mapOf("userId" to entry.userId) else emptyMap()
      }
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.breadcrumbs.last().data["to_arguments"]).isEqualTo(mapOf("userId" to "123"))
    assertThat(fixture.transactions.last().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions.last().getData("arguments")).isEqualTo(mapOf("userId" to "123"))
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to "/ProfileRoute", "args" to mapOf("userId" to "123")),
          mapOf("route" to "/HomeRoute"),
        )
      )
  }

  @Test
  fun `changing the arguments extractor alone does not re-emit Sentry data for the current top entry`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute(), ProfileRoute("123"))
    val argumentsExtractor = mutableStateOf<((Any) -> Map<String, Any?>)?>(null)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        scopes = fixture.scopes,
        argumentsExtractor = argumentsExtractor.value,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      argumentsExtractor.value = { entry ->
        if (entry is ProfileRoute) mapOf("userId" to entry.userId) else emptyMap()
      }
    }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.breadcrumbs.single().data["to_arguments"]).isNull()
    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.transactions.single().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions.single().getData("arguments")).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/ProfileRoute"), mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `changing options applies the new observer configuration`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val options = mutableStateOf(SentryNavOptions(captureBackStack = true))

    composeRule.setContent {
      SentryNavEffect(backStack = backStack, scopes = fixture.scopes, options = options.value)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { options.value = SentryNavOptions(captureBackStack = false) }
    composeRule.waitForIdle()

    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
  }

  @Test
  fun `removal from composition clears tracked state`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val isShown = mutableStateOf(true)

    composeRule.setContent {
      if (isShown.value) {
        SentryNavEffect(backStack = backStack, scopes = fixture.scopes)
      }
    }
    composeRule.waitForIdle()

    val transaction = composeRule.runOnIdle { fixture.transactions.single() }
    composeRule.runOnIdle { isShown.value = false }
    composeRule.waitForIdle()

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertNull(fixture.scope.contexts.app?.viewNames)
    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
  }

  private fun IScope.navigationBackStack(): List<Map<String, Any?>>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null

    @Suppress("UNCHECKED_CAST")
    return navigationContext[BACKSTACK_KEY] as? List<Map<String, Any?>>
  }

  private fun ITransaction.navigationBackStack(): List<Map<String, Any?>>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null

    @Suppress("UNCHECKED_CAST")
    return navigationContext[BACKSTACK_KEY] as? List<Map<String, Any?>>
  }

  private companion object {
    const val NAVIGATION_CONTEXT_KEY = "navigation"
    const val BACKSTACK_KEY = "backstack"
  }
}
