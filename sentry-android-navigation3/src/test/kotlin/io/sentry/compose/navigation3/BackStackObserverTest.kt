package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BackStackObserverTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String)

  private data class CartScreen(val productId: String)

  private data class SettingsScreen(val section: String)

  private data class ObserverConfig(
    val enableNavigationBreadcrumbs: Boolean = true,
    val enableNavigationTransactions: Boolean = true,
    val captureBackStack: Boolean = true,
    val maxCapturedBackStackEntries: Int = 10,
    val enableScreenTracking: Boolean = true,
  )

  private class Fixture {
    private val defaultEntryMapper =
      BackStackEntryMapper<Any> { entry ->
        SentryBackStackEntry(entry::class.simpleName ?: "<unknown>")
      }

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
      entryMapper: BackStackEntryMapper<Any> = defaultEntryMapper,
    ): BackStackObserver<Any> {
      scope.options.isEnableScreenTracking = config.enableScreenTracking

      return BackStackObserver(
        scopes = scopes,
        options =
          SentryNavOptions {
            enableNavigationBreadcrumbs = config.enableNavigationBreadcrumbs
            enableNavigationTransactions = config.enableNavigationTransactions
            captureBackStack = config.captureBackStack
            maxCapturedBackStackEntries = config.maxCapturedBackStackEntries
          },
        entryMapper = ForwardingBackStackEntryMapper { entryMapper },
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
        entryMapper = { entry ->
          SentryBackStackEntry(
            entry::class.simpleName ?: "unknown",
            when (entry) {
              is HomeScreen -> mapOf("tab" to entry.id)
              is ProfileScreen -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            },
          )
        },
      )
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    sut.onBackStackChanged(listOf(home))
    sut.onBackStackChanged(listOf(home, profile))

    val breadcrumb = fixture.breadcrumbs.last()
    assertThat(breadcrumb.type).isEqualTo("navigation")
    assertThat(breadcrumb.category).isEqualTo("navigation")
    assertThat(breadcrumb.data)
      .containsExactly(
        "from",
        "/HomeScreen",
        "from_arguments",
        mapOf("tab" to "home"),
        "to",
        "/ProfileScreen",
        "to_arguments",
        mapOf("userId" to "123"),
      )
  }

  @Test
  fun `onBackStackChanged reuses the previous top snapshot for breadcrumb from payload`() {
    val fixture = Fixture()
    val previousProfile = ProfileScreen("123")
    val replacementProfile = ProfileScreen("123")
    var profileName = "profile"
    var profileArguments = mapOf("userId" to "123")
    val sut =
      fixture.getSut(
        entryMapper = { entry ->
          SentryBackStackEntry(
            name =
              when (entry) {
                is HomeScreen -> "home"
                is ProfileScreen -> profileName
                is SettingsScreen -> "settings"
                else -> error("unknown entry: $entry")
              },
            arguments =
              when (entry) {
                is HomeScreen -> mapOf("tab" to entry.id)
                is ProfileScreen -> profileArguments
                is SettingsScreen -> mapOf("section" to entry.section)
                else -> emptyMap()
              },
          )
        }
      )

    sut.onBackStackChanged(listOf(HomeScreen(), previousProfile))
    profileName = "mutated-profile"
    profileArguments = mapOf("userId" to "999")

    sut.onBackStackChanged(listOf(HomeScreen(), replacementProfile, SettingsScreen("privacy")))

    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly(
        "from",
        "/profile",
        "from_arguments",
        mapOf("userId" to "123"),
        "to",
        "/settings",
        "to_arguments",
        mapOf("section" to "privacy"),
      )
  }

  @Test
  fun `onBackStackChanged does not emit a breadcrumb when breadcrumbs are disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationBreadcrumbs = false))

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.breadcrumbs).isEmpty()
  }

  @Test
  fun `onBackStackChanged emits a screen name for the top back stack entry when screen tracking is enabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableScreenTracking = true))

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.contexts.app?.viewNames).isEqualTo(listOf("/ProfileScreen"))
  }

  @Test
  fun `onBackStackChanged does not emit a screen name when screen tracking is disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableScreenTracking = false))

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts.app?.viewNames).isNull()
    assertThat(fixture.startedTransactions.single().contexts.app?.viewNames).isNull()
  }

  @Test
  fun `onBackStackChanged emits a copy of the back stack up to max captured entries when enabled`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(captureBackStack = true, maxCapturedBackStackEntries = 2)
      )

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123"), SettingsScreen("privacy")))

    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/SettingsScreen"), mapOf("entry" to "/ProfileScreen")))
  }

  @Test
  fun `onBackStackChanged preserves top entry arguments when lower entries exhaust the shared budget`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(captureBackStack = true),
        entryMapper = { entry ->
          SentryBackStackEntry(
            entry::class.simpleName ?: "unknown",
            when (entry) {
              is HomeScreen -> mapOf("values" to List(999) { it })
              is ProfileScreen -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            },
          )
        },
      )

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to "/ProfileScreen", "arguments" to mapOf("userId" to "123")),
          mapOf("entry" to "/HomeScreen"),
        )
      )
    assertThat(fixture.startedTransactions.single().getData("arguments"))
      .isEqualTo(mapOf("userId" to "123"))
  }

  @Test
  fun `onBackStackChanged emits an updated copy of the back stack even when the top entry is unchanged`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = true))
    val home = HomeScreen()
    val profile = ProfileScreen("123")

    sut.onBackStackChanged(listOf(home, profile))
    sut.onBackStackChanged(listOf(home, SettingsScreen("privacy"), profile))

    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to "/ProfileScreen"),
          mapOf("entry" to "/SettingsScreen"),
          mapOf("entry" to "/HomeScreen"),
        )
      )
  }

  @Test
  fun `onBackStackChanged emits new top-entry data when the top entry is replaced by an equal new instance`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = true))
    val home = HomeScreen()
    val firstProfile = ProfileScreen("123")
    val replacementProfile = ProfileScreen("123")

    sut.onBackStackChanged(listOf(home, firstProfile))
    sut.onBackStackChanged(listOf(home, replacementProfile))

    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data["from"]).isEqualTo("/ProfileScreen")
    assertThat(fixture.breadcrumbs.last().data["to"]).isEqualTo("/ProfileScreen")
    assertThat(fixture.startedTransactions).hasSize(2)
    assertThat(fixture.startedTransactions.last().name).isEqualTo("/ProfileScreen")
    assertThat(fixture.startedTransactions.first().isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/ProfileScreen"), mapOf("entry" to "/HomeScreen")))
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
      mapOf("backstack" to listOf(mapOf("entry" to "/Stale"))),
    )

    sut.onBackStackChanged(listOf(HomeScreen()))

    // Doesn't emit a back stack...
    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()

    // ...but continues to emit all other Sentry data.
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeScreen")
    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.startedTransactions.single().name).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
  }

  @Test
  fun `onBackStackChanged does not emit a back stack copy when back stack capture is disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(captureBackStack = false))
    fixture.scope.setContexts(
      "navigation",
      mapOf("backstack" to listOf(mapOf("entry" to "/Stale"))),
    )

    sut.onBackStackChanged(listOf(HomeScreen()))

    // Doesn't emit a back stack...
    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()

    // ...but continues to emit all other Sentry data.
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeScreen")
    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.startedTransactions.single().name).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
  }

  // Like `onBackStackChanged does not emit a back stack copy when back stack capture is disabled`,
  // but here we actually verify that no unnecessary work is done.
  @Test
  fun `onBackStackChanged skips lower back stack resolution when back stack capture is disabled`() {
    val fixture = Fixture()
    val home = HomeScreen()
    val profile = ProfileScreen("123")
    val mapperCalls = mutableMapOf<Any, Int>()
    val sut =
      fixture.getSut(
        config = ObserverConfig(captureBackStack = false),
        entryMapper = { entry ->
          mapperCalls[entry] = (mapperCalls[entry] ?: 0) + 1
          SentryBackStackEntry(
            entry::class.simpleName ?: "unknown",
            when (entry) {
              is HomeScreen -> mapOf("tab" to entry.id)
              is ProfileScreen -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            },
          )
        },
      )

    sut.onBackStackChanged(listOf(home, profile))

    assertThat(mapperCalls[profile]).isEqualTo(1)
    assertThat(mapperCalls).doesNotContainKey(home)
  }

  @Test
  fun `onBackStackChanged maps each captured entry once per update`() {
    val fixture = Fixture()
    val home = HomeScreen()
    val profile = ProfileScreen("123")
    val mapperCalls = mutableMapOf<Any, Int>()
    val sut =
      fixture.getSut(
        entryMapper = { entry ->
          mapperCalls[entry] = (mapperCalls[entry] ?: 0) + 1
          SentryBackStackEntry(
            entry::class.simpleName ?: "unknown",
            when (entry) {
              is HomeScreen -> mapOf("tab" to entry.id)
              is ProfileScreen -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            },
          )
        }
      )

    sut.onBackStackChanged(listOf(home, profile))

    assertThat(mapperCalls[profile]).isEqualTo(1)
    assertThat(mapperCalls[home]).isEqualTo(1)
  }

  @Test
  fun `onBackStackChanged creates a nav transaction when enabled and no ambient transaction is active`() {
    val fixture = Fixture()
    val sut =
      fixture.getSut(
        config = ObserverConfig(enableNavigationTransactions = true),
        entryMapper = { entry ->
          SentryBackStackEntry(
            entry::class.simpleName ?: "unknown",
            when (entry) {
              is ProfileScreen -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            },
          )
        },
      )

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    val transaction = fixture.startedTransactions.single()

    assertThat(transaction.name).isEqualTo("/ProfileScreen")
    assertThat(transaction.transactionNameSource).isEqualTo(TransactionNameSource.ROUTE)
    assertThat(transaction.operation).isEqualTo("navigation")
    assertThat(transaction.spanContext.origin).isEqualTo("auto.navigation.nav3")
    assertThat(transaction.getData("arguments")).isEqualTo(mapOf("userId" to "123"))
    assertThat(transaction.contexts.app?.viewNames).isEqualTo(listOf("/ProfileScreen"))
    assertThat(transaction.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to "/ProfileScreen", "arguments" to mapOf("userId" to "123")),
          mapOf("entry" to "/HomeScreen"),
        )
      )
    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
  }

  // Regression test: Setting origin after starting the transaction breaks the ignoredSpanOrigins
  // check (see SentryOptions.getIgnoredSpanOrigins()).
  @Test
  fun `onBackStackChanged sets nav transaction origin before starting the transaction`() {
    val fixture = Fixture()
    val transactionOptionsCaptor = argumentCaptor<TransactionOptions>()
    whenever(
        fixture.scopes.startTransaction(
          any<TransactionContext>(),
          transactionOptionsCaptor.capture(),
        )
      )
      .thenAnswer {
        val transactionContext = it.arguments[0] as TransactionContext
        val transactionOptions = it.arguments[1] as TransactionOptions
        SentryTracer(transactionContext, fixture.scopes, transactionOptions)
          .also(fixture.startedTransactions::add)
      }
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(transactionOptionsCaptor.firstValue.origin).isEqualTo("auto.navigation.nav3")
  }

  @Test
  fun `onBackStackChanged preserves scope app fields on the nav transaction`() {
    val fixture = Fixture()
    val scopeApp =
      App().apply {
        appName = "Demo App"
        appIdentifier = "io.sentry.demo"
      }
    fixture.scope.contexts.setApp(scopeApp)
    val sut = fixture.getSut(config = ObserverConfig(enableScreenTracking = true))

    sut.onBackStackChanged(listOf(HomeScreen(), ProfileScreen("123")))

    val transactionApp = fixture.startedTransactions.single().contexts.app
    assertThat(transactionApp).isNotNull()
    assertThat(transactionApp).isNotSameInstanceAs(scopeApp)
    assertThat(transactionApp?.appName).isEqualTo("Demo App")
    assertThat(transactionApp?.appIdentifier).isEqualTo("io.sentry.demo")
    assertThat(transactionApp?.viewNames).isEqualTo(listOf("/ProfileScreen"))
  }

  @Test
  fun `onBackStackChanged creates a nav transaction when only an ambient span is active`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    fixture.scope.setActiveSpan(mock<ISpan>())
    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.startedTransactions.single().name).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
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

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(ambientTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
  }

  @Test
  fun `onBackStackChanged does not create a nav transaction when navigation transactions are disabled`() {
    val fixture = Fixture()
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = false))
    val originalPropagationContext = fixture.scope.propagationContext

    sut.onBackStackChanged(listOf(HomeScreen()))

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

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `onBackStackChanged does not bind a no-op nav transaction to the scope`() {
    val fixture = Fixture()
    whenever(fixture.scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(NoOpTransaction.getInstance())
    val sut = fixture.getSut(config = ObserverConfig(enableNavigationTransactions = true))

    sut.onBackStackChanged(listOf(HomeScreen()))

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
  }

  @Test
  fun `onBackStackChanged clears tracked scope state when the back stack becomes empty`() {
    val fixture = Fixture()
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    val transaction = fixture.startedTransactions.single()

    sut.onBackStackChanged(emptyList())

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts.app?.viewNames).isNull()
    assertThat(fixture.scope.contexts.containsKey("navigation")).isFalse()
    assertThat(fixture.breadcrumbs).hasSize(1)
  }

  @Test
  @Suppress("LongMethod")
  fun `onBackStackChanged records unknown entry names when destination name can't be mapped`() {
    val fixture = Fixture()
    val home = HomeScreen()
    val profile = ProfileScreen(userId = "123")
    val cart = CartScreen(productId = "987")
    val settings = SettingsScreen(section = "privacy")
    val sut =
      fixture.getSut(
        entryMapper = { entry ->
          SentryBackStackEntry(
            when (entry) {
              is HomeScreen -> "home"
              is ProfileScreen -> "   "
              is CartScreen -> error("throwing in order to simulate a buggy entry mapper")
              is SettingsScreen -> "settings"
              else -> error("unknown entry: $entry")
            }
          )
        }
      )

    // Navigate to the home screen and verify that a transaction has started and related Sentry data
    // have been generated (i.e., screen name, breadcrumb, and updated back stack context), as the
    // host app's BackStackEntryMapper returned a valid name for the home screen entry.
    sut.onBackStackChanged(listOf(home))
    val transaction = fixture.startedTransactions.single()
    assertThat(transaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isNotNull()
    assertThat(fixture.scope.screen).isEqualTo("/home")
    assertThat(fixture.scope.contexts.app?.viewNames).isEqualTo(listOf("/home"))
    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.scope.navigationBackStack()).isEqualTo(listOf(mapOf("entry" to "/home")))

    // Navigate to the profile screen and verify the invalid route name is recorded as /unknown so
    // the transition history remains intact.
    sut.onBackStackChanged(listOf(home, profile))
    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.startedTransactions).hasSize(2)
    val profileTransaction = fixture.startedTransactions.last()
    assertThat(profileTransaction.isFinished).isFalse()
    assertThat(profileTransaction.name).isEqualTo(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME)
    assertThat(fixture.scope.transaction).isSameInstanceAs(profileTransaction)
    assertThat(fixture.scope.screen).isEqualTo(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME)
    assertThat(fixture.scope.contexts.app?.viewNames)
      .isEqualTo(listOf(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME))
    assertThat(fixture.breadcrumbs).hasSize(2)
    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly("from", "/home", "to", NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME)
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME),
          mapOf("entry" to "/home"),
        )
      )

    // Navigate to the cart screen and verify the later failure is also recorded as /unknown rather
    // than collapsing the route history.
    sut.onBackStackChanged(listOf(home, profile, cart))
    assertThat(profileTransaction.isFinished).isTrue()
    assertThat(fixture.startedTransactions).hasSize(3)
    val cartTransaction = fixture.startedTransactions.last()
    assertThat(cartTransaction.isFinished).isFalse()
    assertThat(cartTransaction.name).isEqualTo(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME)
    assertThat(fixture.scope.transaction).isSameInstanceAs(cartTransaction)
    assertThat(fixture.scope.screen).isEqualTo(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME)
    assertThat(fixture.scope.contexts.app?.viewNames)
      .isEqualTo(listOf(NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME))
    assertThat(fixture.breadcrumbs).hasSize(3)
    assertThat(fixture.breadcrumbs.last().data)
      .containsExactly(
        "from",
        NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME,
        "to",
        NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME,
      )
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME),
          mapOf("entry" to NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME),
          mapOf("entry" to "/home"),
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
      .containsExactly("from", NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME, "to", "/settings")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(
        listOf(
          mapOf("entry" to "/settings"),
          mapOf("entry" to NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME),
          mapOf("entry" to NormalizedSentryBackStackEntry.UNKNOWN_ENTRY_NAME),
          mapOf("entry" to "/home"),
        )
      )
  }

  @Test
  fun `cleanup clears observer owned tracked state`() {
    val fixture = Fixture()
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf(HomeScreen()))
    val transaction = fixture.startedTransactions.single()

    sut.cleanup()

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts.app?.viewNames).isNull()
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
