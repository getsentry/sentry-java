package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Scope
import io.sentry.Scope.IWithTransaction
import io.sentry.ScopeCallback
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.Contexts
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

data class HomeScreen(val dummy: String = "")

data class ProfileScreen(val userId: String)

data class SettingsScreen(val section: String)

class SentryNavStateHolderTest {

  class Fixture {
    val scopes = mock<IScopes>()
    val scope = mock<IScope>()
    val logger = mock<ILogger>()
    lateinit var options: SentryOptions
    lateinit var navOptions: SentryNavOptions
    var nameExtractor: ((Any) -> String)? = null
    var argumentsExtractor: ((Any) -> Map<String, Any?>)? = null
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
          isDebug = true
          setLogger(logger)
        }
      whenever(scopes.options).thenReturn(options)

      this.transaction =
        transaction ?: SentryTracer(TransactionContext("/HomeScreen", "navigation"), scopes)

      whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
        .thenReturn(this.transaction)

      whenever(scopes.configureScope(any())).thenAnswer {
        (it.arguments[0] as ScopeCallback).run(scope)
      }
      whenever(scopes.getSpan()).thenAnswer { scope.span }
      whenever(scope.contexts).thenReturn(Contexts())

      navOptions =
        SentryNavOptions(
          enableNavigationBreadcrumbs = enableBreadcrumbs,
          enableNavigationTransactions = enableNavigationTracing,
          captureBackStack = enableBackstackContext,
          maxCapturedBackStackEntries = maxBackstackSize,
        )
      this.nameExtractor = nameExtractor
      this.argumentsExtractor = argumentsExtractor

