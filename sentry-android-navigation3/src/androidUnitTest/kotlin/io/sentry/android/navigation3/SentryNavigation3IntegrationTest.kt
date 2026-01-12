package io.sentry.android.navigation3

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.TransactionNameSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryNavigation3IntegrationTest {
  private lateinit var scopes: IScopes
  private lateinit var options: SentryOptions
  private lateinit var backStack: SnapshotStateList<String>
  private lateinit var transactionHolder: NavigationTransactionHolder

  @BeforeTest
  fun setup() {
    options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        isTracingEnabled = true
        isEnableScreenTracking = true
      }

    scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(options)
    whenever(scopes.configureScope(any())).then {
      val callback = it.arguments[0] as (io.sentry.IScope) -> Unit
      callback(mock())
    }

    backStack = mutableStateListOf()
    transactionHolder = NavigationTransactionHolder()
  }

  @Test
  fun `adds breadcrumb on navigation`() {
    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    // Simulate navigation
    backStack.add("home")

    observer.handleNavigation("home", backStack.toList())

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(scopes).addBreadcrumb(breadcrumbCaptor.capture(), anyOrNull())

    val breadcrumb = breadcrumbCaptor.firstValue
    assertEquals("navigation", breadcrumb.type)
    assertEquals("navigation", breadcrumb.category)
    assertEquals("home", breadcrumb.data["to"])
    assertEquals(io.sentry.SentryLevel.INFO, breadcrumb.level)
  }

  @Test
  fun `adds breadcrumb with from and to routes`() {
    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    // First navigation
    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    // Second navigation
    backStack.add("profile")
    observer.handleNavigation("profile", backStack.toList())

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(scopes, times(2)).addBreadcrumb(breadcrumbCaptor.capture(), anyOrNull())

    val secondBreadcrumb = breadcrumbCaptor.secondValue
    assertEquals("home", secondBreadcrumb.data["from"])
    assertEquals("profile", secondBreadcrumb.data["to"])
  }

  @Test
  fun `captures back stack keys in breadcrumb`() {
    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    backStack.add("profile")
    backStack.add("settings")

    observer.handleNavigation("settings", backStack.toList())

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(scopes).addBreadcrumb(breadcrumbCaptor.capture(), anyOrNull())

    val breadcrumb = breadcrumbCaptor.firstValue
    val capturedBackStack = breadcrumb.data["back_stack"] as? List<*>
    assertNotNull(capturedBackStack)
    assertEquals(3, capturedBackStack.size)
    assertEquals(listOf("home", "profile", "settings"), capturedBackStack)
  }

  @Test
  fun `does not add breadcrumb when disabled`() {
    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    verify(scopes, never()).addBreadcrumb(any(), anyOrNull())
  }

  @Test
  fun `starts transaction on navigation when tracing enabled`() {
    val mockTransaction = mock<ITransaction>()
    val spanContext = mock<io.sentry.SpanContext>()
    whenever(mockTransaction.spanContext).thenReturn(spanContext)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(mockTransaction)

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    val contextCaptor = argumentCaptor<TransactionContext>()
    verify(scopes).startTransaction(contextCaptor.capture(), any<TransactionOptions>())

    val context = contextCaptor.firstValue
    assertEquals("home", context.name)
    assertEquals(TransactionNameSource.ROUTE, context.transactionNameSource)
    assertEquals("navigation", context.operation)
  }

  @Test
  fun `sets trace origin on transaction`() {
    val mockTransaction = mock<ITransaction>()
    val spanContext = mock<io.sentry.SpanContext>()
    whenever(mockTransaction.spanContext).thenReturn(spanContext)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(mockTransaction)

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    verify(spanContext).origin = "auto.navigation.navigation3"
  }

  @Test
  fun `captures back stack keys in transaction data`() {
    val mockTransaction = mock<ITransaction>()
    val spanContext = mock<io.sentry.SpanContext>()
    whenever(mockTransaction.spanContext).thenReturn(spanContext)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(mockTransaction)

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    backStack.add("profile")
    observer.handleNavigation("profile", backStack.toList())

    verify(mockTransaction)
      .setData(
        check { assertEquals("back_stack", it) },
        check {
          val list = it as? List<*>
          assertNotNull(list)
          assertEquals(listOf("home", "profile"), list)
        },
      )
  }

  @Test
  fun `does not start transaction when tracing disabled`() {
    options.isTracingEnabled = false

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    verify(scopes, never()).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `finishes previous transaction before starting new one`() {
    val mockTransaction1 = mock<ITransaction>()
    val mockTransaction2 = mock<ITransaction>()
    val spanContext1 = mock<io.sentry.SpanContext>()
    val spanContext2 = mock<io.sentry.SpanContext>()
    whenever(mockTransaction1.spanContext).thenReturn(spanContext1)
    whenever(mockTransaction2.spanContext).thenReturn(spanContext2)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(mockTransaction1)
      .thenReturn(mockTransaction2)

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    backStack.add("profile")
    observer.handleNavigation("profile", backStack.toList())

    // Verify first transaction was finished
    verify(mockTransaction1).finish(any())
  }

  @Test
  fun `sets screen name when screen tracking enabled`() {
    val scopeMock = mock<io.sentry.IScope>()
    whenever(scopes.configureScope(any())).then {
      val callback = it.arguments[0] as (io.sentry.IScope) -> Unit
      callback(scopeMock)
    }

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    verify(scopeMock).screen = "home"
  }

  @Test
  fun `does not set screen name when screen tracking disabled`() {
    options.isEnableScreenTracking = false
    val scopeMock = mock<io.sentry.IScope>()

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = false,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    verify(scopeMock, never()).screen = any()
  }

  @Test
  fun `uses custom keyToRoute function`() {
    data class NavKey(val route: String, val id: Int)

    val customBackStack = mutableStateListOf<NavKey>()
    val observer =
      SentryBackStackObserver(
        backStack = customBackStack,
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = false,
        keyToRoute = { it.route },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    val key = NavKey("home", 1)
    customBackStack.add(key)
    observer.handleNavigation(key, customBackStack.toList())

    val breadcrumbCaptor = argumentCaptor<Breadcrumb>()
    verify(scopes).addBreadcrumb(breadcrumbCaptor.capture(), anyOrNull())

    assertEquals("home", breadcrumbCaptor.firstValue.data["to"])
  }

  @Test
  fun `handles null route from keyToRoute`() {
    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = true,
        keyToRoute = { null },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    // Should not capture breadcrumb or start transaction when route is null
    verify(scopes, never()).addBreadcrumb(any(), anyOrNull())
    verify(scopes, never()).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
  }

  @Test
  fun `sets transaction options correctly`() {
    val mockTransaction = mock<ITransaction>()
    val spanContext = mock<io.sentry.SpanContext>()
    whenever(mockTransaction.spanContext).thenReturn(spanContext)
    whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
      .thenReturn(mockTransaction)

    options.idleTimeout = 5000L
    options.deadlineTimeout = 30000L

    val observer =
      SentryBackStackObserver(
        backStack = backStack,
        enableNavigationBreadcrumbs = false,
        enableNavigationTracing = true,
        keyToRoute = { it },
        scopes = scopes,
        transactionHolder = transactionHolder,
      )

    backStack.add("home")
    observer.handleNavigation("home", backStack.toList())

    val optionsCaptor = argumentCaptor<TransactionOptions>()
    verify(scopes).startTransaction(any<TransactionContext>(), optionsCaptor.capture())

    val txOptions = optionsCaptor.firstValue
    assertTrue(txOptions.isWaitForChildren)
    assertEquals(5000L, txOptions.idleTimeout)
    assertEquals(30000L, txOptions.deadlineTimeout)
    assertTrue(txOptions.isTrimEnd)
  }
}
