package io.sentry.android.navigation

import android.content.Context
import android.content.res.Resources
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.Scope.IWithTransaction
import io.sentry.ScopeCallback
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SentryNavigationListenerTest {

    class Fixture {
        val hub = mock<IHub>()
        val destination = mock<NavDestination>()
        val navController = mock<NavController>()

        val context = mock<Context>()
        val resources = mock<Resources>()
        val scope = mock<Scope>()

        lateinit var transaction: SentryTracer

        @Suppress("LongParameterList")
        fun getSut(
            toRoute: String? = "route",
            toId: String? = "destination-id-1",
            enableBreadcrumbs: Boolean = true,
            enableTracing: Boolean = true,
            tracesSampleRate: Double? = 1.0,
            hasViewIdInRes: Boolean = true,
            transaction: SentryTracer = SentryTracer(
                TransactionContext(
                    "/$toRoute",
                    SentryNavigationListener.NAVIGATION_OP
                ),
                hub
            )
        ): SentryNavigationListener {
            this.transaction = transaction

            whenever(hub.startTransaction(any(), any(), any(), anyOrNull(), any<Boolean>()))
                .thenReturn(transaction)
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    setTracesSampleRate(
                        tracesSampleRate
                    )
                }
            )
            whenever(hub.configureScope(any())).thenAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }

            whenever(destination.id).thenReturn(1)
            if (hasViewIdInRes) {
                whenever(resources.getResourceEntryName(1)).thenReturn(toId)
            } else {
                whenever(resources.getResourceEntryName(destination.id)).thenThrow(
                    Resources.NotFoundException()
                )
            }
            whenever(context.resources).thenReturn(resources)
            whenever(navController.context).thenReturn(context)
            whenever(destination.route).thenReturn(toRoute)
            return SentryNavigationListener(hub, enableBreadcrumbs, enableTracing)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `onDestinationChanged captures a breadcrumb`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("navigation", it.type)
                assertEquals("navigation", it.category)
                assertEquals("/route", it.data["to"])
                assertEquals(SentryLevel.INFO, it.level)
            },
            any()
        )
    }

    @Test
    fun `onDestinationChanged captures a breadcrumb with arguments`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(
            fixture.navController,
            fixture.destination,
            bundleOf("arg1" to "foo", "arg2" to "bar")
        )

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("/route", it.data["to"])
                assertEquals(mapOf("arg1" to "foo", "arg2" to "bar"), it.data["to_arguments"])
            },
            any()
        )
    }

    @Test
    fun `onDestinationChanged does not send empty args map`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(
            fixture.navController,
            fixture.destination,
            bundleOf()
        )

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("/route", it.data["to"])
                assertNull(it.data["to_arguments"])
            },
            any()
        )
    }

    @Test
    fun `onDestinationChanged captures a breadcrumb with from and to destinations`() {
        val sut = fixture.getSut(toRoute = "route_from")

        sut.onDestinationChanged(
            fixture.navController,
            fixture.destination,
            bundleOf("from_arg1" to "from_foo")
        )

        val toDestination = mock<NavDestination> {
            whenever(mock.route).thenReturn("route_to")
        }
        sut.onDestinationChanged(
            fixture.navController,
            toDestination,
            bundleOf("to_arg1" to "to_foo")
        )
        val captor = argumentCaptor<Breadcrumb>()
        verify(fixture.hub, times(2)).addBreadcrumb(captor.capture(), any())
        captor.secondValue.let {
            assertEquals("/route_from", it.data["from"])
            assertEquals(mapOf("from_arg1" to "from_foo"), it.data["from_arguments"])

            assertEquals("/route_to", it.data["to"])
            assertEquals(mapOf("to_arg1" to "to_foo"), it.data["to_arguments"])
        }
    }

    @Test
    fun `onDestinationChanged does not capture a breadcrumb when breadcrumbs are disabled`() {
        val sut = fixture.getSut(enableBreadcrumbs = false)

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `onDestinationChanged does not start tracing when tracing is disabled`() {
        val sut = fixture.getSut(enableTracing = false)

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any(),
            any(),
            anyOrNull(),
            any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged does not start tracing when tracesSampleRate is not set`() {
        val sut = fixture.getSut(enableTracing = true, tracesSampleRate = null)

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any(),
            any(),
            anyOrNull(),
            any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged does not start tracing when navigating between activities`() {
        val sut = fixture.getSut()
        whenever(fixture.destination.navigatorName).thenReturn("activity")

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any(),
            any(),
            anyOrNull(),
            any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged does not start tracing when route and id are not available`() {
        val sut = fixture.getSut(toRoute = null, hasViewIdInRes = false)

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any(),
            any(),
            anyOrNull(),
            any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged starts tracing with the route name as transaction name`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub).startTransaction(
            check { assertEquals("/route", it) },
            check { assertEquals(SentryNavigationListener.NAVIGATION_OP, it) },
            any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged strips out route parameters from transaction name`() {
        val sut = fixture.getSut(toRoute = "github/{user_id}?per_page={per_page}")

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub).startTransaction(
            check { assertEquals("/github", it) },
            any(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged starts tracing with destination id if route is not available`() {
        val sut = fixture.getSut(toRoute = null, hasViewIdInRes = true)

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        verify(fixture.hub).startTransaction(
            check { assertEquals("/destination-id-1", it) },
            any(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `onDestinationChanged captures arguments as additional data for transaction`() {
        val sut = fixture.getSut(toRoute = "github/{user_id}?per_page={per_page}")

        sut.onDestinationChanged(
            fixture.navController,
            fixture.destination,
            bundleOf("user_id" to 123, "per_page" to 10)
        )

        verify(fixture.hub).startTransaction(
            check { assertEquals("/github", it) },
            any(), any(), anyOrNull(), any<Boolean>()
        )

        val capturedArgs = fixture.transaction.data!!["arguments"]
        require(capturedArgs is Map<*, *>)
        assertEquals(123, capturedArgs["user_id"])
        assertEquals(10, capturedArgs["per_page"])
    }

    @Test
    fun `onDestinationChanged binds transaction to the Scope`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        val captor = argumentCaptor<IWithTransaction>()
        verify(fixture.scope).withTransaction(captor.capture())
        captor.firstValue.accept(null)
        verify(fixture.scope).transaction = fixture.transaction
    }

    @Test
    fun `onDestinationChanged does not replace existing transaction on the Scope`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        val captor = argumentCaptor<IWithTransaction>()
        verify(fixture.scope).withTransaction(captor.capture())
        captor.firstValue.accept(mock())
        verify(fixture.scope, never()).transaction = fixture.transaction
    }

    @Test
    fun `onDestinationChanged finishes previous navigation transaction before starting a new one`() {
        val sut = fixture.getSut()

        sut.onDestinationChanged(fixture.navController, fixture.destination, null)
        sut.onDestinationChanged(fixture.navController, fixture.destination, null)

        assertEquals(true, fixture.transaction.isFinished)
        val captor = argumentCaptor<IWithTransaction>()
        verify(fixture.scope, times(4)).withTransaction(captor.capture())
        // 1st time - bind to scope, 2nd time - in SentryTracer when finish, 3rd time - in the nav listener
        captor.thirdValue.accept(fixture.transaction)
        verify(fixture.scope).clearTransaction()
    }
}
