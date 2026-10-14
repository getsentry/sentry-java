package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ISentryClient
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.protocol.TransactionNameSource
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * End-to-end scenarios with more than one [SentryNavEffect], each under its own lifecycle owner, as
 * in multi-Activity apps or apps where each Fragment hosts its own Nav3 root.
 *
 * Lifecycle sequences mirror the framework:
 * - Activities: the outgoing owner pauses before the incoming one composes; the incoming one
 *   composes already resumed; the outgoing one stops afterwards.
 * - Fragment `replace`: the incoming owner composes at CREATED while the outgoing one is still
 *   resumed; the outgoing one then pauses and stops; the incoming one resumes last.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SentryNavEffectMultiOwnerTest {

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

  // region Activities

  @Test
  fun `activity forward - incoming owner gets its own transaction and nav data`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    val txA = fixture.runOnIdle { requireNotNull(fixture.scope.transaction) }

    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)

    fixture.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
      assertThat(fixture.scope.screen).isEqualTo("/Checkout")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Checkout")
      assertThat(txA.isFinished).isFalse()
    }
  }

  @Test
  fun `activity forward - outgoing owner's cleanup doesn't clear incoming owner's data`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    val txA = fixture.runOnIdle { requireNotNull(fixture.scope.transaction) }
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)

    fixture.runOnIdle { a.stop() }
    fixture.hide(a)

    fixture.runOnIdle {
      assertThat(txA.isFinished).isTrue()
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
      assertThat(fixture.scope.screen).isEqualTo("/Checkout")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Checkout")
    }
  }

  @Test
  fun `activity back - returning owner's nav data is restored and survives the other's cleanup`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)
    fixture.runOnIdle { a.stop() }

    fixture.runOnIdle {
      b.pause()
      a.resume()
    }
    fixture.runOnIdle {
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Home")
      b.stop()
    }
    fixture.hide(b)

    fixture.runOnIdle {
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Home")
    }
  }

  @Test
  fun `activity abort - outgoing owner's nav data is back once it resumes`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)

    fixture.runOnIdle { b.pause() }
    fixture.hide(b)
    fixture.runOnIdle { a.resume() }

    fixture.runOnIdle {
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Home")
    }
  }

  @Test
  fun `activity abort - outgoing owner's unfinished transaction is rebound once it resumes`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    val txA = fixture.runOnIdle { requireNotNull(fixture.scope.transaction) }
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)

    // Framework order when B finishes quickly: B pauses, A resumes, then B is destroyed.
    fixture.runOnIdle {
      b.pause()
      a.resume()
    }
    fixture.runOnIdle { b.stop() }
    fixture.hide(b)

    fixture.runOnIdle { assertThat(fixture.scope.transaction).isSameInstanceAs(txA) }
  }

  @Test
  fun `overlap - outgoing owner navigating while paused doesn't take the incoming transaction`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "Checkout")
    fixture.show(b)
    val txB = fixture.runOnIdle { requireNotNull(fixture.scope.transaction) }

    fixture.runOnIdle { a.backStack.add("Settings") }

    fixture.runOnIdle { assertThat(fixture.scope.transaction).isSameInstanceAs(txB) }
  }

  @Test
  fun `equal route names - outgoing owner's cleanup doesn't clear the incoming owner's equal screen`() {
    val fixture = Fixture(composeRule)
    // Same literal in both owners, so both route names are the same interned string.
    val a = fixture.host("A", "/Home")
    fixture.show(a)
    fixture.runOnIdle { a.pause() }
    val b = fixture.host("B", "/Home")
    fixture.show(b)

    fixture.runOnIdle { a.stop() }
    fixture.hide(a)

    fixture.runOnIdle {
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Home")
    }
  }

  @Test
  fun `first update never evicts a non-navigation transaction`() {
    val fixture = Fixture(composeRule)
    val hostTransaction =
      SentryTracer(
        TransactionContext("host", TransactionNameSource.CUSTOM, "ui.load"),
        fixture.scopes,
      )
    fixture.scope.transaction = hostTransaction

    fixture.show(fixture.host("A", "Home"))

    fixture.runOnIdle { assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction) }
  }

  // endregion

  // region Single owner

  @Test
  fun `single owner - navigating while paused still records a transaction and breadcrumb`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)

    fixture.runOnIdle { a.pause() }
    fixture.runOnIdle { a.backStack.add("Settings") }
    fixture.runOnIdle { a.resume() }

    fixture.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Settings")
      assertThat(fixture.scope.screen).isEqualTo("/Settings")
      assertThat(fixture.breadcrumbDestinations()).containsExactly("/Home", "/Settings").inOrder()
    }
  }

  @Test
  fun `single owner - pause and resume keep a host screen when screen tracking is disabled`() {
    val fixture = Fixture(composeRule, enableScreenTracking = false)
    fixture.scope.screen = "HostScreen"
    val a = fixture.host("A", "Home")
    fixture.show(a)

    fixture.runOnIdle { a.pause() }
    fixture.runOnIdle { a.resume() }

    fixture.runOnIdle { assertThat(fixture.scope.screen).isEqualTo("HostScreen") }
  }

  // endregion

  // region Fragments (each Fragment hosts its own Nav3 root)

  @Test
  fun `fragment replace - incoming owner takes over while still CREATED`() {
    val fixture = Fixture(composeRule)
    val a = fixture.host("A", "Home")
    fixture.show(a)
    val b = fixture.host("B", "Checkout", Lifecycle.State.CREATED)

    fixture.show(b)

    fixture.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
      assertThat(fixture.scope.screen).isEqualTo("/Checkout")
      a.pause()
      a.stop()
      b.start()
    }
    fixture.runOnIdle {
      // Enter-animation window: B is STARTED, A is stopped.
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
      b.resume()
    }
    fixture.hide(a)

    fixture.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
      assertThat(fixture.scope.screen).isEqualTo("/Checkout")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Checkout")
    }
  }

  @Test
  fun `fragment pop - recreated owner takes over again`() {
    val fixture = Fixture(composeRule)
    val b = fixture.host("B", "Checkout")
    fixture.show(b)
    val a = fixture.host("A", "Home", Lifecycle.State.CREATED)

    fixture.show(a)
    fixture.runOnIdle {
      b.pause()
      b.stop()
      a.start()
      a.resume()
    }
    fixture.hide(b)

    fixture.runOnIdle {
      assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
      assertThat(fixture.scope.screen).isEqualTo("/Home")
      assertThat(fixture.topOfNavigationContext()).isEqualTo("/Home")
    }
  }

  // endregion

  private class Host(val id: String, initialRoute: String, initialState: Lifecycle.State) :
    LifecycleOwner {
    val backStack: SnapshotStateList<String> = mutableStateListOf(initialRoute)

    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

    fun start() {
      lifecycle.currentState = Lifecycle.State.STARTED
    }

    fun resume() {
      lifecycle.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
      lifecycle.currentState = Lifecycle.State.STARTED
    }

    fun stop() {
      lifecycle.currentState = Lifecycle.State.CREATED
    }
  }

  /** Real SDK state with only the event delivery boundary stubbed. */
  private class Fixture(
    private val composeRule: AndroidComposeTestRule<*, ComponentActivity>,
    enableScreenTracking: Boolean = true,
  ) {
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/1"
        defaultScopeType = ScopeType.CURRENT
        setTracesSampleRate(1.0)
        isEnableScreenTracking = enableScreenTracking
        idleTimeout = null
        deadlineTimeout = 0
      }
    val scope = Scope(options)
    val scopes = Scopes(scope, Scope(options), Scope(options), "multi-owner test")
    private val navOptions = SentryNavOptions()
    private val hosts = mutableStateListOf<Host>()

    init {
      val client = mock<ISentryClient>()
      whenever(client.isEnabled).thenReturn(true)
      scopes.bindClient(client)
      composeRule.setContent {
        hosts.forEach { host -> key(host.id) { HostContent(host) } }
      }
    }

    @Composable
    private fun HostContent(host: Host) {
      CompositionLocalProvider(LocalLifecycleOwner provides host) {
        SentryNavEffect(
          backStack = host.backStack,
          backStackEntryMapper = { SentryBackStackEntry(it) },
          options = navOptions,
          scopes = scopes,
        )
      }
    }

    fun host(id: String, route: String, state: Lifecycle.State = Lifecycle.State.RESUMED) =
      Host(id, route, state)

    fun show(host: Host) = runOnIdle { hosts += host }

    fun hide(host: Host) = runOnIdle { hosts -= host }

    fun <R> runOnIdle(block: () -> R): R = composeRule.runOnIdle(block)

    fun topOfNavigationContext(): Any? {
      val navigationContext = scope.contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null
      val entries = navigationContext["Back Stack"] as? List<*> ?: return null
      return (entries.firstOrNull() as? Map<*, *>)?.get("entry")
    }

    fun breadcrumbDestinations(): List<Any?> = scope.breadcrumbs.map { it.data["to"] }
  }
}
