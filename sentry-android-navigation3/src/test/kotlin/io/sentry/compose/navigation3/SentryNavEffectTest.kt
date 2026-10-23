package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import org.junit.Rule
import org.junit.Test
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
@Suppress("LargeClass")
class SentryNavEffectTest {

  private val defaultEntryMapper =
    BackStackEntryMapper<Any> { entry ->
      SentryBackStackEntry(entry::class.simpleName ?: "unknown")
    }

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
        idleTimeout = null
        deadlineTimeout = 0
      }
    val scope = Scope(options)
    val scopes = mock<IScopes>()
    val coordinator = NavLeaseCoordinator()
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
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }

    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions.single().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/HomeRoute")))
  }

  @Test
  fun `pushing a new top entry emits Sentry data for that entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
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
      .isEqualTo(listOf(mapOf("entry" to "/ProfileRoute"), mapOf("entry" to "/HomeRoute")))
  }

  @Test
  fun `popping the top entry emits Sentry data for the new top entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf(HomeRoute(), ProfileRoute("123"))

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
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
      .isEqualTo(listOf(mapOf("entry" to "/HomeRoute")))
  }

  @Test
  fun `replacing the back stack emits Sentry data for the new top entry and a new back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf(HomeRoute(), ProfileRoute("123"))

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
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
      .isEqualTo(listOf(mapOf("entry" to "/HomeRoute"), mapOf("entry" to "/ProfileRoute")))
  }

  @Test
  fun `changing non-top entries does not re-emit top-entry Sentry data but does emit the new back stack copy`() {
    val fixture = Fixture()
    val home = HomeRoute()
    val profile = ProfileRoute("123")
    val backStack = mutableStateListOf<Any>(home, profile)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
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
          mapOf("entry" to "/ProfileRoute"),
          mapOf("entry" to "/HomeRoute"),
          mapOf("entry" to "/HomeRoute"),
        )
      )
  }

  @Test
  fun `changing back stack with capture limit of 0 still emits top-entry Sentry data but not back stack copy`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf(HomeRoute(), ProfileRoute("123"))

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions { maxCapturedBackStackEntries = 0 },
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions.single().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.contexts.containsKey(NAVIGATION_CONTEXT_KEY)).isFalse()
  }

  @Test
  fun `unrelated recomposition does not re-emit Sentry data`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val recomposeTick = mutableIntStateOf(0)

    composeRule.setContent {
      recomposeTick.intValue
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
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
      .isEqualTo(listOf(mapOf("entry" to "/HomeRoute")))
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
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = defaultEntryMapper,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )

      val currentTop = backStack.last()
      DisposableEffect(currentTop) {
        val transaction = fixture.scopes.getSpan() as? SentryTracer
        observedTransactionNames += transaction?.name ?: "<none>"
        onDispose {}
      }
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(observedTransactionNames).containsExactly("/HomeRoute", "/ProfileRoute").inOrder()
  }

  @Test
  fun `updated entry mapper is used for later navigation changes`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val entryMapper =
      mutableStateOf<BackStackEntryMapper<Any>>(
        BackStackEntryMapper { entry ->
          SentryBackStackEntry(entry::class.simpleName ?: "<unknown>")
        }
      )

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = entryMapper.value,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      entryMapper.value = BackStackEntryMapper { entry ->
        SentryBackStackEntry(if (entry is ProfileRoute) "profile-updated" else "home-updated")
      }
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { backStack.add(ProfileRoute("123")) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/profile-updated")
    assertThat(fixture.transactions.last().name).isEqualTo("/profile-updated")
    assertThat(fixture.scope.screen).isEqualTo("/profile-updated")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/profile-updated"), mapOf("entry" to "/home-updated")))
  }

  @Test
  fun `changing the entry mapper alone does not re-emit Sentry data for the current top entry`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute(), ProfileRoute("123"))
    val entryMapper =
      mutableStateOf<BackStackEntryMapper<Any>>(
        BackStackEntryMapper { entry ->
          SentryBackStackEntry(entry::class.simpleName ?: "unknown")
        }
      )

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = entryMapper.value,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      entryMapper.value = BackStackEntryMapper { entry ->
        SentryBackStackEntry(if (entry is ProfileRoute) "profile-updated" else "home-updated")
      }
    }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.transactions.single().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/ProfileRoute"), mapOf("entry" to "/HomeRoute")))
  }

  @Test
  fun `updated entry mapper arguments are used for later navigation changes`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val entryMapper = mutableStateOf(defaultEntryMapper)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = entryMapper.value,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      entryMapper.value = BackStackEntryMapper { entry ->
        SentryBackStackEntry(
          entry::class.simpleName ?: "unknown",
          if (entry is ProfileRoute) mapOf("userId" to entry.userId) else emptyMap(),
        )
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
          mapOf("entry" to "/ProfileRoute", "entry_arguments" to mapOf("userId" to "123")),
          mapOf("entry" to "/HomeRoute"),
        )
      )
  }

  @Test
  fun `changing entry mapper arguments alone does not re-emit Sentry data for current top entry`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute(), ProfileRoute("123"))
    val entryMapper = mutableStateOf(defaultEntryMapper)

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = entryMapper.value,
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle {
      entryMapper.value = BackStackEntryMapper { entry ->
        SentryBackStackEntry(
          entry::class.simpleName ?: "unknown",
          if (entry is ProfileRoute) mapOf("userId" to entry.userId) else emptyMap(),
        )
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
      .isEqualTo(listOf(mapOf("entry" to "/ProfileRoute"), mapOf("entry" to "/HomeRoute")))
  }

  @Test
  fun `changing options applies the new observer configuration`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val options = mutableStateOf(SentryNavOptions { captureBackStack = true })

    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        options = options.value,
        backStackEntryMapper = defaultEntryMapper,
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
      )
    }
    composeRule.waitForIdle()

    val originalTransaction = composeRule.runOnIdle { fixture.transactions.single() }

    composeRule.runOnIdle { options.value = SentryNavOptions { captureBackStack = false } }
    composeRule.waitForIdle()

    assertThat(originalTransaction.isFinished).isTrue()
    assertThat(fixture.transactions).hasSize(2)
    assertThat(fixture.transactions.last().name).isEqualTo("/HomeRoute")
    assertThat(fixture.transactions.last().isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.transactions.last())
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.contexts.containsKey(NAVIGATION_CONTEXT_KEY)).isFalse()
  }

  @Test
  fun `removal from composition clears tracked state`() {
    val fixture = Fixture()
    val backStack = mutableStateListOf<Any>(HomeRoute())
    val isShown = mutableStateOf(true)

    composeRule.setContent {
      if (isShown.value) {
        SentryNavEffect(
          backStack = backStack,
          backStackEntryMapper = defaultEntryMapper,
          options = SentryNavOptions(),
          scopes = fixture.scopes,
          coordinator = fixture.coordinator,
        )
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
    assertThat(fixture.scope.contexts.app?.viewNames).isNull()
    assertThat(fixture.scope.contexts.containsKey(NAVIGATION_CONTEXT_KEY)).isFalse()
  }

  @Test
  fun `effect publishes initial navigation before its lifecycle owner resumes`() {
    val fixture = NavTestFixture()
    val owner = NavTestLifecycle()
    val options = SentryNavOptions()
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        SentryNavEffect(
          backStack = listOf("Home"),
          backStackEntryMapper = BackStackEntryMapper { SentryBackStackEntry(it) },
          options = options,
          scopes = fixture.scopes,
          coordinator = fixture.coordinator,
        )
      }
    }
    composeRule.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
      owner.resume()
    }
    composeRule.runOnIdle {
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
    }
  }

  @Test
  fun `effect records navigation while its lifecycle owner is paused`() {
    val fixture = NavTestFixture()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)
    val backStack = mutableStateListOf("Home")
    val options = SentryNavOptions()
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        SentryNavEffect(
          backStack = backStack,
          backStackEntryMapper = BackStackEntryMapper { SentryBackStackEntry(it) },
          options = options,
          scopes = fixture.scopes,
          coordinator = fixture.coordinator,
        )
      }
    }
    composeRule.runOnIdle {
      owner.pause()
      backStack += "Profile"
    }
    composeRule.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
      assertThat(fixture.scope.screen).isEqualTo("/Profile")
      assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
        .containsExactly("/Home", "/Profile")
        .inOrder()
    }
  }

  @Test
  fun `navigating without the lease records breadcrumbs, then starts a nav transaction on resume`() {
    val fixture = NavTestFixture()
    val ownerA = NavTestLifecycle(Lifecycle.State.RESUMED)
    val ownerB = NavTestLifecycle()
    val backStackA = mutableStateListOf("Home")
    val showB = mutableStateOf(false)
    val options = SentryNavOptions()
    val mapper = BackStackEntryMapper<String> { SentryBackStackEntry(it) }
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides ownerA) {
        SentryNavEffect(backStackA, mapper, options, fixture.scopes, fixture.coordinator)
      }
      if (showB.value) {
        CompositionLocalProvider(LocalLifecycleOwner provides ownerB) {
          SentryNavEffect(listOf("Picker"), mapper, options, fixture.scopes, fixture.coordinator)
        }
      }
    }

    // A starts B: A pauses, B is created and takes over, B resumes, then A stops.
    composeRule.runOnIdle {
      ownerA.pause()
      showB.value = true
    }
    composeRule.runOnIdle {
      ownerB.resume()
      ownerA.stop()
    }
    // B finishes with a result: B pauses, then A restarts and navigates before it resumes.
    composeRule.runOnIdle {
      ownerB.pause()
      ownerA.start()
      backStackA += "Result"
    }
    lateinit var contextBeforeResume: PropagationContext
    composeRule.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Picker")
      assertThat(fixture.scope.screen).isEqualTo("/Picker")
      assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
        .containsExactly("/Home", "/Picker", "/Result")
        .inOrder()
      contextBeforeResume = fixture.scope.propagationContext
      ownerA.resume()
    }

    // A got no nav transaction for /Result while it lacked the lease: one started then would have
    // collected no spans of its own. It starts one when it regains the lease, with a fresh trace.
    composeRule.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Result")
      assertThat(fixture.scope.screen).isEqualTo("/Result")
      assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(contextBeforeResume)
    }
  }

  @Test
  fun `backgrounding with a translucent owner on top keeps the top owner's data published`() {
    val fixture = NavTestFixture()
    val beneath = NavTestLifecycle(Lifecycle.State.RESUMED)
    val translucent = NavTestLifecycle()
    val showTranslucent = mutableStateOf(false)
    val options = SentryNavOptions()
    val mapper = BackStackEntryMapper<String> { SentryBackStackEntry(it) }
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides beneath) {
        SentryNavEffect(listOf("Products"), mapper, options, fixture.scopes, fixture.coordinator)
      }
      if (showTranslucent.value) {
        CompositionLocalProvider(LocalLifecycleOwner provides translucent) {
          SentryNavEffect(listOf("Checkout"), mapper, options, fixture.scopes, fixture.coordinator)
        }
      }
    }

    // A translucent owner opens on top: the owner beneath pauses but stays visible.
    composeRule.runOnIdle {
      beneath.pause()
      showTranslucent.value = true
    }
    lateinit var checkoutTransaction: ITransaction
    composeRule.runOnIdle {
      translucent.resume()
      checkoutTransaction = requireNotNull(fixture.scope.transaction)
      assertThat(checkoutTransaction.name).isEqualTo("/Checkout")
    }

    // The app goes to the background: the top owner stops before the one beneath it.
    composeRule.runOnIdle {
      translucent.pause()
      translucent.stop()
      beneath.stop()

      assertThat(fixture.scope.screen).isEqualTo("/Checkout")
      assertThat(fixture.scope.navigationBackStack()?.map { it["entry"] })
        .containsExactly("/Checkout")
      assertThat(fixture.scope.transaction).isSameInstanceAs(checkoutTransaction)
    }
  }

  @Test
  fun `regaining the lease keeps a host screen name when screen tracking is off`() {
    val fixture = NavTestFixture()
    fixture.options.isEnableScreenTracking = false
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)
    val options = SentryNavOptions()
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        SentryNavEffect(
          backStack = listOf("Home"),
          backStackEntryMapper = BackStackEntryMapper { SentryBackStackEntry(it) },
          options = options,
          scopes = fixture.scopes,
          coordinator = fixture.coordinator,
        )
      }
    }
    composeRule.runOnIdle {
      fixture.scope.screen = "HostScreen"

      // E.g., the app goes to the background and comes back.
      owner.pause()
      owner.stop()
      owner.start()
      owner.resume()

      assertThat(fixture.scope.screen).isEqualTo("HostScreen")
      assertThat(fixture.scope.navigationBackStack()?.map { it["entry"] }).containsExactly("/Home")
    }
  }

  @Test
  fun `replacing lifecycle owner disposes the old session and observes the new owner`() {
    val fixture = NavTestFixture()
    val originalOwner = NavTestLifecycle(Lifecycle.State.RESUMED)
    val replacementOwner = NavTestLifecycle()
    val owner = mutableStateOf(originalOwner)
    val options = SentryNavOptions()
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner.value) {
        SentryNavEffect(
          backStack = listOf("Home"),
          backStackEntryMapper = BackStackEntryMapper { SentryBackStackEntry(it) },
          options = options,
          scopes = fixture.scopes,
          coordinator = fixture.coordinator,
        )
      }
    }
    lateinit var originalTransaction: ITransaction
    composeRule.runOnIdle {
      originalTransaction = requireNotNull(fixture.scope.transaction)
      owner.value = replacementOwner
    }
    composeRule.runOnIdle {
      assertThat(originalTransaction.isFinished).isTrue()
      assertThat(fixture.scope.transaction).isNotSameInstanceAs(originalTransaction)
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      originalOwner.pause()
      originalOwner.resume()
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      replacementOwner.resume()
      assertThat(fixture.scope.screen).isEqualTo("/Home")
    }
  }

  private fun IScope.navigationBackStack(): List<Map<String, Any?>>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null

    @Suppress("UNCHECKED_CAST")
    return navigationContext[BACKSTACK_KEY] as? List<Map<String, Any?>>
  }

  private companion object {
    const val NAVIGATION_CONTEXT_KEY = "Navigation"
    const val BACKSTACK_KEY = "Back Stack"
  }
}

