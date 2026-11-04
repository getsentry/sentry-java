package io.sentry.compose.navigation3

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.navigation3.lifecycle.NAVIGATION_CONTEXT_KEY
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Drives real Activities and Fragments through Robolectric so that each `SentryNavEffect` observes
 * the lifecycle events, and the composition timing, that Android produces when screens hand off to
 * one another. Lets us test the multi-[SentryNavEffect] policy implemented by our
 * `io.sentry.compose.navigation3.lifecycle` package.
 *
 * Activity transitions are driven through [ActivityController]s in the order the framework uses
 * (e.g., the outgoing Activity pauses before the incoming one is created, and stops after the
 * incoming one resumes and becomes visible). Fragment transitions are left entirely to the real
 * FragmentManager (and, for ViewPager2 pages, to FragmentStateAdapter). Compositions run when a
 * ComposeView attaches to its window, as on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
@Suppress("LargeClass")
class SentryNavEffectLifecycleIntegrationTest {

  @After
  fun tearDown() {
    Fixture.current = null
  }

  // Activity handoffs
  @Test
  fun `an incoming activity publishes its own nav data and transaction over the outgoing activity's`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:ON_PAUSE", "Checkout:ON_RESUME", "Checkout:composed(RESUMED)")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Checkout")
      .inOrder()
  }

  /**
   * `ActivityLifecycleIntegration` binds an Activity's `ui.load` transaction when the Activity is
   * created, and only if the scope's transaction slot is free. Withdrawing the outgoing Activity's
   * lease before then frees the slot, so `ui.load` takes precedence over the incoming Activity's
   * first nav transaction, as it does for the first Activity at app start.
   */
  @Test
  fun `an incoming activity binds its own ui load transaction instead of its first nav transaction`() {
    val fixture = Fixture.create()
    fixture.bindUiLoadTransactionWhenCreated(CheckoutActivity::class.java)
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()

    val uiLoadTransaction = fixture.uiLoadTransactions.single()
    assertThat(uiLoadTransaction.operation).isEqualTo("ui.load")
    assertThat(fixture.scope.transaction).isSameInstanceAs(uiLoadTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
  }

  /**
   * Below API 29, there's no `onActivityPreCreated`, so the outgoing Activity's lease is withdrawn
   * from `onActivityCreated`, after `ActivityLifecycleIntegration` has found the scope's
   * transaction slot taken. `ui.load` stays unbound, and the incoming Activity binds its first nav
   * transaction instead.
   */
  @Test
  @Config(sdk = [28])
  fun `below API 29, an incoming activity binds its first nav transaction instead of its ui load transaction`() {
    val fixture = Fixture.create()
    fixture.bindUiLoadTransactionWhenCreated(CheckoutActivity::class.java)
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()

    home.pause()
    Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()

    val uiLoadTransaction = fixture.uiLoadTransactions.single()
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(uiLoadTransaction)
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
  }

  @Test
  fun `an incoming activity's nav data and transaction survive the outgoing activity stopping`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    home.pause()
    Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()
    val checkoutTransaction = requireNotNull(fixture.scope.transaction)

    home.stop()
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction).isSameInstanceAs(checkoutTransaction)
    assertThat(checkoutTransaction.isFinished).isFalse()
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
  }

  @Test
  fun `an incoming activity's nav data and transaction survive the outgoing activity being destroyed`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()
    val checkoutTransaction = requireNotNull(fixture.scope.transaction)
    val propagationContext = fixture.scope.propagationContext

    home.stop()
    home.destroy()
    fixture.idle()

    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction).isSameInstanceAs(checkoutTransaction)
    assertThat(checkoutTransaction.isFinished).isFalse()
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(propagationContext)
  }

  @Test
  fun `going back to a stopped activity restores its nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    val checkout = Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()
    val checkoutTransaction = requireNotNull(fixture.scope.transaction)
    home.stop()
    fixture.idle()

    checkout.pause()
    home.restart()
    home.resume()
    fixture.idle()
    checkout.stop()
    checkout.destroy()
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(checkoutTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `closing an activity that only paused the one beneath it restores the one beneath's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    val checkout = Robolectric.buildActivity(CheckoutActivity::class.java).setup()
    fixture.idle()
    val checkoutTransaction = requireNotNull(fixture.scope.transaction)

    checkout.pause()
    home.resume()
    fixture.idle()
    checkout.stop()
    checkout.destroy()
    fixture.idle()

    assertThat(fixture.events).doesNotContain("Home:ON_STOP")
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(checkoutTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
  }

  // Activity recreation
  @Test
  fun `a recreated activity publishes its own nav data and transaction over the destroyed instance's`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val destroyedTransaction = requireNotNull(fixture.scope.transaction)
    fixture.events.clear()

    home.recreate()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:ON_DESTROY", "Home:ON_CREATE", "Home:composed(RESUMED)")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(destroyedTransaction)
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
  }

  @Test
  fun `recreating an activity finishes the destroyed instance's transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val destroyedTransaction = requireNotNull(fixture.scope.transaction)

    home.recreate()
    fixture.idle()

    assertThat(destroyedTransaction.isFinished).isTrue()
  }

  // A single Activity going to the background and back
  @Test
  fun `a backgrounded activity's nav data and transaction stay on the scope while the activity is stopped`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    home.stop()
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
  }

  @Test
  fun `navigating after an activity returns to the foreground publishes the destination's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    home.stop()
    fixture.idle()
    home.restart()
    home.resume()
    fixture.idle()

    fixture.backStack("Home") += "Profile"
    fixture.idle()

    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Profile", "/Home").inOrder()
  }

  @Test
  fun `navigating before a restarted activity resumes publishes the destination's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    home.stop()
    fixture.idle()
    home.restart()
    fixture.idle()

    fixture.backStack("Home") += "Profile"
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:ON_PAUSE", "Home:ON_STOP", "Home:ON_START")
      .inOrder()
    assertThat(fixture.events.last()).isEqualTo("Home:ON_START")
    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Profile", "/Home").inOrder()
  }

  @Test
  fun `destroying an activity that navigated before resuming finishes its transaction and clears its nav data`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    home.pause()
    home.stop()
    fixture.idle()
    home.restart()
    fixture.idle()
    fixture.backStack("Home") += "Profile"
    fixture.idle()
    val profileTransaction = requireNotNull(fixture.scope.transaction)

    home.stop()
    home.destroy()
    fixture.idle()

    assertThat(fixture.events.takeLast(2))
      .containsExactly("Home:ON_STOP", "Home:ON_DESTROY")
      .inOrder()
    assertThat(profileTransaction.name).isEqualTo("/Profile")
    assertThat(profileTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
  }

  // Fragment replacement
  @Test
  fun `replacing a fragment publishes the incoming fragment's nav data after the outgoing view is destroyed`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    host
      .get()
      .supportFragmentManager
      .beginTransaction()
      .replace(FragmentHostActivity.CONTAINER_ID, CheckoutFragment())
      .addToBackStack(null)
      .commit()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:view:ON_STOP", "Checkout:composed(CREATED)", "Home:view:ON_DESTROY")
      .inOrder()
    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
  }

  @Test
  fun `popping a replaced fragment restores the first fragment's nav data`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val fragmentManager = host.get().supportFragmentManager
    fragmentManager
      .beginTransaction()
      .replace(FragmentHostActivity.CONTAINER_ID, CheckoutFragment())
      .addToBackStack(null)
      .commit()
    fixture.idle()
    val checkoutTransaction = requireNotNull(fixture.scope.transaction)
    fixture.events.clear()

    fragmentManager.popBackStack()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast(
        "Checkout:view:ON_STOP",
        "Home:composed(CREATED)",
        "Checkout:view:ON_DESTROY",
      )
      .inOrder()
    assertThat(checkoutTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
  }

  // ViewPager2 pages

  @Test
  @Config(sdk = [30])
  fun `backgrounding a view pager activity above API 29 keeps the current page's nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    activity.pause()
    activity.stop()
    fixture.idle()

    assertThat(fixture.events).containsAtLeast("ViewPager:ON_PAUSE", "Home:view:ON_PAUSE").inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  @Config(sdk = [29])
  fun `backgrounding a view pager activity at API 29 keeps the current page's nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    activity.pause()
    activity.stop()
    fixture.idle()

    assertThat(fixture.events).containsAtLeast("ViewPager:ON_PAUSE", "Home:view:ON_PAUSE").inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  /**
   * Below API 29, `ComponentActivity` reports that it paused from `ReportFragment` rather than from
   * `onActivityPrePaused`, which must still precede its Fragments pausing.
   */
  @Test
  @Config(sdk = [28])
  fun `backgrounding a view pager activity below API 29 keeps the current page's nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    activity.pause()
    activity.stop()
    fixture.idle()

    assertThat(fixture.events).containsAtLeast("ViewPager:ON_PAUSE", "Home:view:ON_PAUSE").inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `a preloaded view pager page does not displace the current page's nav data and transaction`() {
    val fixture = Fixture.create()

    Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast(
        "Home:view:ON_RESUME",
        "Checkout:composed(CREATED)",
        "Checkout:view:ON_START",
      )
      .inOrder()
    assertThat(fixture.events).doesNotContain("Checkout:view:ON_RESUME")
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
  }

  @Test
  fun `switching to a preloaded view pager page publishes the incoming page's nav data`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    fixture.events.clear()

    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()

    assertThat(fixture.events)
      .containsExactly("Home:view:ON_PAUSE", "Checkout:view:ON_RESUME")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Checkout")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Checkout")
  }

  @Test
  fun `switching to a preloaded view pager page starts its nav transaction with a new propagation context`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    val propagationContext = fixture.scope.propagationContext

    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()

    assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(propagationContext)
  }

  @Test
  fun `switching back to a view pager page restores its unfinished nav transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()
    check(!homeTransaction.isFinished)

    activity.get().viewPager.setCurrentItem(0, false)
    fixture.idle()

    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/Home")
  }

  @Test
  fun `switching back to a view pager page whose nav transaction finished starts no nav transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()
    homeTransaction.finish()

    activity.get().viewPager.setCurrentItem(0, false)
    fixture.idle()

    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
  }

  // Dialogs
  @Test
  fun `a dialog fragment shown over a fragment publishes its own nav data while the fragment stays resumed`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    PickerDialogFragment().show(host.get().supportFragmentManager, "picker")
    fixture.idle()

    assertThat(fixture.events).contains("Picker:composed(RESUMED)")
    assertThat(fixture.events).doesNotContain("Home:view:ON_PAUSE")
    assertThat(fixture.scope.screen).isEqualTo("/Picker")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Picker")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Picker")
  }

  @Test
  fun `dismissing a dialog fragment restores the still-resumed fragment's nav data and transaction`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    val dialog = PickerDialogFragment()
    dialog.show(host.get().supportFragmentManager, "picker")
    fixture.idle()
    val pickerTransaction = requireNotNull(fixture.scope.transaction)

    dialog.dismiss()
    fixture.idle()

    assertThat(fixture.events).contains("Picker:view:ON_DESTROY")
    assertThat(fixture.events.filter { it == "Home:view:ON_RESUME" }).hasSize(1)
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(pickerTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `a dialog fragment shown over an activity publishes its own nav data while the activity stays resumed`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    PickerDialogFragment().show(home.get().supportFragmentManager, "picker")
    fixture.idle()

    assertThat(fixture.events).contains("Picker:composed(RESUMED)")
    assertThat(fixture.events).doesNotContain("Home:ON_PAUSE")
    assertThat(fixture.scope.screen).isEqualTo("/Picker")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Picker")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Picker")
  }

  @Test
  fun `dismissing a dialog fragment restores the still-resumed activity's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    val dialog = PickerDialogFragment()
    dialog.show(home.get().supportFragmentManager, "picker")
    fixture.idle()
    val pickerTransaction = requireNotNull(fixture.scope.transaction)

    dialog.dismiss()
    fixture.idle()

    assertThat(fixture.events).contains("Picker:view:ON_DESTROY")
    assertThat(fixture.events.filter { it == "Home:ON_RESUME" }).hasSize(1)
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(pickerTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  // Nav roots without a SentryNavEffect

  @Test
  fun `swiping to a view pager page without an effect clears the outgoing page's nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(PlainPageViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    fixture.events.clear()

    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()

    assertThat(fixture.events)
      .containsExactly("Home:view:ON_PAUSE", "Plain:view:ON_RESUME")
      .inOrder()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `swiping back to a view pager page from one without an effect restores its nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(PlainPageViewPagerActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    activity.get().viewPager.setCurrentItem(1, false)
    fixture.idle()

    activity.get().viewPager.setCurrentItem(0, false)
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `a view pager page preloaded next to a current page without an effect publishes no nav data or transaction`() {
    val fixture = Fixture.create()

    Robolectric.buildActivity(PlainInitialPageViewPagerActivity::class.java).setup()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Plain:view:ON_RESUME", "Home:composed(CREATED)", "Home:view:ON_START")
    assertThat(fixture.events).doesNotContain("Home:view:ON_RESUME")
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `swiping from a view pager page without an effect to a preloaded page publishes its nav data and transaction`() {
    val fixture = Fixture.create()
    val activity = Robolectric.buildActivity(PlainInitialPageViewPagerActivity::class.java).setup()
    fixture.idle()

    activity.get().viewPager.setCurrentItem(0, false)
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
  }

  @Test
  fun `an activity without an effect clears a covered activity's nav data and unbinds its unfinished transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    home.stop()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:ON_PAUSE", "Plain:ON_RESUME", "Home:ON_STOP")
      .inOrder()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `an activity without an effect clears the nav data and transaction of an activity it only pauses`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()

    assertThat(fixture.events).doesNotContain("Home:ON_STOP")
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(homeTransaction.isFinished).isFalse()
  }

  /**
   * `ActivityLifecycleIntegration` binds an Activity's `ui.load` transaction when the Activity is
   * created, and only if the scope's transaction slot is free.
   */
  @Test
  fun `an activity without an effect binds its own ui load transaction over a covered activity's nav transaction`() {
    val fixture = Fixture.create()
    fixture.bindUiLoadTransactionWhenCreated(PlainActivity::class.java)
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()

    val uiLoadTransaction = fixture.uiLoadTransactions.single()
    assertThat(uiLoadTransaction.operation).isEqualTo("ui.load")
    assertThat(fixture.scope.transaction).isSameInstanceAs(uiLoadTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  /**
   * Below API 29, there's no `onActivityPreCreated`, so the lease is withdrawn from
   * `onActivityCreated`, after `ActivityLifecycleIntegration` has found the scope's transaction
   * slot taken. The covered Activity's nav data and transaction are still removed, but `ui.load`
   * isn't bound.
   */
  @Test
  @Config(sdk = [28])
  fun `below API 29, an activity without an effect unbinds nav too late for ui load`() {
    val fixture = Fixture.create()
    fixture.bindUiLoadTransactionWhenCreated(PlainActivity::class.java)
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    home.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()

    val uiLoadTransaction = fixture.uiLoadTransactions.single()
    assertThat(uiLoadTransaction.operation).isEqualTo("ui.load")
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `going back to an activity covered by one without an effect restores its nav data and unfinished transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    val plain = Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    home.stop()
    fixture.idle()

    plain.pause()
    home.restart()
    home.resume()
    fixture.idle()
    plain.stop()
    plain.destroy()
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `navigating while covered by an activity without an effect publishes no nav data or transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    home.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()

    fixture.backStack("Home") += "Profile"
    fixture.idle()

    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Profile")
      .inOrder()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `going back to an activity that navigated while covered publishes the destination's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    val plain = Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    fixture.backStack("Home") += "Profile"
    fixture.idle()

    plain.pause()
    home.resume()
    fixture.idle()

    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Profile", "/Home").inOrder()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction?.isFinished).isFalse()
  }

  @Test
  fun `an activity without an effect removes a covered fragment's nav data and transaction from the scope`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    host.pause()
    Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    host.stop()
    fixture.idle()

    assertThat(fixture.events).containsAtLeast("Home:view:ON_PAUSE", "Plain:ON_RESUME").inOrder()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `going back to a fragment covered by an activity without an effect restores its nav data and transaction`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    host.pause()
    val plain = Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    host.stop()
    fixture.idle()

    plain.pause()
    host.restart()
    host.resume()
    fixture.idle()
    plain.stop()
    plain.destroy()
    fixture.idle()

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(homeTransaction)
    assertThat(homeTransaction.isFinished).isFalse()
  }

  @Test
  fun `navigating after closing an activity without an effect publishes the destination's nav data and transaction`() {
    val fixture = Fixture.create()
    val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)
    home.pause()
    val plain = Robolectric.buildActivity(PlainActivity::class.java).setup()
    fixture.idle()
    home.stop()
    fixture.idle()
    plain.pause()
    home.restart()
    home.resume()
    fixture.idle()
    plain.stop()
    plain.destroy()
    fixture.idle()

    fixture.backStack("Home") += "Profile"
    fixture.idle()

    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction?.isFinished).isFalse()
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Profile", "/Home").inOrder()
  }

  @Test
  fun `replacing a fragment with one without an effect clears the outgoing fragment's nav data and transaction`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val homeTransaction = requireNotNull(fixture.scope.transaction)

    host
      .get()
      .supportFragmentManager
      .beginTransaction()
      .replace(FragmentHostActivity.CONTAINER_ID, PlainFragment())
      .addToBackStack(null)
      .commit()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Home:view:ON_STOP", "Plain:view:ON_CREATE", "Home:view:ON_DESTROY")
      .inOrder()
    assertThat(homeTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationEntries()).isNull()
  }

  @Test
  fun `popping a fragment without an effect republishes the replaced fragment's nav data with a new transaction`() {
    val fixture = Fixture.create()
    val host = Robolectric.buildActivity(FragmentHostActivity::class.java).setup()
    fixture.idle()
    val destroyedTransaction = requireNotNull(fixture.scope.transaction)
    val fragmentManager = host.get().supportFragmentManager
    fragmentManager
      .beginTransaction()
      .replace(FragmentHostActivity.CONTAINER_ID, PlainFragment())
      .addToBackStack(null)
      .commit()
    fixture.idle()
    fixture.events.clear()

    fragmentManager.popBackStack()
    fixture.idle()

    assertThat(fixture.events)
      .containsAtLeast("Plain:view:ON_STOP", "Home:composed(CREATED)", "Plain:view:ON_DESTROY")
      .inOrder()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.navigationEntries()).containsExactly("/Home")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Home")
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(destroyedTransaction)
    assertThat(fixture.scope.transaction?.isFinished).isFalse()
  }

  private fun IScope.navigationEntries(): List<Any?>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null
    val backStack = navigationContext["Back Stack"] as? List<*> ?: return null
    return backStack.map { (it as? Map<*, *>)?.get("entry") }
  }

  internal open class NavEffectActivity(private val route: String) : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      val fixture = Fixture.require()
      lifecycle.addObserver(fixture.eventRecorder(route))
      setContentView(navEffectView(this, route))
    }
  }

  /** A [NavEffectActivity] whose SentryNavEffect observes the "Home" back stack. */
  internal class HomeActivity : NavEffectActivity("Home")

  /** A [NavEffectActivity] whose SentryNavEffect observes the "Checkout" back stack. */
  internal class CheckoutActivity : NavEffectActivity("Checkout")

  /** An Activity whose content has no SentryNavEffect. */
  internal class PlainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      lifecycle.addObserver(Fixture.require().eventRecorder("Plain"))
      setContentView(FrameLayout(this))
    }
  }

  /** An Activity with no SentryNavEffect of its own, hosting a [HomeFragment] that has one. */
  internal class FragmentHostActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      setContentView(FrameLayout(this).apply { id = CONTAINER_ID })
      if (savedInstanceState == null) {
        supportFragmentManager.beginTransaction().add(CONTAINER_ID, HomeFragment()).commit()
      }
    }

    companion object {
      val CONTAINER_ID: Int = View.generateViewId()
    }
  }

  /**
   * An Activity without a SentryNavEffect of its own whose ViewPager2 pages are a [HomeFragment]
   * and, by default, a [CheckoutFragment], each with a SentryNavEffect. The page at [initialPage]
   * is current when the Activity is created.
   */
  internal open class ViewPagerActivity(
    private val createSecondPage: () -> Fragment = ::CheckoutFragment,
    private val initialPage: Int = 0,
  ) : FragmentActivity() {

    lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      lifecycle.addObserver(Fixture.require().eventRecorder("ViewPager"))
      viewPager =
        ViewPager2(this).apply {
          // Preload the page next to the current one. FragmentStateAdapter caps a preloaded page
          // at STARTED and resumes only the current page.
          offscreenPageLimit = 1
          adapter = PagesAdapter(this@ViewPagerActivity, createSecondPage)
          setCurrentItem(initialPage, false)
        }
      setContentView(viewPager)
    }

    private class PagesAdapter(
      activity: FragmentActivity,
      private val createSecondPage: () -> Fragment,
    ) : FragmentStateAdapter(activity) {

      override fun getItemCount(): Int = 2

      override fun createFragment(position: Int): Fragment =
        if (position == 0) HomeFragment() else createSecondPage()
    }
  }

  /** A [ViewPagerActivity] whose second page has no SentryNavEffect. */
  internal class PlainPageViewPagerActivity : ViewPagerActivity(::PlainFragment)

  /**
   * Like [PlainPageViewPagerActivity], but its current page is initially the one without a
   * SentryNavEffect, e.g., as after rotating the device on that page.
   */
  internal class PlainInitialPageViewPagerActivity :
    ViewPagerActivity(::PlainFragment, initialPage = 1)

  internal open class NavEffectFragment(private val route: String) : Fragment() {

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
    ): View = navEffectView(requireContext(), route)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
      viewLifecycleOwner.lifecycle.addObserver(Fixture.require().eventRecorder("$route:view"))
    }
  }

  internal class HomeFragment : NavEffectFragment("Home")

  internal class CheckoutFragment : NavEffectFragment("Checkout")

  /** A Fragment whose content has no SentryNavEffect. */
  internal class PlainFragment : Fragment() {

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
      viewLifecycleOwner.lifecycle.addObserver(Fixture.require().eventRecorder("Plain:view"))
    }
  }

  internal class PickerDialogFragment : DialogFragment() {

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
    ): View = navEffectView(requireContext(), "Picker")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
      viewLifecycleOwner.lifecycle.addObserver(Fixture.require().eventRecorder("Picker:view"))
    }
  }

  private class Fixture private constructor() {

    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/1"
        defaultScopeType = ScopeType.CURRENT
        setTracesSampleRate(1.0)
        isEnableScreenTracking = true
        idleTimeout = null
        deadlineTimeout = 0
      }

    val scope = Scope(options)
    val scopes = Scopes(scope, Scope(options), Scope(options), "navigation lifecycle test")
    val coordinator = NavLeaseCoordinator()
    val navOptions = SentryNavOptions()
    val mapper = BackStackEntryMapper<String> { SentryBackStackEntry(it) }

    /** Lifecycle events and first compositions, in the order they were observed. */
    val events = mutableListOf<String>()

    private val backStacks = mutableMapOf<String, SnapshotStateList<String>>()

    init {
      val client = mock<ISentryClient>()
      whenever(client.isEnabled).thenReturn(true)
      scopes.bindClient(client)
    }

    fun idle() {
      shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * The back stack observed by the effect of each Activity or Fragment showing [route], which
     * starts out holding just `route`.
     */
    fun backStack(route: String): SnapshotStateList<String> =
      backStacks.getOrPut(route) { mutableStateListOf(route) }

    fun eventRecorder(name: String) = LifecycleEventObserver { _, event ->
      events += "$name:$event"
    }

    /** `ui.load` transactions started by [bindUiLoadTransactionWhenCreated], in start order. */
    val uiLoadTransactions = mutableListOf<ITransaction>()

    /**
     * Mimics `ActivityLifecycleIntegration`, which the SDK registers at initialization (i.e.,
     * before any effect's coordinator registers its own callbacks). In particular, when an instance
     * of [activityClass] is created, starts a `ui.load` transaction and binds it to the scope if no
     * transaction is bound.
     */
    fun bindUiLoadTransactionWhenCreated(activityClass: Class<out Activity>) {
      val application: Application = ApplicationProvider.getApplicationContext()

      application.registerActivityLifecycleCallbacks(
        object : Application.ActivityLifecycleCallbacks {
          override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (!activityClass.isInstance(activity)) {
              return
            }
            val transaction =
              scopes.startTransaction(
                TransactionContext(activityClass.simpleName, "ui.load"),
                TransactionOptions(),
              )
            uiLoadTransactions += transaction
            scopes.configureScope { scope ->
              scope.withTransaction { current ->
                if (current == null) {
                  scope.transaction = transaction
                }
              }
            }
          }

          override fun onActivityStarted(activity: Activity) = Unit

          override fun onActivityResumed(activity: Activity) = Unit

          override fun onActivityPaused(activity: Activity) = Unit

          override fun onActivityStopped(activity: Activity) = Unit

          override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

          override fun onActivityDestroyed(activity: Activity) = Unit
        }
      )
    }

    /**
     * The host-app setup under test: one `SentryNavEffect` in an Activity's or Fragment's content.
     */
    @Composable
    fun NavEffect(route: String) {
      val lifecycle = LocalLifecycleOwner.current.lifecycle
      DisposableEffect(Unit) {
        events += "$route:composed(${lifecycle.currentState})"
        onDispose {}
      }
      SentryNavEffect(backStack(route), mapper, navOptions, scopes, coordinator)
    }

    companion object {
      var current: Fixture? = null

      fun require(): Fixture = checkNotNull(current) { "Fixture.create() was not called" }

      fun create(): Fixture {
        val application: Application = ApplicationProvider.getApplicationContext()
        val packageManager = shadowOf(application.packageManager)
        listOf(
            HomeActivity::class.java,
            CheckoutActivity::class.java,
            PlainActivity::class.java,
            FragmentHostActivity::class.java,
            ViewPagerActivity::class.java,
            PlainPageViewPagerActivity::class.java,
            PlainInitialPageViewPagerActivity::class.java,
          )
          .forEach { activityClass: Class<out Activity> ->
            packageManager.addActivityIfNotPresent(
              ComponentName(application.packageName, activityClass.name)
            )
          }
        return Fixture().also { current = it }
      }
    }
  }

  private companion object {

    fun navEffectView(context: Context, route: String): View {
      val fixture = Fixture.require()
      return ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { fixture.NavEffect(route) }
      }
    }
  }
}
