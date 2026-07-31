package io.sentry.compose.navigation3

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class RememberSentryNavEntryDecoratorTest {

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

  private fun createScopes(): TestScopes = TestScopes()

  @Test
  fun `backstack change triggers breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      rememberSentryNavEntryDecorator(backStack = backStack, scopes = fixture.scopes)
    }

    composeRule.waitForIdle()

    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `pushing new key triggers additional breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      rememberSentryNavEntryDecorator(backStack = backStack, scopes = fixture.scopes)
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
    val showDecorator = mutableStateOf(true)

    composeRule.setContent {
      if (showDecorator.value) {
        rememberSentryNavEntryDecorator(backStack = backStack, scopes = fixture.scopes)
      }
    }
    composeRule.waitForIdle()

    assertEquals(1, fixture.transactions.size)
    assertEquals(false, fixture.transactions[0].isFinished)

    showDecorator.value = false
    composeRule.waitForIdle()

    assertEquals(true, fixture.transactions[0].isFinished)
  }

  @Test
  fun `inline lambdas do not cause state holder churn on recomposition`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val recomposeTrigger = mutableStateOf(0)

    composeRule.setContent {
      @Suppress("UNUSED_EXPRESSION") recomposeTrigger.value

      rememberSentryNavEntryDecorator(
        backStack = backStack,
        scopes = fixture.scopes,
        nameExtractor = { key -> key::class.simpleName ?: "unknown" },
        argumentsExtractor = { _ -> emptyMap() },
      )
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())

    recomposeTrigger.value = 1
    composeRule.waitForIdle()
    recomposeTrigger.value = 2
    composeRule.waitForIdle()

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
      rememberSentryNavEntryDecorator(
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
  fun `changing config mid-composition takes effect on subsequent navigation`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val breadcrumbsEnabled = mutableStateOf(true)

    composeRule.setContent {
      rememberSentryNavEntryDecorator(
        backStack = backStack,
        scopes = fixture.scopes,
        enableNavigationBreadcrumbs = breadcrumbsEnabled.value,
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
  fun `pushing structurally equal key does not trigger duplicate event`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(ProfileScreen("123"))

    composeRule.setContent {
      rememberSentryNavEntryDecorator(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())

    backStack.add(ProfileScreen("123"))
    composeRule.waitForIdle()

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `empty backstack does not trigger breadcrumb`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>()

    composeRule.setContent {
      rememberSentryNavEntryDecorator(backStack = backStack, scopes = fixture.scopes)
    }
    composeRule.waitForIdle()

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  // region Phase 2: holder-based API

  @Test
  fun `rememberSentryNavStateHolder with rememberSentryNavEntryDecorator fires breadcrumb and transaction`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())

    composeRule.setContent {
      val holder = rememberSentryNavStateHolder<Any>(scopes = fixture.scopes)
      SentryNav3NavigationEffect(backStack = backStack, holder = holder)
      rememberSentryNavEntryDecorator(holder = holder)
    }
    composeRule.waitForIdle()

    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `holder cleanup called when removed from composition`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    val showHolder = mutableStateOf(true)

    composeRule.setContent {
      if (showHolder.value) {
        val holder = rememberSentryNavStateHolder<Any>(scopes = fixture.scopes)
        SentryNav3NavigationEffect(backStack = backStack, holder = holder)
        rememberSentryNavEntryDecorator(holder = holder)
      }
    }
    composeRule.waitForIdle()

    assertEquals(1, fixture.transactions.size)
    assertEquals(false, fixture.transactions[0].isFinished)

    showHolder.value = false
    composeRule.waitForIdle()

    assertEquals(true, fixture.transactions[0].isFinished)
  }

  @Test
  fun `holder-based decorator is accessible as SentryNavStateHolder public type`() {
    val fixture = createScopes()
    val backStack = mutableStateListOf<Any>(HomeScreen())
    var capturedHolder: SentryNavStateHolder<Any>? = null

    composeRule.setContent {
      val holder = rememberSentryNavStateHolder<Any>(scopes = fixture.scopes)
      capturedHolder = holder
      SentryNav3NavigationEffect(backStack = backStack, holder = holder)
      rememberSentryNavEntryDecorator(holder = holder)
    }
    composeRule.waitForIdle()

    // SentryNavStateHolder is a public type — confirm we can hold a reference to it
    kotlin.test.assertNotNull(capturedHolder)
  }

  // endregion
}
