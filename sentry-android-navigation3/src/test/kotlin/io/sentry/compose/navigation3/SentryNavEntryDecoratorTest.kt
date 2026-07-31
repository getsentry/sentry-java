package io.sentry.compose.navigation3

import io.sentry.Breadcrumb
import io.sentry.ILogger
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
import io.sentry.protocol.Contexts
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

data class HomeScreen(val dummy: String = "")

data class ProfileScreen(val userId: String)

data class SettingsScreen(val section: String)

@Suppress("LargeClass") // Region-grouped behavioral coverage for a single state holder.
class SentryNavEntryDecoratorTest {

  class Fixture {
    val scopes = mock<IScopes>()
    val scope = mock<IScope>()
    val logger = mock<ILogger>()
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
      primaryRouteSelector: ((List<SentryNavVisibleEntry<Any>>) -> SentryNavVisibleEntry<Any>?)? =
        null,
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
      whenever(scope.contexts).thenReturn(Contexts())

      return SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
        primaryRouteSelector = primaryRouteSelector,
      )
    }
  }

  private val fixture = Fixture()

  // region Breadcrumbs

  @Test
  fun `onBackstackChanged captures a breadcrumb`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

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
  fun `onBackstackChanged captures breadcrumb with from and to`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    sut.onBackstackChanged(listOf(home))
    sut.onBackstackChanged(listOf(home, profile))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals("/HomeScreen", it.data["from"])
      assertEquals("/ProfileScreen", it.data["to"])
    }
  }

  @Test
  fun `onBackstackChanged includes arguments in breadcrumb when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackstackChanged(listOf(ProfileScreen("123")))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> { assertEquals(mapOf("userId" to "123"), it.data["to_arguments"]) },
        any(),
      )
  }

  @Test
  fun `onBackstackChanged does not include empty arguments map`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `onBackstackChanged does not capture breadcrumb when breadcrumbs disabled`() {
    val sut = fixture.getSut(enableBreadcrumbs = false)

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `onBackstackChanged sets hint with nav3 destination key`() {
    val sut = fixture.getSut()
    val key = HomeScreen()

    sut.onBackstackChanged(listOf(key))

    verify(fixture.scopes)
      .addBreadcrumb(
        any<Breadcrumb>(),
        check { assertEquals(key, it.get(TypeCheckHint.NAV3_DESTINATION)) },
      )
  }

  @Test
  fun `onBackstackChanged includes from_arguments when extractor provided`() {
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
    sut.onBackstackChanged(listOf(profile))
    sut.onBackstackChanged(listOf(profile, settings))

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
  fun `onBackstackChanged starts transaction with route name`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

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
  fun `onBackstackChanged does not start transaction when tracing disabled`() {
    val sut = fixture.getSut(enableNavigationTracing = false)

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackstackChanged does not start transaction when tracesSampleRate not set`() {
    val sut = fixture.getSut(enableNavigationTracing = true, tracesSampleRate = null)

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackstackChanged finishes previous transaction before starting new one`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.onBackstackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    assertEquals(true, fixture.transaction.isFinished)
  }

  @Test
  fun `onBackstackChanged captures arguments as transaction data`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackstackChanged(listOf(ProfileScreen("123")))

    val capturedArgs = fixture.transaction.data!!["arguments"]
    require(capturedArgs is Map<*, *>)
    assertEquals("123", capturedArgs["userId"])
  }

  @Test
  fun `onBackstackChanged binds transaction to scope`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(null)
    verify(fixture.scope).transaction = fixture.transaction
  }

  @Test
  fun `onBackstackChanged does not replace existing transaction on scope`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<IWithTransaction>()
    verify(fixture.scope).withTransaction(captor.capture())
    captor.firstValue.accept(mock())
    verify(fixture.scope, never()).transaction = fixture.transaction
  }

  @Test
  fun `onBackstackChanged sets trace origin`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    assertEquals("auto.navigation.nav3", fixture.transaction.spanContext.origin)
  }

  @Test
  fun `onBackstackChanged sets automatic deadline timeout`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

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
  fun `onBackstackChanged uses custom deadline timeout when set to positive value`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 60000L

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertEquals(60000L, options.deadlineTimeout) },
      )
  }

  @Test
  fun `onBackstackChanged uses no deadline timeout when set to zero`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = 0L

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(
        any<TransactionContext>(),
        check<TransactionOptions> { options -> assertNull(options.deadlineTimeout) },
      )
  }

  @Test
  fun `onBackstackChanged uses no deadline timeout when set to negative value`() {
    val sut = fixture.getSut()
    fixture.options.deadlineTimeout = -1L

    sut.onBackstackChanged(listOf(HomeScreen()))

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

    sut.onBackstackChanged(listOf(HomeScreen()))

    assertNotSame(propagationContextAtStart, scope.propagationContext)
  }

  // endregion

  // region Screen tracking

  @Test
  fun `onBackstackChanged sets scope screen`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scope).screen = "/HomeScreen"
  }

  @Test
  fun `onBackstackChanged does not set scope screen when screen tracking disabled`() {
    val sut = fixture.getSut(enableScreenTracking = false)

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scope, never()).screen = any()
  }

  // endregion

  // region Backstack context

  @Suppress("UNCHECKED_CAST")
  private fun captureNavigationContext(): Map<String, Any?> {
    val keyCaptor = argumentCaptor<String>()
    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope).setContexts(keyCaptor.capture(), valueCaptor.capture())
    assertEquals("navigation", keyCaptor.firstValue)
    return valueCaptor.firstValue as Map<String, Any?>
  }

  @Suppress("UNCHECKED_CAST")
  private fun captureDefaultStackEntries(): List<Map<String, Any?>> {
    val ctx = captureNavigationContext()
    assertEquals("default", ctx["selected_stack"])
    assertEquals(listOf("default"), ctx["stacks_in_use"])
    val backstacks = ctx["backstacks"] as List<Map<String, Any?>>
    assertEquals(1, backstacks.size)
    val defaultStack = backstacks[0]
    assertEquals("default", defaultStack["name"])
    assertEquals(true, defaultStack["selected"])
    assertEquals(true, defaultStack["in_use"])
    return defaultStack["backstack"] as List<Map<String, Any?>>
  }

  @Test
  fun `onBackstackChanged attaches default backstack to scope as context`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    val stack = captureDefaultStackEntries()
    assertEquals(2, stack.size)
    assertEquals("/HomeScreen", stack[0]["route"])
    assertEquals("/ProfileScreen", stack[1]["route"])
  }

  @Test
  fun `onBackstackChanged caps backstack at maxBackstackSize`() {
    val sut = fixture.getSut(maxBackstackSize = 2)

    sut.onBackstackChanged(listOf(HomeScreen(), ProfileScreen("1"), SettingsScreen("a")))

    val stack = captureDefaultStackEntries()
    assertEquals(2, stack.size)
    assertEquals("/ProfileScreen", stack[0]["route"])
    assertEquals("/SettingsScreen", stack[1]["route"])
  }

  @Test
  fun `onBackstackChanged includes arguments in backstack context when extractor provided`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackstackChanged(listOf(ProfileScreen("123")))

    val stack = captureDefaultStackEntries()
    assertEquals(mapOf("userId" to "123"), stack[0]["args"])
  }

  @Test
  fun `onBackstackChanged omits args field when arguments are empty`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))

    val stack = captureDefaultStackEntries()
    assertTrue(!stack[0].containsKey("args"))
  }

  @Test
  fun `onBackstackChanged does not attach backstack when context disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scope, never()).setContexts(any<String>(), any<Any>())
  }

  @Test
  fun `onBackstackChanged refreshes backstack context when deeper entries change`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen(), ProfileScreen("123")))
    sut.onBackstackChanged(listOf(HomeScreen(), SettingsScreen("privacy"), ProfileScreen("123")))

    val keyCaptor = argumentCaptor<String>()
    val valueCaptor = argumentCaptor<Any>()
    verify(fixture.scope, times(2)).setContexts(keyCaptor.capture(), valueCaptor.capture())

    @Suppress("UNCHECKED_CAST") val secondCtx = valueCaptor.secondValue as Map<String, Any?>
    assertEquals("default", secondCtx["selected_stack"])
    assertEquals(listOf("default"), secondCtx["stacks_in_use"])
    @Suppress("UNCHECKED_CAST") val backstacks = secondCtx["backstacks"] as List<Map<String, Any?>>
    assertEquals(1, backstacks.size)
    assertEquals("default", backstacks[0]["name"])
    assertEquals(true, backstacks[0]["selected"])
    assertEquals(true, backstacks[0]["in_use"])
    @Suppress("UNCHECKED_CAST") val stack = backstacks[0]["backstack"] as List<Map<String, Any?>>
    assertEquals(3, stack.size)
    assertEquals("/HomeScreen", stack[0]["route"])
    assertEquals("/SettingsScreen", stack[1]["route"])
    assertEquals("/ProfileScreen", stack[2]["route"])
  }

  @Test
  fun `onBackstackChanged does not fire breadcrumb when only deeper entries change`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen(), ProfileScreen("123")))
    sut.onBackstackChanged(listOf(HomeScreen(), SettingsScreen("privacy"), ProfileScreen("123")))

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `onBackstacksChanged attaches retained stacks to navigation context`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { key ->
          when (key) {
            is ProfileScreen -> mapOf("userId" to key.userId)
            else -> emptyMap()
          }
        }
      )

    sut.onBackstacksChanged(
      selectedStack = "mail",
      backStacks =
        linkedMapOf(
          "home" to listOf(HomeScreen()),
          "mail" to listOf(ProfileScreen("123")),
        ),
      stacksInUse = linkedSetOf("home", "mail"),
      stackNameExtractor = { stack -> "tab-$stack" },
    )

    val ctx = captureNavigationContext()
    assertEquals("tab-mail", ctx["selected_stack"])
    assertEquals(listOf("tab-home", "tab-mail"), ctx["stacks_in_use"])
    @Suppress("UNCHECKED_CAST") val backstacks = ctx["backstacks"] as List<Map<String, Any?>>
    assertEquals(2, backstacks.size)
    assertEquals("tab-home", backstacks[0]["name"])
    assertEquals(false, backstacks[0]["selected"])
    assertEquals(true, backstacks[0]["in_use"])
    assertEquals("tab-mail", backstacks[1]["name"])
    assertEquals(true, backstacks[1]["selected"])
    assertEquals(true, backstacks[1]["in_use"])
    @Suppress("UNCHECKED_CAST")
    val mailStack = backstacks[1]["backstack"] as List<Map<String, Any?>>
    assertEquals("/ProfileScreen", mailStack[0]["route"])
    assertEquals(mapOf("userId" to "123"), mailStack[0]["args"])
  }

  @Test
  fun `onBackstacksChanged treats selected stack switch as navigation`() {
    val sut = fixture.getSut()
    val homeBackStack = listOf(HomeScreen())
    val mailBackStack = listOf(ProfileScreen("123"))
    val backStacks = linkedMapOf("home" to homeBackStack, "mail" to mailBackStack)

    sut.onBackstacksChanged(
      selectedStack = "home",
      backStacks = backStacks,
      stacksInUse = setOf("home"),
      stackNameExtractor = null,
    )
    sut.onBackstacksChanged(
      selectedStack = "mail",
      backStacks = backStacks,
      stacksInUse = setOf("mail"),
      stackNameExtractor = null,
    )

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, times(2)).addBreadcrumb(captor.capture(), any())
    captor.secondValue.let {
      assertEquals("/HomeScreen", it.data["from"])
      assertEquals("/ProfileScreen", it.data["to"])
    }
    verify(fixture.scope, atLeastOnce()).setScreen(eq("/ProfileScreen"))
    verify(fixture.scopes, times(2))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `onBackstacksChanged refreshes inactive stacks without changing primary route`() {
    val sut = fixture.getSut()

    sut.onBackstacksChanged(
      selectedStack = "home",
      backStacks =
        linkedMapOf(
          "home" to listOf(HomeScreen()),
          "mail" to listOf(ProfileScreen("123")),
        ),
      stacksInUse = setOf("home"),
      stackNameExtractor = null,
    )
    sut.onBackstacksChanged(
      selectedStack = "home",
      backStacks =
        linkedMapOf(
          "home" to listOf(HomeScreen()),
          "mail" to listOf(ProfileScreen("123"), SettingsScreen("privacy")),
        ),
      stacksInUse = setOf("home"),
      stackNameExtractor = null,
    )

    @Suppress("UNCHECKED_CAST") val contextCaptor = argumentCaptor<Map<String, Any>>()
    verify(fixture.scope, times(2)).setContexts(eq("navigation"), contextCaptor.capture())
    val latest = contextCaptor.lastValue
    @Suppress("UNCHECKED_CAST") val backstacks = latest["backstacks"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val inactiveStack = backstacks[1]["backstack"] as List<Map<String, Any?>>
    assertEquals(2, inactiveStack.size)
    assertEquals("/SettingsScreen", inactiveStack[1]["route"])
    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    verify(fixture.scope, never()).setScreen(eq("/SettingsScreen"))
  }

  // endregion

  // region Cleanup

  @Test
  fun `cleanup finishes active transaction`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.cleanup()

    assertEquals(true, fixture.transaction.isFinished)
  }

  @Test
  fun `cleanup does not start new transaction after finishing`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.cleanup()

    verify(fixture.scopes, times(1))
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `cleanup is safe to call when no active transaction`() {
    val sut = fixture.getSut()

    sut.cleanup()

    verify(fixture.scopes, never())
      .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `cleanup is safe to call when tracing is disabled`() {
    val sut = fixture.getSut(enableNavigationTracing = false)

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.cleanup()

    verify(fixture.scope, never()).clearTransaction()
  }

  @Test
  fun `cleanup removes backstack context from scope`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.cleanup()

    verify(fixture.scope).removeContexts("navigation")
  }

  @Test
  fun `cleanup does not remove backstack context when backstack context disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.cleanup()

    verify(fixture.scope, never()).removeContexts(any())
  }

  @Test
  fun `empty backstack does not affect active transaction`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.onBackstackChanged(emptyList())

    assertEquals(false, fixture.transaction.isFinished)
  }

  @Test
  fun `empty backstack does not capture breadcrumb`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(emptyList())

    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `empty backstack clears backstack context`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.onBackstackChanged(emptyList())

    verify(fixture.scope).removeContexts("navigation")
  }

  @Test
  fun `empty backstack does not clear backstack context when disabled`() {
    val sut = fixture.getSut(enableBackstackContext = false)

    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.onBackstackChanged(emptyList())

    verify(fixture.scope, never()).removeContexts(any())
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

  @Test
  fun `resolveRouteName does not double slash when extractor returns leading slash`() {
    val sut = fixture.getSut(nameExtractor = { "/profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileScreen("123")))
  }

  // endregion

  // region Argument sanitization

  @Test
  fun `arguments with primitive values pass through unchanged`() {
    val sut =
      fixture.getSut(
        argumentsExtractor = { _ ->
          mapOf("str" to "hello", "num" to 42, "bool" to true, "nil" to null)
        }
      )

    sut.onBackstackChanged(listOf(HomeScreen()))

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

    sut.onBackstackChanged(listOf(HomeScreen()))

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

    sut.onBackstackChanged(listOf(HomeScreen()))

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
  fun `arguments with non-primitive object are coerced to toString`() {
    class OpaqueObject {
      override fun toString(): String = "opaque-value"
    }

    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("obj" to OpaqueObject()) })

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          @Suppress("UNCHECKED_CAST") val args = it.data["to_arguments"] as Map<String, Any?>
          assertEquals("opaque-value", args["obj"])
        },
        any(),
      )
  }

  // endregion

  // region Extractor error handling

  @Test
  fun `nameExtractor that throws falls back to class simpleName without crashing`() {
    val sut = fixture.getSut(nameExtractor = { error("boom") })

    // Must not propagate the exception out of instrumentation.
    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertEquals("/HomeScreen", it.data["to"]) }, any())
  }

  @Test
  fun `nameExtractor that throws still starts transaction with fallback route name`() {
    val sut = fixture.getSut(nameExtractor = { error("boom") })

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .startTransaction(check { assertEquals("/HomeScreen", it.name) }, any<TransactionOptions>())
  }

  @Test
  fun `argumentsExtractor that throws skips arguments without crashing`() {
    val sut = fixture.getSut(argumentsExtractor = { error("boom") })

    // Must not propagate the exception out of instrumentation.
    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  @Test
  fun `argument sanitization failure skips arguments without crashing`() {
    // A value whose toString() throws forces sanitizeValue to fail, which must be caught and
    // degrade to no arguments rather than crashing the host app.
    class ExplodingValue {
      override fun toString(): String = error("boom")
    }

    val sut = fixture.getSut(argumentsExtractor = { _ -> mapOf("bad" to ExplodingValue()) })

    sut.onBackstackChanged(listOf(HomeScreen()))

    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertNull(it.data["to_arguments"]) }, any())
  }

  // endregion

  // region Callback guard against throwing host key methods

  /**
   * A backstack key whose [equals], [hashCode] and [toString] all throw. Unlike a throwing
   * extractor lambda (covered above), these methods are invoked outside the extractor guards — in
   * map lookups, equality checks and string coercion — so they exercise the outermost callback
   * guard.
   */
  private class ExplodingKey {
    override fun equals(other: Any?): Boolean = error("equals boom")

    override fun hashCode(): Int = error("hashCode boom")

    override fun toString(): String = error("toString boom")
  }

  @Test
  fun `onBackstackChanged does not crash when a key equals throws`() {
    val sut = fixture.getSut()
    // Keep strong references so the holder's WeakReference to the previous primary key cannot be
    // collected before the second call's equality check exercises the throwing equals/hashCode.
    val first = ExplodingKey()
    val second = ExplodingKey()

    sut.onBackstackChanged(listOf(first))
    sut.onBackstackChanged(listOf(first, second))
    assertTrue(first !== second)

    // No exception escaped; the test would otherwise fail.
  }

  @Test
  fun `onEntryVisible does not crash when a content key throws`() {
    val sut = fixture.getSut()

    val ek = ExplodingKey()
    sut.onBackstackChanged(listOf(ek))

    // visiblePanes map insertion invokes hashCode on the throwing content key.
    sut.onEntryVisible(ek, emptyMap())

    // No exception escaped; the test would otherwise fail.
  }

  @Test
  fun `onEntryHidden does not crash when a content key throws`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    sut.onBackstackChanged(listOf(home))
    sut.onEntryVisible(home, emptyMap())

    // visiblePanes.remove invokes hashCode on the throwing content key once the map is initialized.
    sut.onEntryHidden(ExplodingKey())

    // No exception escaped; the test would otherwise fail.
  }

  @Test
  fun `onEntryPopped does not crash when a content key throws`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    sut.onBackstackChanged(listOf(home))
    sut.onEntryVisible(home, emptyMap())

    sut.onEntryPopped(ExplodingKey())

    // No exception escaped; the test would otherwise fail.
  }

  @Test
  fun `callback guard degrades gracefully without crashing across mixed navigation`() {
    val sut = fixture.getSut()

    // Interleave throwing and well-behaved navigation; the guard must swallow every failure rather
    // than letting any of these calls propagate an exception to the host.
    val exploding = ExplodingKey()
    sut.onBackstackChanged(listOf(exploding))
    sut.onEntryVisible(exploding, emptyMap())
    val home = HomeScreen()
    sut.onBackstackChanged(listOf(home))
    sut.onEntryVisible(home, emptyMap())
    sut.onEntryHidden(ExplodingKey())
    sut.onEntryPopped(ExplodingKey())

    // No exception escaped; the test would otherwise fail.
  }

  @Test
  fun `valid navigation after cleanup recovers from a previously throwing key`() {
    val sut = fixture.getSut()

    sut.onBackstackChanged(listOf(ExplodingKey()))
    // cleanup clears the stale throwing previous-primary key so later equality checks are safe.
    sut.cleanup()

    sut.onBackstackChanged(listOf(HomeScreen()))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, atLeastOnce()).addBreadcrumb(captor.capture(), any())
    assertEquals("/HomeScreen", captor.lastValue.data["to"])
  }

  // endregion

  // region Multipane

  @Test
  fun `selectPrimaryPane prefers detail metadata over list`() {
    val sut = fixture.getSut()
    val list = HomeScreen()
    val detail = ProfileScreen("1")

    val primary =
      sut.selectPrimaryPane(
        listOf(
          VisiblePane(list, "list", mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_LIST), null),
          VisiblePane(
            detail,
            "detail",
            mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_DETAIL),
            null,
          ),
        )
      )

    assertEquals(detail, primary?.key)
  }

  @Test
  fun `selectPrimaryPane prefers selected stack when metadata is inconclusive`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val mail = ProfileScreen("1")

    sut.onBackstacksChanged(
      selectedStack = "mail",
      backStacks = linkedMapOf("home" to listOf(home), "mail" to listOf(mail)),
      stacksInUse = linkedSetOf("home", "mail"),
      stackNameExtractor = null,
    )

    val primary =
      sut.selectPrimaryPane(
        listOf(
          VisiblePane(home, "home", emptyMap(), "home"),
          VisiblePane(mail, "mail", emptyMap(), "mail"),
        )
      )

    assertEquals(mail, primary?.key)
  }

  @Test
  fun `onEntryVisible sets multiple view names when multipane`() {
    val sut = fixture.getSut()
    val list = HomeScreen()
    val detail = ProfileScreen("1")
    sut.onBackstackChanged(listOf(list, detail))

    sut.onEntryVisible(list.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_LIST))
    sut.onEntryVisible(detail.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_DETAIL))

    val app = fixture.scope.contexts.app
    assertEquals(listOf("/HomeScreen", "/ProfileScreen"), app?.viewNames)
    verify(fixture.scope, atLeastOnce()).screen = eq("/ProfileScreen")
  }

  @Test
  fun `onEntryVisible attaches visible entries to navigation context`() {
    val sut = fixture.getSut()
    val list = HomeScreen()
    val detail = ProfileScreen("1")
    sut.onBackstackChanged(listOf(list, detail))

    sut.onEntryVisible(list.toString(), emptyMap())
    sut.onEntryVisible(detail.toString(), emptyMap())

    @Suppress("UNCHECKED_CAST") val contextCaptor = argumentCaptor<Map<String, Any>>()
    verify(fixture.scope, times(3)).setContexts(eq("navigation"), contextCaptor.capture())
    val navigation = contextCaptor.lastValue
    @Suppress("UNCHECKED_CAST")
    val visible = navigation["visible_entries"] as List<Map<String, Any?>>
    assertEquals(2, visible.size)
    assertEquals("/HomeScreen", visible[0]["route"])
    assertEquals("/ProfileScreen", visible[1]["route"])
  }

  @Test
  fun `onEntryVisible attaches stack names to visible entries when known`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val mail = ProfileScreen("1")
    sut.onBackstacksChanged(
      selectedStack = "mail",
      backStacks = linkedMapOf("home" to listOf(home), "mail" to listOf(mail)),
      stacksInUse = linkedSetOf("home", "mail"),
      stackNameExtractor = null,
    )

    sut.onEntryVisible(home.toString(), emptyMap())
    sut.onEntryVisible(mail.toString(), emptyMap())

    @Suppress("UNCHECKED_CAST") val contextCaptor = argumentCaptor<Map<String, Any>>()
    verify(fixture.scope, times(3)).setContexts(eq("navigation"), contextCaptor.capture())
    val navigation = contextCaptor.lastValue
    @Suppress("UNCHECKED_CAST")
    val visible = navigation["visible_entries"] as List<Map<String, Any?>>
    assertEquals("home", visible[0]["stack"])
    assertEquals("/HomeScreen", visible[0]["route"])
    assertEquals("mail", visible[1]["stack"])
    assertEquals("/ProfileScreen", visible[1]["route"])
  }

  @Test
  fun `primaryRouteSelector overrides default visible route selection`() {
    val sut =
      fixture.getSut(
        primaryRouteSelector = { visibleEntries ->
          visibleEntries.firstOrNull { it.stack == "home" }
        }
      )
    val home = HomeScreen()
    val mail = ProfileScreen("1")
    sut.onBackstacksChanged(
      selectedStack = "mail",
      backStacks = linkedMapOf("home" to listOf(home), "mail" to listOf(mail)),
      stacksInUse = linkedSetOf("home", "mail"),
      stackNameExtractor = null,
    )

    sut.onEntryVisible(home.toString(), emptyMap())
    sut.onEntryVisible(mail.toString(), emptyMap())

    val screenCaptor = argumentCaptor<String>()
    verify(fixture.scope, atLeastOnce()).setScreen(screenCaptor.capture())
    assertEquals("/HomeScreen", screenCaptor.lastValue)
  }

  @Test
  fun `onEntryVisible captures breadcrumb with visible routes when multipane`() {
    val sut = fixture.getSut()
    val list = HomeScreen()
    val detail = ProfileScreen("1")
    sut.onBackstackChanged(listOf(list, detail))

    sut.onEntryVisible(list.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_LIST))
    sut.onEntryVisible(detail.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_DETAIL))

    val captor = argumentCaptor<Breadcrumb>()
    verify(fixture.scopes, atLeastOnce()).addBreadcrumb(captor.capture(), any())
    assertEquals(listOf("/HomeScreen", "/ProfileScreen"), captor.lastValue.data["visible"])
  }

  @Test
  fun `onEntryVisible does not duplicate breadcrumb when only primary changes once`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    sut.onBackstackChanged(listOf(home))
    sut.onEntryVisible(home.toString(), emptyMap())

    verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  @Test
  fun `onEntryHidden falls back to backstack top when visible panes empty`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val profile = ProfileScreen("1")
    sut.onBackstackChanged(listOf(home, profile))
    sut.onEntryVisible(home.toString(), emptyMap())
    sut.onEntryHidden(home.toString())

    verify(fixture.scope, atLeastOnce()).setScreen(eq("/ProfileScreen"))
  }

  @Test
  fun `onEntryPopped selects new primary among remaining visible panes`() {
    val sut = fixture.getSut()
    val list = HomeScreen()
    val detail = ProfileScreen("1")
    sut.onBackstackChanged(listOf(list, detail))
    sut.onEntryVisible(list.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_LIST))
    sut.onEntryVisible(detail.toString(), mapOf(NAV3_METADATA_LIST_DETAIL_PANE to NAV3_PANE_DETAIL))

    // Popping the detail pane should promote the remaining list pane to primary.
    sut.onEntryPopped(detail.toString())

    verify(fixture.scope, atLeastOnce()).setScreen(eq("/HomeScreen"))
  }

  @Test
  fun `onEntryPopped of last visible pane does not fall back to backstack top`() {
    val sut = fixture.getSut()
    val home = HomeScreen()
    val profile = ProfileScreen("1")
    // home is the backstack top and the only visible pane; profile sits below and is never visible.
    sut.onBackstackChanged(listOf(profile, home))
    sut.onEntryVisible(home.toString(), emptyMap())

    // Unlike onEntryHidden, popping the last visible pane must not re-promote the backstack top.
    sut.onEntryPopped(home.toString())

    verify(fixture.scope, never()).setScreen(eq("/ProfileScreen"))
  }

  @Test
  fun `resolveKey matches contentKey to backstack key`() {
    val sut = fixture.getSut()
    val profile = ProfileScreen("1")
    sut.onBackstackChanged(listOf(HomeScreen(), profile))

    assertEquals(profile, sut.resolveKey(profile.toString()))
  }

  @Test
  fun `cleanup clears visible panes`() {
    val sut = fixture.getSut()
    sut.onBackstackChanged(listOf(HomeScreen()))
    sut.onEntryVisible(HomeScreen().toString(), emptyMap())
    sut.cleanup()

    sut.onBackstackChanged(listOf(ProfileScreen("1")))
    verify(fixture.scopes, times(2)).addBreadcrumb(any<Breadcrumb>(), any())
  }

  // endregion
}