private class NavTestLifecycle(state: Lifecycle.State = Lifecycle.State.CREATED) : LifecycleOwner {

  override val lifecycle =
    LifecycleRegistry.createUnsafe(this).apply {
      if (state == Lifecycle.State.DESTROYED) currentState = Lifecycle.State.CREATED
      currentState = state
    }

  fun resume() {
    lifecycle.currentState = Lifecycle.State.RESUMED
  }

  fun pause() {
    lifecycle.currentState = Lifecycle.State.STARTED
  }

  fun start() {
    lifecycle.currentState = Lifecycle.State.STARTED
  }

  fun stop() {
    lifecycle.currentState = Lifecycle.State.CREATED
  }
}

/** Real SDK state with only the event delivery boundary stubbed. No timers or global SDK state. */
private class NavTestFixture {

  val options =
    SentryOptions().apply {
      dsn = "http://key@localhost/1"
      defaultScopeType = ScopeType.CURRENT
      setTracesSampleRate(1.0)
      idleTimeout = null
      deadlineTimeout = 0
    }

  val scope = Scope(options)
  val scopes = Scopes(scope, Scope(options), Scope(options), "navigation test")

  val coordinator = NavLeaseCoordinator()

  init {
    val client = mock<ISentryClient>()
    whenever(client.isEnabled).thenReturn(true)
    scopes.bindClient(client)
  }
}
