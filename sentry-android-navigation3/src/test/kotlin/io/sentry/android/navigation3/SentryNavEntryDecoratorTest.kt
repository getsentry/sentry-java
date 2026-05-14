package io.sentry.android.navigation3

import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.Scope.IWithTransaction
import io.sentry.ScopeCallback
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

data class HomeScreen(val dummy: String = "")

data class ProfileScreen(val userId: String)

data class SettingsScreen(val section: String)

class SentryNavEntryDecoratorTest {

  class Fixture {
    val scopes = mock<IScopes>()
    val scope = mock<IScope>()
    lateinit var options: SentryOptions
    lateinit var transaction: SentryTracer

    @Suppress("LongParameterList")
    internal fun getSut(
      enableBreadcrumbs: Boolean = true,
      enableNavigationTracing: Boolean = true,
      enableScreenTracking: Boolean = true,
      enableBackstackContext: Boolean = true,
      maxBackstackSize: Int = 30,
      tracesSampleRate: Double? = 1.0,
      nameExtractor: ((Any) -> String)? = null,
      argumentsExtractor: ((Any) -> Map<String, Any?>)? = null,
      transaction: SentryTracer? = null,
    ): SentryNavStateHolder<Any> {
      options =
        SentryOptions().apply {
          dsn = "http://key@localhost/proj"
          setTracesSampleRate(tracesSampleRate)
          isEnableScreenTracking = enableScreenTracking
        }
      whenever(scopes.options).thenReturn(options)

      this.transaction =
        transaction ?: SentryTracer(TransactionContext("/HomeScreen", "navigation"), scopes)

      whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
        .thenReturn(this.transaction)

      whenever(scopes.configureScope(any())).thenAnswer {
        (it.arguments[0] as ScopeCallback).run(scope)
      }

      return SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
      )
    }
  }

  private val fixture = Fixture()

  // region Breadcrumbs

  @Test
  fun `onTopKeyChanged captures a breadcrumb`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("navigation", it.type)
          assertEquals("navigation", it.category)
          assertEquals("/HomeScreen", it.data["to"])
          assertEquals(SentryLevel.INFO, it.level)
        },
        any(),
      )
  }

  @Test
  fun `onTopKeyChanged captures breadcrumb with from and to`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    sut.onTopKeyChanged(home, listOf(home))
    sut.onTopKeyChanged(profile, listOf(home, profile))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals("/HomeScreen", it.data["from"])
      assertEquals("/ProfileScreen", it.data["to"])
    }
  }

  @Test
  fun `onTopKeyChanged includes arguments in breadcrumb when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onTopKeyChanged(ProfileScreen("123"), listOf(ProfileScreen("123")))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> { assertEquals(mapOf("userId" to "123"), it.data["to_arguments"]) },
        any(),
      )
  }

  @Test
  fun `onTopKeyChanged does not include empty arguments map`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `onTopKeyChanged does not capture breadcrumb when breadcrumbs disabled`() {
    val sut = fixture.getSut(enableBreadcrumbs = false)

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `onTopKeyChanged sets hint with nav3 destination key`() {
    val sut = fixture.getSut()
    val key = HomeScreen()

    sut.onTopKeyChanged(key, listOf(key))

    verify(fixture.scopes)
      .addBreadcrumb(
        any<Breadcrumb>(),
        check { assertEquals(key, it.get(TypeCheckHint.ANDROID_NAV3_DESTINATION)) },
      )
  }

  @Test
  fun `onTopKeyChanged includes from_arguments when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            is SettingsScreen -> mapOf("section" to key.section)
            else -> emptyMap()
          }
        }
      )

    val profile = ProfileScreen("123")
    val settings = SettingsScreen("privacy")
    sut.onTopKeyChanged(profile, listOf(profile))
    sut.onTopKeyChanged(settings, listOf(profile, settings))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals(mapOf("userId" to "123"), it.data["from_arguments"])
      assertEquals(mapOf("section" to "privacy"), it.data["to_arguments"])
    }
  }

  // endregion

  // region Tracing

  @Test
  fun `onTopKeyChanged starts transaction with route name`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        check {
          assertEquals("/HomeScreen", it.name)
          assertEquals("navigation", it.operation)
          assertEquals(TransactionNameSource.ROUTE, it.transactionNameSource)
        },
        any<TransactionOptions>(),
      )
  }

  @Test
  fun `onTopKeyChanged does not start transaction when tracing disabled`() {
    val sut = fixture.getSut(enableNavigationTracing = false)

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onTopKeyChanged does not start transaction when tracesSampleRate not set`() {
    val sut = fixture.getSut(enableNavigationTracing = true, tracesSampleRate = null)

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onTopKeyChanged finishes previous transaction before starting new one`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))
    sut.onTopKeyChanged(ProfileScreen("123"), listOf(HomeScreen(), ProfileScreen("123")))

    assertEquals(true, fixture.transaction.isFinished)
  }

  @Test
  fun `onTopKeyChanged captures arguments as transaction data`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onTopKeyChanged(ProfileScreen("123"), listOf(ProfileScreen("123")))

    val capturedArgs = fixture.transaction.data!!["arguments"]
    require(capturedArgs is Map<*, *>)
    assertEquals("123", capturedArgs["userId"])
  }

  @Test
  fun `onTopKeyChanged binds transaction to scope`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(null)
    verify(fixture.scope).transaction = fixture.transaction
  }

  @Test
  fun `onTopKeyChanged does not replace existing transaction on scope`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(mock())
    verify(fixture.scope, never()).transaction = fixture.transaction
  }

  @Test
  fun `onTopKeyChanged sets trace origin`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    assertEquals("auto.navigation.nav3", fixture.transaction.spanContext.origin)
  }

  @Test
  fun `onTopKeyChanged sets automatic deadline timeout`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options ->
          assertEquals(
            TransactionOptions.DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION,
            options.deadlineTimeout,
          )
        },
      )
  }

  @Test
  fun `onTopKeyChanged uses custom deadline timeout when set to positive value`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 60000L

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertEquals(60000L, options.deadlineTimeout) },
      )
  }

  @Test
  fun `onTopKeyChanged uses no deadline timeout when set to zero`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 0L

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertNull(options.deadlineTimeout) },
      )
  }

  @Test
  fun `onTopKeyChanged uses no deadline timeout when set to negative value`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = -1L

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertNull(options.deadlineTimeout) },
      )
  }

  @Test
  fun `starts new trace if performance is disabled`() {
    val sut = fixture.getSut(enableNavigationTracing = false)

    val argumentCaptor = org.mockito.ArgumentCaptor.forClass(ScopeCallback::class.java)
    val scope = Scope(fixture.options)
    val propagationContextAtStart = scope.propagationContext
    whenever(fixture.scopes.configureScope(argumentCaptor.capture())).thenAnswer {
      argumentCaptor.value.run(scope)
    }

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    assertNotSame(propagationContextAtStart, scope.propagationContext)
  }

  // endregion

  // region Screen tracking

  @Test
  fun `onTopKeyChanged sets scope screen`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scope).screen = "/HomeScreen"
  }

  @Test
  fun `onTopKeyChanged does not set scope screen when screen tracking disabled`() {
    val sut = fixture.getSut(enableScreenTracking = false)

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scope, never()).screen = any()
  }

  // endregion

  // region Backstack context

  @Suppress("UNCHECKED_CAST")
  private fun captureBackstack(): List<Map<String, Any?>> {
    val keyCaptor = argumentCaptor<String>()
    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope).setContexts(keyCaptor.capture(), valueCaptor.capture())
    assertEquals("navigation", keyCaptor.firstValue)
    val ctx = valueCaptor.firstValue as Map<String, Any?>
    return ctx["backstack"] as List<Map<String, Any?>>
  }

  @Test
  fun `onTopKeyChanged attaches backstack to scope as context`() {
    val sut = fixture.getSut()
    val backStack = listOf(HomeScreen(), ProfileScreen("123"))

    sut.onTopKeyChanged(ProfileScreen("123"), backStack)

    val stack = captureBackstack()
    assertEquals(2, stack.size)
    assertEquals("/HomeScreen", stack[0]["route"])
    assertEquals("/ProfileScreen", stack[1]["route"])
  }

  @Test
  fun `onTopKeyChanged caps backstack at maxBackstackSize`() {
    val sut = fixture.getSut(maxBackstackSize = 2)
    val backStack = listOf(HomeScreen(), ProfileScreen("1"), SettingsScreen("a"))

    sut.onTopKeyChanged(SettingsScreen("a"), backStack)

    val stack = captureBackstack()
    assertEquals(2, stack.size)
    assertEquals("/ProfileScreen", stack[0]["route"])
    assertEquals("/SettingsScreen", stack[1]["route"])
  }

  @Test
  fun `onTopKeyChanged includes arguments in backstack context when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onTopKeyChanged(ProfileScreen("123"), listOf(ProfileScreen("123")))

    val stack = captureBackstack()
    assertEquals(mapOf("userId" to "123"), stack[0]["args"])
  }

  @Test
  fun `onTopKeyChanged omits args field when arguments are empty`() {
    val sut = fixture.getSut()

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    val stack = captureBackstack()
    assertTrue(!stack[0].containsKey("args"))
  }

  @Test
  fun `onTopKeyChanged does not attach backstack when context disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onTopKeyChanged(HomeScreen(), listOf(HomeScreen()))

    verify(fixture.scope, never()).setContexts(any<String>(), any<Any>())
  }

  // endregion

  // region Name resolution

  @Test
  fun `resolveRouteName uses nameExtractor when provided`() {
    val sut = fixture.getSut(nameExtractor = { "custom" })

    assertEquals("/custom", sut.resolveRouteName(HomeScreen()))
  }

  @Test
  fun `resolveRouteName falls back to class simpleName when no extractor`() {
    val sut = fixture.getSut()

    assertEquals("/HomeScreen", sut.resolveRouteName(HomeScreen()))
  }

  @Test
  fun `resolveRouteName prepends slash to route name`() {
    val sut = fixture.getSut(nameExtractor = { "profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileScreen("123")))
  }

  // endregion
}
