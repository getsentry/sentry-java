package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.navigation3.lifecycle.NavEffectGate
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertThrows
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
class SentryNavAutoInstrumentationTest {

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

  @Serializable @SerialName("home") private data object Home

  @Serializable @SerialName("profile") private data class Profile(val id: String)

  @Serializable @SerialName("tab") private data object Tab

  @Serializable @SerialName("tab_detail") private data object TabDetail

  private data object NotSerializable

  // region decorated entries

  @Test
  fun `returns the entries the entry provider creates for each back stack key, in back stack order`() {
    val fixture = Fixture.create()
    val providedKeys = mutableListOf<Any>()
    lateinit var entries: List<NavEntry<Any>>

    composeRule.setContent {
      entries =
        fixture.AutoNavEntries(listOf(Home, Profile("1"))) { key ->
          providedKeys += key
          NavEntry(key, contentKey = "content:$key", metadata = mapOf("key" to key.toString())) {}
        }
    }
    composeRule.waitForIdle()

    assertThat(providedKeys).containsExactly(Home, Profile("1")).inOrder()
    assertThat(entries.map { it.contentKey })
      .containsExactly("content:$Home", "content:${Profile("1")}")
      .inOrder()
    assertThat(entries.map { it.metadata })
      .containsExactly(mapOf("key" to Home.toString()), mapOf("key" to Profile("1").toString()))
      .inOrder()
  }

  @Test
  fun `returned entries render their content through the entry decorators`() {
    val fixture = Fixture.create()
    val rendered = mutableListOf<String>()
    val decorator =
      NavEntryDecorator<Any> { entry ->
        rendered += "decorator(${entry.contentKey})"
        entry.Content()
      }

    composeRule.setContent {
      val entries =
        fixture.AutoNavEntries(listOf(Home), entryDecorators = listOf(decorator)) { key ->
          NavEntry(key, contentKey = "home") { rendered += "content($it)" }
        }
      entries.last().Content()
    }
    composeRule.waitForIdle()

    assertThat(rendered).containsExactly("decorator(home)", "content($Home)").inOrder()
  }

  @Test
  fun `exceptions thrown by the entry provider propagate unchanged`() {
    val fixture = Fixture.create()
    val exception = IllegalStateException("entry provider failed")

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        composeRule.setContent { fixture.AutoNavEntries(listOf(Home)) { throw exception } }
        composeRule.waitForIdle()
      }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  // endregion

  // region recording navigation