      return SentryNavStateHolder(scopes = scopes)
    }
  }

  private val fixture = Fixture()

  private fun SentryNavOptions.with(
    enableNavigationTransactions: Boolean = this.enableNavigationTransactions,
    captureBackStack: Boolean = this.captureBackStack,
  ): SentryNavOptions =
    SentryNavOptions(
      enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
      enableNavigationTransactions = enableNavigationTransactions,
      captureBackStack = captureBackStack,
      maxCapturedBackStackEntries = maxCapturedBackStackEntries,
    )

  private fun SentryNavStateHolder<Any>.onBackStackChanged(backStack: List<Any>) {
    onBackStackChanged(
      backStack = backStack,
      options = fixture.navOptions,
      nameExtractor = fixture.nameExtractor,
      argumentsExtractor = fixture.argumentsExtractor,
    )
  }

  @Test
  fun `onBackStackChanged captures a breadcrumb`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

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
  fun `onBackStackChanged captures breadcrumb with from and to`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    sut.onBackStackChanged(listOf(home))
    sut.onBackStackChanged(listOf(home, profile))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals("/HomeScreen", it.data["from"])
      assertEquals("/ProfileScreen", it.data["to"])
    }
  }

  @Test
  fun `onBackStackChanged is idempotent for same backstack`() {
    val sut = fixture.getSut()
    val backStack = listOf<Any>(HomeScreen())

    sut.onBackStackChanged(backStack)
    sut.onBackStackChanged(backStack)

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    verify(fixture.scope, times(1)).setContexts(any<String>(), any<Any>())
  }

  @Test
  fun `onBackStackChanged resolves current entry once when capturing backstack`() {
    var nameExtractorCalls = 0
    var argumentsExtractorCalls = 0
    val sut =
      fixture.getSut(
        nameExtractor = {
          nameExtractorCalls++
          "profile"
        },
        argumentsExtractor = {
          argumentsExtractorCalls++
          emptyMap()
        },
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))

    assertEquals(1, nameExtractorCalls)
    assertEquals(1, argumentsExtractorCalls)
  }

  @Test
  fun `disabled backstack context does not resolve deeper entries`() {
    val resolvedNames = mutableListOf<Any>()
    val resolvedArguments = mutableListOf<Any>()
    val sut =
      fixture.getSut(
        enableBackstackContext = false,
        nameExtractor = { entry ->
          resolvedNames.add(entry)
          entry::class.simpleName ?: "unknown"
        },
        argumentsExtractor = { entry ->
          resolvedArguments.add(entry)
          emptyMap()
        },
      )
    val home = HomeScreen()
    val settings = SettingsScreen("privacy")
    val profile = ProfileScreen("123")

    sut.onBackStackChanged(listOf(home, settings, profile))

    assertEquals(listOf<Any>(profile), resolvedNames)
    assertEquals(listOf<Any>(profile), resolvedArguments)
  }

  @Test
  fun `onBackStackChanged includes arguments in breadcrumb when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> { assertEquals(mapOf("userId" to "123"), it.data["to_arguments"]) },
        any(),
      )
  }

  @Test
  fun `onBackStackChanged does not include empty arguments map`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `onBackStackChanged does not capture breadcrumb when breadcrumbs disabled`() {
    val sut = fixture.getSut(enableBreadcrumbs = false)

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `onBackStackChanged sets hint with nav3 destination key`() {
    val sut = fixture.getSut()
    val key = HomeScreen()

    sut.onBackStackChanged(listOf(key))

    verify(fixture.scopes)
      .addBreadcrumb(
        any<Breadcrumb>(),
        check { assertEquals(key, it.get(TypeCheckHint.NAV3_DESTINATION)) },
      )
  }

  @Test
  fun `onBackStackChanged includes from_arguments when extractor provided`() {
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
    sut.onBackStackChanged(listOf(profile))
    sut.onBackStackChanged(listOf(profile, settings))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals(mapOf("userId" to "123"), it.data["from_arguments"])
      assertEquals(mapOf("section" to "privacy"), it.data["to_arguments"])
    }
  }

  @Test
  fun `enabling breadcrumbs preserves previously unused from arguments`() {
    val sut =
      fixture.getSut(
        enableBreadcrumbs = false,
        enableNavigationTracing = false,
        enableBackstackContext = false,
        argumentsExtractor = { entry ->
          when (entry) {
            is ProfileScreen -> mapOf("userId" to entry.userId)
            is SettingsScreen -> mapOf("section" to entry.section)
            else -> emptyMap()
          }
        },
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))
    fixture.navOptions =
      SentryNavOptions(
        enableNavigationBreadcrumbs = true,
        enableNavigationTransactions = false,
        captureBackStack = false,
      )
    sut.onBackStackChanged(listOf(SettingsScreen("privacy")))

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes).addBreadcrumb(breadcrumbCaptor.capture(), any())
    assertThat(breadcrumbCaptor.firstValue.data["from_arguments"])
      .isEqualTo(mapOf("userId" to "123"))
  }

  @Test
  fun `onBackStackChanged starts transaction with route name`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

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
  fun `onBackStackChanged does not start transaction when tracing disabled`() {
    val sut = fixture.getSut(enableNavigationTracing = false)

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackStackChanged does not start transaction when tracesSampleRate not set`() {
    val sut = fixture.getSut(enableNavigationTracing = true, tracesSampleRate = null)

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackStackChanged does not start transaction when another span is active`() {
    val sut = fixture.getSut()
    whenever(fixture.scope.span).thenReturn(mock<ISpan>())

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scope).screen = "/HomeScreen"
    verify(fixture.scope).setContexts(any<String>(), any<Any>())
  }

  @Test
  fun `onBackStackChanged finishes previous transaction before starting new one`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    assertEquals(true, fixture.transaction.isFinished)
  }

  @Test
  fun `onBackStackChanged captures arguments as transaction data`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))

    val capturedArgs = fixture.transaction.data!!["arguments"]
    require(capturedArgs is Map<*, *>)
    assertEquals("123", capturedArgs["userId"])
  }

  @Test
  fun `onBackStackChanged rotates when top entry changes with same route name`() {
    val sut =
      fixture.getSut(
        nameExtractor = { "Profile" },
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        },
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))
    sut.onBackStackChanged(listOf(ProfileScreen("456")))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    verify(fixture.scopes, times(2))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    captor.secondValue.let {
      assertEquals("/Profile", it.data["from"])
      assertEquals("/Profile", it.data["to"])
      assertEquals(mapOf("userId" to "123"), it.data["from_arguments"])
      assertEquals(mapOf("userId" to "456"), it.data["to_arguments"])
    }
  }

  @Test
  fun `onBackStackChanged binds transaction to scope`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(null)
    verify(fixture.scope).transaction = fixture.transaction
  }

  @Test
  fun `onBackStackChanged does not replace existing transaction on scope`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(mock())
    verify(fixture.scope, never()).transaction = fixture.transaction
  }

  @Test
  fun `onBackStackChanged sets trace origin`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertEquals("auto.navigation.nav3", fixture.transaction.spanContext.origin)
  }

  @Test
  fun `onBackStackChanged sets automatic deadline timeout`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

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
  fun `onBackStackChanged uses custom deadline timeout when set to positive value`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 60000L

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertEquals(60000L, options.deadlineTimeout) },
      )
  }

  @Test
  fun `onBackStackChanged uses no deadline timeout when set to zero`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 0L

    sut.onBackStackChanged(listOf(HomeScreen()))

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

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertNotSame(propagationContextAtStart, scope.propagationContext)
  }

  @Test
  fun `onBackStackChanged sets scope screen and view names`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scope).screen = "/HomeScreen"
    assertEquals(listOf("/HomeScreen"), fixture.scope.contexts.app?.viewNames)
  }

  @Test
  fun `onBackStackChanged does not set scope screen when screen tracking disabled`() {
    val sut = fixture.getSut(enableScreenTracking = false)

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scope, never()).screen = any()
  }

  @Test
  fun `extractor change updates screen and context without new navigation event`() {
    val sut = fixture.getSut()
    val backStack = listOf<Any>(HomeScreen())

    sut.onBackStackChanged(backStack)
    clearInvocations(fixture.scopes, fixture.scope)
    fixture.nameExtractor = { "custom-home" }

    sut.onBackStackChanged(backStack)

    verify(fixture.scope).screen = "/custom-home"
    verify(fixture.scope).setContexts(any<String>(), any<Any>())
    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Suppress("UNCHECKED_CAST")
  private fun captureBackstackContext(): List<Map<String, Any?>> {
    val keyCaptor = argumentCaptor<String>()
    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope).setContexts(keyCaptor.capture(), valueCaptor.capture())
    assertEquals("navigation", keyCaptor.firstValue)
    val navigation = valueCaptor.firstValue as Map<String, Any?>
    return navigation["backstack"] as List<Map<String, Any?>>
  }

  @Test
  fun `onBackStackChanged attaches backstack to scope as context`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    val stack = captureBackstackContext()
    assertEquals(2, stack.size)
    assertEquals("/ProfileScreen", stack[0]["route"])
    assertEquals("/HomeScreen", stack[1]["route"])
  }

  @Test
  fun `onBackStackChanged caps backstack at maxBackstackSize`() {
    val sut = fixture.getSut(maxBackstackSize = 2)

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("1"), SettingsScreen("a")))

    val stack = captureBackstackContext()
    assertEquals(2, stack.size)
    assertEquals("/SettingsScreen", stack[0]["route"])
    assertEquals("/ProfileScreen", stack[1]["route"])
  }

  @Test
  fun `onBackStackChanged includes arguments in backstack context when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackStackChanged(listOf(ProfileScreen("123")))

    val stack = captureBackstackContext()
    assertEquals(mapOf("userId" to "123"), stack[0]["args"])
  }

  @Test
  fun `onBackStackChanged omits args field when arguments are empty`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))

    val stack = captureBackstackContext()
    assertTrue(!stack[0].containsKey("args"))
  }

  @Test
  fun `onBackStackChanged does not attach backstack when context disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scope, never()).setContexts(any<String>(), any<Any>())
    verify(fixture.scope, never()).removeContexts("navigation")
  }

  @Test
  fun `onBackStackChanged refreshes backstack context when deeper entries change`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))
    sut.onBackStackChanged(listOf(HomeScreen(), SettingsScreen("privacy"), ProfileScreen("123")))

    val keyCaptor = argumentCaptor<String>()
    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope, times(2)).setContexts(keyCaptor.capture(), valueCaptor.capture())

    @Suppress("UNCHECKED_CAST") val secondCtx = valueCaptor.secondValue as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") val stack = secondCtx["backstack"] as List<Map<String, Any?>>
    assertEquals(3, stack.size)
    assertEquals("/ProfileScreen", stack[0]["route"])
    assertEquals("/SettingsScreen", stack[1]["route"])
    assertEquals("/HomeScreen", stack[2]["route"])
  }

  @Test
  fun `disabling backstack context removes navigation context once`() {
    val sut = fixture.getSut()
    val backStack = listOf<Any>(HomeScreen())

    sut.onBackStackChanged(backStack)
    clearInvocations(fixture.scope)
    fixture.navOptions = fixture.navOptions.with(captureBackStack = false)

    sut.onBackStackChanged(backStack)
    sut.onBackStackChanged(backStack)

    verify(fixture.scope, times(1)).removeContexts("navigation")
    verify(fixture.scope, never()).setContexts(any<String>(), any<Any>())
  }

  @Test
  fun `re-enabling backstack context restores navigation context`() {
    val sut = fixture.getSut(enableBackstackContext = false)
    val backStack = listOf<Any>(HomeScreen())

    sut.onBackStackChanged(backStack)
    clearInvocations(fixture.scope)
    fixture.navOptions = fixture.navOptions.with(captureBackStack = true)

    sut.onBackStackChanged(backStack)

    verify(fixture.scope).setContexts(any<String>(), any<Any>())
  }

  @Test
  fun `disabling navigation transactions finishes active transaction immediately`() {
    val sut = fixture.getSut()
    val backStack = listOf<Any>(HomeScreen())

    sut.onBackStackChanged(backStack)
    fixture.navOptions = fixture.navOptions.with(enableNavigationTransactions = false)
    sut.onBackStackChanged(backStack)

    assertEquals(true, fixture.transaction.isFinished)
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackStackChanged does not fire breadcrumb when only deeper entries change`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))
    sut.onBackStackChanged(listOf(HomeScreen(), SettingsScreen("privacy"), ProfileScreen("123")))

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `cleanup finishes active transaction`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    sut.cleanup()

    assertEquals(true, fixture.transaction.isFinished)
  }

  @Test
  fun `cleanup removes backstack context from scope`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    sut.cleanup()

    verify(fixture.scope).removeContexts("navigation")
  }

  @Test
  fun `cleanup does not remove backstack context when backstack context disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onBackStackChanged(listOf(HomeScreen()))
    clearInvocations(fixture.scope)
    sut.cleanup()

    verify(fixture.scope, never()).removeContexts(any())
  }

  @Test
  fun `empty backstack does not capture breadcrumb`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(emptyList())

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `empty backstack clears backstack context`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    sut.onBackStackChanged(emptyList())

    verify(fixture.scope).removeContexts("navigation")
  }

  @Test
  fun `empty backstack finishes transaction and clears screen`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    clearInvocations(fixture.scope)
    whenever(fixture.scope.screen).thenReturn("/HomeScreen")
    sut.onBackStackChanged(emptyList())

    assertEquals(true, fixture.transaction.isFinished)
    verify(fixture.scope).screen = null
    verify(fixture.scope).removeContexts("navigation")
  }

  @Test
  fun `resolveRouteName uses nameExtractor when provided`() {
    val sut = fixture.getSut(nameExtractor = { "custom" })

    assertEquals("/custom", sut.resolveRouteName(HomeScreen(), fixture.nameExtractor))
  }

  @Test
  fun `resolveRouteName falls back to class simpleName when no extractor`() {
    val sut = fixture.getSut()

    assertEquals("/HomeScreen", sut.resolveRouteName(HomeScreen()))
  }

  @Test
  fun `resolveRouteName prepends slash to route name`() {
    val sut = fixture.getSut(nameExtractor = { "profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileScreen("123"), fixture.nameExtractor))
  }

  @Test
  fun `resolveRouteName does not double slash when extractor returns leading slash`() {
    val sut = fixture.getSut(nameExtractor = { "/profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileScreen("123"), fixture.nameExtractor))
  }

  @Test
  fun `arguments with primitive values pass through unchanged`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { _ ->
          mapOf("str" to "hello", "num" to 42, "bool" to true, "nil" to null)
        }
      )

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          assertEquals("hello", args["str"])
          assertEquals(42, args["num"])
          assertEquals(true, args["bool"])
          assertNull(args["nil"])
        },
        any(),
      )
  }

  @Test
  fun `arguments with nested map are sanitized recursively`() {
    val sut =
      fixture.getSut(argumentsExtractor = { _ -> mapOf("nested" to mapOf("inner" to "value")) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          assertEquals(mapOf("inner" to "value"), args["nested"])
        },
        any(),
      )
  }

  @Test
  fun `arguments with collection are sanitized recursively`() {
    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("tags" to listOf("a", "b", "c")) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          assertEquals(listOf("a", "b", "c"), args["tags"])
        },
        any(),
      )
  }

  @Test
  fun `cyclic arguments are skipped without overflowing the stack`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic
    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("cyclic" to cyclic) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `arguments exceeding maximum depth are skipped`() {
    var nested: Any? = "value"
    repeat(25) { nested = listOf(nested) }
    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("nested" to nested) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `arguments exceeding maximum value count are skipped`() {
    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("values" to List(1_001) { it }) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `arguments at maximum value count are retained`() {
    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("values" to List(999) { it }) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          @Suppress("UNCHECKED_CAST") val values = args["values"] as List<Int>
          assertThat(values).hasSize(999)
        },
        any(),
      )
  }

  @Test
  fun `shared argument budget preserves current entry and skips older entry arguments`() {
    var homeCalls = 0
    var profileCalls = 0
    var settingsCalls = 0
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is HomeScreen -> {
              homeCalls++
              mapOf("home" to true)
            }

            is ProfileScreen -> {
              profileCalls++
              mapOf("values" to List(999) { it })
            }

            is SettingsScreen -> {
              settingsCalls++
              mapOf("section" to key.section)
            }

            else -> emptyMap()
          }
        }
      )

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123"), SettingsScreen("privacy")))

    assertThat(settingsCalls).isEqualTo(1)
    assertThat(profileCalls).isEqualTo(1)
    assertThat(homeCalls).isEqualTo(0)

    val stack = captureBackstackContext()
    assertEquals(mapOf("section" to "privacy"), stack[0]["args"])
    assertTrue(!stack[1].containsKey("args"))
    assertTrue(!stack[2].containsKey("args"))
  }

  @Test
  fun `cyclic older entry arguments do not prevent later captured entry arguments`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is HomeScreen -> mapOf("home" to true)
            is ProfileScreen -> mapOf("cyclic" to cyclic)
            is SettingsScreen -> mapOf("section" to key.section)
            else -> emptyMap()
          }
        }
      )

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123"), SettingsScreen("privacy")))

    val stack = captureBackstackContext()
    assertEquals(mapOf("section" to "privacy"), stack[0]["args"])
    assertTrue(!stack[1].containsKey("args"))
    assertEquals(mapOf("home" to true), stack[2]["args"])
  }

  @Test
  fun `arguments with non-primitive object are coerced to toString`() {
    class OpaqueObject {
      override fun toString(): String = "opaque-value"
    }

    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("obj" to OpaqueObject()) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          assertEquals("opaque-value", args["obj"])
        },
        any(),
      )
  }

  @Test
  fun `nameExtractor that throws falls back to class simpleName without crashing`() {
    val sut = fixture.getSut(nameExtractor = { error("boom") })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertEquals("/HomeScreen", it.data["to"]) }, any())
  }

  @Test
  fun `nameExtractor rethrows fatal errors before configuring scope`() {
    val fatal = OutOfMemoryError("fatal")
    val sut = fixture.getSut(nameExtractor = { throw fatal })

    val thrown = assertFailsWith<OutOfMemoryError> { sut.onBackStackChanged(listOf(HomeScreen())) }

    assertThat(thrown).isSameInstanceAs(fatal)
    verify(fixture.scopes, never()).configureScope(any())
  }

  @Test
  fun `argumentsExtractor that throws skips arguments without crashing`() {
    val sut = fixture.getSut(argumentsExtractor = { error("boom") })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `argument sanitization failure skips arguments without crashing`() {
    class ExplodingValue {
      override fun toString(): String = error("boom")
    }

    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("bad" to ExplodingValue()) })

    sut.onBackStackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  private class ExplodingKey {
    override fun equals(other: Any?): Boolean = error("equals boom")

    override fun hashCode(): Int = error("hashCode boom")

    override fun toString(): String = error("toString boom")
  }

  @Test
  fun `onBackStackChanged does not crash when a key equals throws`() {
    val sut = fixture.getSut()
    val first = ExplodingKey()
    val second = ExplodingKey()

    sut.onBackStackChanged(listOf(first))
    sut.onBackStackChanged(listOf(first, second))
    assertTrue(first !== second)
  }

  @Test
  fun `valid navigation after cleanup recovers from a previously throwing key`() {
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(ExplodingKey()))
    sut.cleanup()

    sut.onBackStackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    assertEquals("/HomeScreen", captor.lastValue.data["to"])
  }
}
