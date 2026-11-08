package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
import io.sentry.compose.navigation3.lifecycle.NavEffectKind
import io.sentry.compose.navigation3.lifecycle.NavEffectKind.AUTO
import io.sentry.compose.navigation3.lifecycle.NavEffectKind.MANUAL
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

/**
 * Composes manual and auto-instrumented `SentryNavEffect`s side by side, sharing one
 * [NavEffectGate] and one [NavLeaseCoordinator], to check that the gate keeps them from recording
 * the same navigation twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryNavEffectGateIntegrationTest {

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

  @Test
  fun `a manual and an auto effect on the same back stack record each navigation once`() {
    val fixture = Fixture.create()
    val backStack = mutableStateListOf("Home")
    composeRule.setContent {
      // The host app calls its effect before its NavDisplay, which auto-instrumentation observes.
      fixture.NavEffect(backStack, MANUAL)
      fixture.NavEffect(backStack, AUTO)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack += "Profile" }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Profile")
      .inOrder()
    assertThat(fixture.transactions.map { it.name }).containsExactly("/Home", "/Profile").inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
  }

  @Test
  fun `known limitation - a manual effect composed after the auto effect records the first screen twice`() {
    val fixture = Fixture.create()
    val backStack = mutableStateListOf("Home")
    composeRule.setContent {
      // Unsupported placement: the host app calls its effect after its NavDisplay, so the auto
      // effect records the first screen before the manual effect takes over and records it again.
      fixture.NavEffect(backStack, AUTO)
      fixture.NavEffect(backStack, MANUAL)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { backStack += "Profile" }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Home", "/Profile")
      .inOrder()
    assertThat(fixture.transactions.map { it.name })
      .containsExactly("/Home", "/Home", "/Profile")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
  }

  @Test
  fun `nested auto effects in one nav root record only the outer effect's navigation`() {
    val fixture = Fixture.create()
    val outerBackStack = mutableStateListOf("Home")
    val innerBackStack = mutableStateListOf("Tab")
    composeRule.setContent {
      fixture.NavEffect(outerBackStack, AUTO)
      fixture.NavEffect(innerBackStack, AUTO)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { innerBackStack += "TabDetail" }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
    assertThat(fixture.transactions.map { it.name }).containsExactly("/Home")
    assertThat(fixture.scope.screen).isEqualTo("/Home")
  }

  @Test
  fun `when the outer auto effect leaves composition, the inner auto effect records its current top entry`() {
    val fixture = Fixture.create()
    val outerBackStack = mutableStateListOf("Home")
    val innerBackStack = mutableStateListOf("Tab")
    val isOuterShown = mutableStateOf(true)
    composeRule.setContent {
      if (isOuterShown.value) {
        fixture.NavEffect(outerBackStack, AUTO)
      }
      fixture.NavEffect(innerBackStack, AUTO)
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { innerBackStack += "TabDetail" }
    composeRule.waitForIdle()
    val outerTransaction = fixture.transactions.single()

    composeRule.runOnIdle { isOuterShown.value = false }
    composeRule.waitForIdle()

    assertThat(outerTransaction.isFinished).isTrue()
    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/TabDetail")
      .inOrder()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/TabDetail")
    assertThat(fixture.scope.screen).isEqualTo("/TabDetail")
  }

  @Test
  fun `a manual effect added after an auto effect in the same nav root stops the auto effect's recording`() {
    val fixture = Fixture.create()
    val autoBackStack = mutableStateListOf("Home")
    val isManualShown = mutableStateOf(false)
    composeRule.setContent {
      fixture.NavEffect(autoBackStack, AUTO)
      if (isManualShown.value) {
        fixture.NavEffect(listOf("Settings"), MANUAL)
      }
    }
    composeRule.waitForIdle()
    val autoTransaction = fixture.transactions.single()

    composeRule.runOnIdle { isManualShown.value = true }
    composeRule.waitForIdle()
    composeRule.runOnIdle { autoBackStack += "Profile" }
    composeRule.waitForIdle()

    assertThat(autoTransaction.isFinished).isTrue()
    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Settings")
      .inOrder()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Settings")
    assertThat(fixture.scope.screen).isEqualTo("/Settings")
  }

  @Test
  fun `an auto effect handed a manual effect's back stack instance stops recording`() {
    val fixture = Fixture.create()
    val manualBackStack = mutableStateListOf("Home")
    // Holds the same entry instance, so only the back stack instance changes for the auto effect.
    val autoBackStack = mutableStateOf<List<String>>(mutableStateListOf(manualBackStack.single()))
    composeRule.setContent {
      fixture.NavEffect(manualBackStack, MANUAL)
      fixture.NavEffect(autoBackStack.value, AUTO, fixture.otherNavRoot)
    }
    composeRule.waitForIdle()
    check(fixture.breadcrumbs.size == 2)

    composeRule.runOnIdle { autoBackStack.value = manualBackStack }
    composeRule.waitForIdle()
    composeRule.runOnIdle { manualBackStack += "Profile" }
    composeRule.waitForIdle()

    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Home", "/Profile")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
  }

  @Test
  fun `an auto effect handed a manual effect's back stack with different entries records nothing for it`() {
    val fixture = Fixture.create()
    val manualBackStack = mutableStateListOf("Home")
    val autoBackStack = mutableStateOf<List<String>>(mutableStateListOf("Other"))
    composeRule.setContent {
      fixture.NavEffect(manualBackStack, MANUAL)
      fixture.NavEffect(autoBackStack.value, AUTO, fixture.otherNavRoot)
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { autoBackStack.value = manualBackStack }
    composeRule.waitForIdle()

    // Regression: the auto effect used to record the new back stack's top entry before the gate
    // learned it now shares the manual effect's back stack.
    assertThat(fixture.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Other")
      .inOrder()
    assertThat(fixture.transactions.map { it.name }).containsExactly("/Home", "/Other").inOrder()
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
    val navOptions = SentryNavOptions()
    val mapper = BackStackEntryMapper<String> { SentryBackStackEntry(it) }

    val navRoot: LifecycleOwner = ResumedOwner()
    val otherNavRoot: LifecycleOwner = ResumedOwner()

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

    @Composable
    fun NavEffect(
      backStack: List<String>,
      kind: NavEffectKind,
      navRoot: LifecycleOwner = this.navRoot,
    ) {
      SentryNavEffect(backStack, mapper, navOptions, scopes, coordinator, gate, kind, navRoot)
    }

    companion object {
      fun create(): Fixture = Fixture()
    }
  }

  private class ResumedOwner : LifecycleOwner {
    override val lifecycle =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }
}