  @Test
  fun `records the first screen named after its serial name`() {
    val fixture = Fixture.create()

    composeRule.setContent { fixture.AutoNavEntries(listOf(Home)) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] }).containsExactly("/home")
    assertThat(fixture.transactions.map { it.name }).containsExactly("/home")
    assertThat(fixture.scope.screen).isEqualTo("/home")
  }

  @Test
  fun `records pushed screens named after their serial names`() {
    val fixture = Fixture.create()
    val backStack = mutableStateListOf<Any>(Home)
    composeRule.setContent { fixture.AutoNavEntries(backStack) }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack += Profile("1") }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/home", "/profile")
      .inOrder()
    assertThat(fixture.transactions.map { it.name }).containsExactly("/home", "/profile").inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/profile")
  }

  @Test
  fun `records a screen without a serial name as unknown`() {
    val fixture = Fixture.create()

    composeRule.setContent { fixture.AutoNavEntries(listOf(NotSerializable)) }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] }).containsExactly("/unknown")
    assertThat(fixture.scope.screen).isEqualTo("/unknown")
  }

  // endregion

  // region nav root

  @Test
  fun `the View tree's lifecycle owner is the nav root, so a manual effect in the hosting activity outranks it`() {
    val fixture = Fixture.create()
    val entryOwner = TestOwner()
    val innerBackStack = mutableStateListOf<Any>(Tab)
    composeRule.setContent {
      // The manual effect's nav root is the activity hosting the composition.
      SentryNavEffect(
        backStack = listOf(Home),
        backStackEntryMapper = BackStackEntryMapper.serialName(),
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
        gate = fixture.gate,
      )
      // E.g., a NavDisplay nested in an entry of the host app's NavDisplay, which provides the
      // entry's own lifecycle owner.
      CompositionLocalProvider(LocalLifecycleOwner provides entryOwner) {
        fixture.AutoNavEntries(innerBackStack)
      }
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { innerBackStack += TabDetail }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] }).containsExactly("/home")
    assertThat(fixture.scope.screen).isEqualTo("/home")
  }

  @Test
  fun `the provided LocalLifecycleOwner's lifecycle doesn't affect the nav transaction`() {
    val fixture = Fixture.create()
    val entryOwner = TestOwner()
    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides entryOwner) {
        fixture.AutoNavEntries(listOf(Home))
      }
    }
    composeRule.waitForIdle()
    val homeTransaction = fixture.transactions.single()

    // A nav root's destruction would finish its nav transaction and clear its nav data.
    composeRule.runOnIdle {
      entryOwner.lifecycle.currentState = Lifecycle.State.STARTED
      entryOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }
    composeRule.waitForIdle()

    assertThat(homeTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/home")
  }

  @Test
  fun `without a View tree lifecycle owner, records nothing but still returns the entries`() {
    val fixture = Fixture.create()
    lateinit var entries: List<NavEntry<Any>>

    composeRule.setContent {
      // A View that isn't attached to a hierarchy has no View tree lifecycle owner.
      CompositionLocalProvider(LocalView provides View(LocalView.current.context)) {
        entries = fixture.AutoNavEntries(listOf(Home))
      }
    }
    composeRule.waitForIdle()

    assertThat(entries.map { it.contentKey }).containsExactly(Home.toString())
    assertThat(fixture.breadcrumbs).isEmpty()
    assertThat(fixture.transactions).isEmpty()
    assertThat(fixture.scope.screen).isNull()
  }

  // endregion

  // region other effects

  @Test
  fun `of nested NavDisplays in one activity, only the outer one records`() {
    val fixture = Fixture.create()
    val innerBackStack = mutableStateListOf<Any>(Tab)
    composeRule.setContent {
      fixture.AutoNavEntries(listOf(Home))
      fixture.AutoNavEntries(innerBackStack)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { innerBackStack += TabDetail }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] }).containsExactly("/home")
    assertThat(fixture.transactions.map { it.name }).containsExactly("/home")
  }

  @Test
  fun `a manual effect on the same back stack records each navigation once`() {
    val fixture = Fixture.create()
    val backStack = mutableStateListOf<Any>(Home)
    composeRule.setContent {
      SentryNavEffect(
        backStack = backStack,
        backStackEntryMapper = BackStackEntryMapper.serialName(),
        options = SentryNavOptions(),
        scopes = fixture.scopes,
        coordinator = fixture.coordinator,
        gate = fixture.gate,
      )
      fixture.AutoNavEntries(backStack)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack += Profile("1") }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/home", "/profile")
      .inOrder()
    assertThat(fixture.transactions.map { it.name }).containsExactly("/home", "/profile").inOrder()
  }

  // endregion

  private class TestOwner : LifecycleOwner {
    override val lifecycle =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }

  private class Fixture private constructor() {

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
    val gate = NavEffectGate()

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

    /** What a `NavDisplay` redirected by the Gradle plugin calls, with this fixture's SDK state. */
    @Composable
    fun AutoNavEntries(
      backStack: List<Any>,
      entryDecorators: List<NavEntryDecorator<Any>> = emptyList(),
      entryProvider: (Any) -> NavEntry<Any> = { key -> NavEntry(key) {} },
    ): List<NavEntry<Any>> =
      SentryNavAutoInstrumentation.rememberSentryDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
        scopes = scopes,
        coordinator = coordinator,
        gate = gate,
      )

    companion object {
      fun create(): Fixture = Fixture()
    }
  }
}
