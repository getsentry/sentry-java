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
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
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

  // Screens without an effect

  @Test
  fun `an activity without an effect keeps the outgoing activity's nav data and transaction on the scope`() {
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

  internal class HomeActivity : NavEffectActivity("Home")

  internal class CheckoutActivity : NavEffectActivity("Checkout")

  internal class PlainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      lifecycle.addObserver(Fixture.require().eventRecorder("Plain"))
      setContentView(FrameLayout(this))
    }
  }

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

  internal class ViewPagerActivity : FragmentActivity() {

    lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      lifecycle.addObserver(Fixture.require().eventRecorder("ViewPager"))
      viewPager =
        ViewPager2(this).apply {
          // Preload the page next to the current one. FragmentStateAdapter caps a preloaded page
          // at STARTED and resumes only the current page.
          offscreenPageLimit = 1
          adapter = PagesAdapter(this@ViewPagerActivity)
        }
      setContentView(viewPager)
    }

    private class PagesAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

      override fun getItemCount(): Int = 2

      override fun createFragment(position: Int): Fragment =
        if (position == 0) HomeFragment() else CheckoutFragment()
    }
  }

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
