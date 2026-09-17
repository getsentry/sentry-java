package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertNull
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BackStackObserverTest {

  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  private data class CartRoute(val productId: String)

  private data class SettingsRoute(val section: String)

  private data class ObserverConfig(
    val enableNavigationBreadcrumbs: Boolean = true,
    val enableNavigationTransactions: Boolean = true,
    val captureBackStack: Boolean = true,
    val maxCapturedBackStackEntries: Int = 10,
    val enableScreenTracking: Boolean = true,
  )

  private class Fixture {
    private val defaultNameExtractor =
      RouteNameExtractor<Any> { entry -> entry::class.simpleName ?: "unknown" }

    val logger = mock<ILogger>()
    val scope = Scope(createOptions(logger))
    val scopes = mock<IScopes>()
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val breadcrumbHints = mutableListOf<Hint>()
    val startedTransactions = mutableListOf<SentryTracer>()

    init {
      whenever(scopes.options).thenReturn(scope.options)
      whenever(scopes.getSpan()).thenAnswer { scope.span }
      whenever(scopes.getTransaction()).thenAnswer { scope.transaction }
      doAnswer {
          (it.arguments[0] as ScopeCallback).run(scope)
          null
        }
        .whenever(scopes)
        .configureScope(any())
      doAnswer {
          val transactionContext = it.arguments[0] as TransactionContext
          val transactionOptions = it.arguments[1] as TransactionOptions
          SentryTracer(transactionContext, scopes, transactionOptions)
            .also(startedTransactions::add)
        }
        .whenever(scopes)
        .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
      doAnswer {
          breadcrumbs += it.arguments[0] as Breadcrumb
          breadcrumbHints += it.arguments[1] as Hint
          null
        }
        .whenever(scopes)
        .addBreadcrumb(any<Breadcrumb>(), any<Hint>())
    }

    fun getSut(
      config: ObserverConfig = ObserverConfig(),
      nameExtractor: RouteNameExtractor<Any> = defaultNameExtractor,
      argumentsExtractor: RouteArgumentsExtractor<Any>? = null,
    ): BackStackObserver<Any> {
      scope.options.isEnableScreenTracking = config.enableScreenTracking

      return BackStackObserver(
        scopes = scopes,
        options =
          SentryNavOptions(
            enableNavigationBreadcrumbs = config.enableNavigationBreadcrumbs,
            enableNavigationTransactions = config.enableNavigationTransactions,
            captureBackStack = config.captureBackStack,
            maxCapturedBackStackEntries = config.maxCapturedBackStackEntries,
          ),
        resolvers = { RouteResolvers(nameExtractor, argumentsExtractor) },
      )
    }

    private companion object {
      fun createOptions(logger: ILogger): SentryOptions =
        SentryOptions().apply {
          dsn = "http://key@localhost/proj"
          setTracesSampleRate(1.0)
          isEnableScreenTracking = true
          isDebug = true
          setLogger(logger)
          idleTimeout = null
          deadlineTimeout = 0
        }
    }
  }

  @Test
  fun `onBackStackChanged emits a breadcrumb for the top back stack entry when breadcrumbs are enabled`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(enableNavigationBreadcrumbs = true),
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is HomeRoute -> mapOf("tab" to entry.id)
              is ProfileRoute -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            }
          },
      )
    val home = HomeRoute()
    val profile = ProfileRoute("123")

    sut.onBackStackChanged(listOf(home))
    sut.onBackStackChanged(listOf(home, profile))

    val breadcrumb = fixture.breadcrumbs.last()
    assertThat(breadcrumb.type).isEqualTo("navigation")
    assertThat(breadcrumb.category).isEqualTo("navigation")
    assertThat(breadcrumb.data)
      .containsExactly(
        "from",
        "/HomeRoute",
        "from_arguments",
        mapOf("tab" to "home"),
        "to",
        "/ProfileRoute",
        "to_arguments",
        mapOf("userId" to "123"),
      )
    assertThat(fixture.breadcrumbHints.last().get(TypeCheckHint.NAV3_DESTINATION))
      .isSameInstanceAs(profile)
  }

  @Test
  fun `onBackStackChanged does not emit a breadcrumb when breadcrumbs are disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationBreadcrumbs = false))

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.breadcrumbs).isEmpty()
  }

  @Test
  fun `onBackStackChanged emits a screen name for the top back stack entry when screen tracking is enabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableScreenTracking = true))

    sut.onBackStackChanged(listOf(HomeRoute(), ProfileRoute("123")))

    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.contexts.app?.viewNames).isEqualTo(listOf("/ProfileRoute"))
  }

  @Test
  fun `onBackStackChanged does not emit a screen name when screen tracking is disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableScreenTracking = false))

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts.app?.viewNames).isNull()
  }

  @Test
  fun `onBackStackChanged emits a copy of the back stack up to max captured entries when enabled`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(captureBackStack = true, maxCapturedBackStackEntries = 2)
      )

    sut.onBackStackChanged(listOf(HomeRoute(), ProfileRoute("123"), SettingsRoute("privacy")))

    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/SettingsRoute"), mapOf("route" to "/ProfileRoute")))
  }

  @Test
  fun `onBackStackChanged emits an updated copy of the back stack even when the top entry is unchanged`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = true))
    val home = HomeRoute()
    val profile = ProfileRoute("123")

    sut.onBackStackChanged(listOf(home, profile))
    sut.onBackStackChanged(listOf(home, SettingsRoute("privacy"), profile))

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to "/ProfileRoute"),
          mapOf("route" to "/SettingsRoute"),
          mapOf("route" to "/HomeRoute"),
        )
      )
  }

  @Test
  fun `onBackStackChanged emits new top-entry data when the top entry is replaced by an equal new instance`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = true))
    val home = HomeRoute()
    val firstProfile = ProfileRoute("123")
    val replacementProfile = ProfileRoute("123")

    sut.onBackStackChanged(listOf(home, firstProfile))
    sut.onBackStackChanged(listOf(home, replacementProfile))

    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data["from"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/ProfileRoute")
    assertThat(fixture.breadcrumbHints.last().get(TypeCheckHint.NAV3_DESTINATION))
      .isSameInstanceAs(replacementProfile)
    assertThat(fixture.startedTransactions).hasSize(2)
    assertThat(fixture.startedTransactions.last().name).isEqualTo("/ProfileRoute")
    assertThat(fixture.startedTransactions.first().isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/ProfileRoute")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("route" to "/ProfileRoute"), mapOf("route" to "/HomeRoute")))
  }

  @Test
  fun `onBackStackChanged does not emit a back stack copy when max captured entries is 0`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(captureBackStack = true, maxCapturedBackStackEntries = 0)
      )
    fixture.scope.setContexts(
      "navigation",
      mapOf("backstack" to listOf(mapOf("route" to "/Stale"))),
    )

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
  }

  @Test
  fun `onBackStackChanged does not emit a back stack copy when back stack capture is disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = false))
    fixture.scope.setContexts(
      "navigation",
      mapOf("backstack" to listOf(mapOf("route" to "/Stale"))),
    )

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
  }

  @Test
  fun `onBackStackChanged creates a nav transaction when enabled and no ambient transaction is active`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(enableNavigationTransactions = true),
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is ProfileRoute -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            }
          },
      )

    sut.onBackStackChanged(listOf(HomeRoute(), ProfileRoute("123")))

    val transaction = fixture.startedTransactions.single()

    assertThat(transaction.name).isEqualTo("/ProfileRoute")
    assertThat(transaction.transactionNameSource).isEqualTo(TransactionNameSource.ROUTE)
    assertThat(transaction.operation).isEqualTo("navigation")
    assertThat(transaction.spanContext.origin).isEqualTo("auto.navigation.nav3")
    assertThat(transaction.getData("arguments")).isEqualTo(mapOf("userId" to "123"))
    assertThat(transaction.contexts.app?.viewNames).isEqualTo(listOf("/ProfileRoute"))
    assertThat(transaction.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to "/ProfileRoute", "args" to mapOf("userId" to "123")),
          mapOf("route" to "/HomeRoute"),
        )
      )
    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
  }

  @Test
  fun `onBackStackChanged creates a nav transaction when only an ambient span is active`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    fixture.scope.setActiveSpan(mock<ISpan>())
    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.startedTransactions.single().name).isEqualTo("/HomeRoute")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
  }

  @Test
  fun `onBackStackChanged does not create a nav transaction when an ambient transaction is active`() {
    val fixture = Fixture()
    val ambientTransaction =
      SentryTracer(
        TransactionContext("ambient", TransactionNameSource.CUSTOM, "ui.load"),
        fixture.scopes,
      )
    ambientTransaction.startChild("db.query")
    fixture.scope.transaction = ambientTransaction
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(ambientTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/HomeRoute")
  }

  @Test
  fun `onBackStackChanged does not create a nav transaction when navigation transactions are disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = false))
    val originalPropagationContext = fixture.scope.propagationContext

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(originalPropagationContext)
  }

  @Test
  fun `onBackStackChanged clears a finished stale scope transaction before starting a fresh nav transaction`() {
    val fixture = Fixture()
    val staleTransaction =
      SentryTracer(
        TransactionContext("stale", TransactionNameSource.CUSTOM, "ui.load"),
        fixture.scopes,
      )
    staleTransaction.finish()
    fixture.scope.transaction = staleTransaction
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    sut.onBackStackChanged(listOf(HomeRoute()))

    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `onBackStackChanged clears tracked scope state when the back stack becomes empty`() {
    val fixture = Fixture()
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeRoute()))
    val transaction = fixture.startedTransactions.single()

    sut.onBackStackChanged(emptyList())

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertNull(fixture.scope.contexts.app?.viewNames)
    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
    assertThat(fixture.breadcrumbs).hasSize(1)
  }

  @Test
  @Suppress("LongMethod")
  fun `onBackStackChanged records unknown route names when destination route name can't be extracted`() {
    val fixture = Fixture()
    val home = HomeRoute()
    val profile = ProfileRoute(userId = "123")
    val cart = CartRoute(productId = "987")
    val settings = SettingsRoute(section = "privacy")
    val sut =
      fixture.getSut(
        nameExtractor =
          RouteNameExtractor { entry ->
            when (entry) {
              is HomeRoute -> "home"
              is ProfileRoute -> "   "
              is CartRoute -> error("throwing in order to simulate a buggy name extractor")
              is SettingsRoute -> "settings"
              else -> error("unknown route: $entry")
            }
          }
      )

    // Navigate to the home screen and verify that a transaction has started and related Sentry data
    // have been generated (i.e., screen name, breadcrumb, and updated back stack context), as the
    // host app's RouteNameExtractor returned a valid route name for the home screen entry.
    sut.onBackStackChanged(listOf(home))
    val transaction = fixture.startedTransactions.single()
    assertThat(transaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isNotNull()
    assertThat(fixture.scope.screen).isEqualTo("/home")
    assertThat(fixture.scope.contexts.app?.viewNames).isEqualTo(listOf("/home"))
    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.scope.navigationBackStack()).isEqualTo(listOf(mapOf("route" to "/home")))

    // Navigate to the profile screen and verify the invalid route name is recorded as /unknown so
    // the transition history remains intact.
    sut.onBackStackChanged(listOf(home, profile))
    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.startedTransactions).hasSize(2)
    val profileTransaction = fixture.startedTransactions.last()
    assertThat(profileTransaction.isFinished).isFalse()
    assertThat(profileTransaction.name).isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    assertThat(fixture.scope.transaction).isSameInstanceAs(profileTransaction)
    assertThat(fixture.scope.screen).isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    assertThat(fixture.scope.contexts.app?.viewNames)
      .isEqualTo(listOf(RouteTranslator.UNKNOWN_ROUTE_NAME))
    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly("from", "/home", "to", RouteTranslator.UNKNOWN_ROUTE_NAME)
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to RouteTranslator.UNKNOWN_ROUTE_NAME),
          mapOf("route" to "/home"),
        )
      )

    // Navigate to the cart screen and verify the later failure is also recorded as /unknown rather
    // than collapsing the route history.
    sut.onBackStackChanged(listOf(home, profile, cart))
    assertThat(profileTransaction.isFinished).isTrue()
    assertThat(fixture.startedTransactions).hasSize(3)
    val cartTransaction = fixture.startedTransactions.last()
    assertThat(cartTransaction.isFinished).isFalse()
    assertThat(cartTransaction.name).isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    assertThat(fixture.scope.transaction).isSameInstanceAs(cartTransaction)
    assertThat(fixture.scope.screen).isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    assertThat(fixture.scope.contexts.app?.viewNames)
      .isEqualTo(listOf(RouteTranslator.UNKNOWN_ROUTE_NAME))
    assertThat(fixture.breadcrumbs).hasSize(3)
    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly(
        "from",
        RouteTranslator.UNKNOWN_ROUTE_NAME,
        "to",
        RouteTranslator.UNKNOWN_ROUTE_NAME,
      )
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to RouteTranslator.UNKNOWN_ROUTE_NAME),
          mapOf("route" to RouteTranslator.UNKNOWN_ROUTE_NAME),
          mapOf("route" to "/home"),
        )
      )

    // Navigate to the settings screen and verify a new /settings transaction is started and Sentry
    // data are generated again, as we received a valid route name.
    sut.onBackStackChanged(listOf(home, profile, cart, settings))

    assertThat(cartTransaction.isFinished).isTrue()
    assertThat(fixture.startedTransactions).hasSize(4)
    val settingsTransaction = fixture.startedTransactions.last()
    assertThat(settingsTransaction.isFinished).isFalse()
    assertThat(settingsTransaction.name).isEqualTo("/settings")
    assertThat(fixture.scope.transaction).isSameInstanceAs(settingsTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/settings")
    assertThat(fixture.scope.contexts.app?.viewNames).isEqualTo(listOf("/settings"))
    assertThat(fixture.breadcrumbs).hasSize(4)
    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly("from", RouteTranslator.UNKNOWN_ROUTE_NAME, "to", "/settings")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("route" to "/settings"),
          mapOf("route" to RouteTranslator.UNKNOWN_ROUTE_NAME),
          mapOf("route" to RouteTranslator.UNKNOWN_ROUTE_NAME),
          mapOf("route" to "/home"),
        )
      )
  }

  @Test
  fun `cleanup clears observer owned tracked state`() {
    val fixture = Fixture()
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeRoute()))
    val transaction = fixture.startedTransactions.single()

    sut.cleanup()

    assertThat(transaction.isFinished).isTrue()
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
