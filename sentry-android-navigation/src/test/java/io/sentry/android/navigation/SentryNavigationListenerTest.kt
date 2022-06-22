package io.sentry.android.navigation

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel
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

        fun getSut(toRoute: String = "route"): SentryNavigationListener {
            whenever(destination.route).thenReturn(toRoute)
            return SentryNavigationListener(hub)
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
                assertEquals("route", it.data["to"])
                assertEquals(SentryLevel.INFO, it.level)
            }
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
                assertEquals("route", it.data["to"])
                assertEquals(mapOf("arg1" to "foo", "arg2" to "bar"), it.data["to_arguments"])
            }
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
                assertEquals("route", it.data["to"])
                assertNull(it.data["to_arguments"])
            }
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
        reset(fixture.hub)

        val toDestination = mock<NavDestination> {
            whenever(mock.route).thenReturn("route_to")
        }
        sut.onDestinationChanged(
            fixture.navController,
            toDestination,
            bundleOf("to_arg1" to "to_foo")
        )
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("route_from", it.data["from"])
                assertEquals(mapOf("from_arg1" to "from_foo"), it.data["from_arguments"])

                assertEquals("route_to", it.data["to"])
                assertEquals(mapOf("to_arg1" to "to_foo"), it.data["to_arguments"])
            }
        )
    }
}
