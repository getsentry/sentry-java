package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import io.sentry.samples.android.R
import io.sentry.samples.android.navigation.common.NavigationSampleConfig

/**
 * Hosts Fragments that each own a Nav3 root (back stack, `SentryNavEffect`, and `NavDisplay`), to
 * exercise how separate effects hand off in three independently selectable scenarios:
 *
 * - `replace` + back stack (Products → Checkout), optionally with a slow enter animation;
 * - a full-screen `DialogFragment` overlay whose host Fragment stays resumed;
 * - a `ViewPager2` of Fragments, where neighboring pages are preloaded at STARTED.
 */
// FragmentActivity rather than AppCompatActivity: AppCompatActivity.setContentView doesn't install
// the NavigationEventDispatcherOwner that NavDisplay needs on the view tree.
class Nav3FragmentsActivity : FragmentActivity() {

  internal val mode: Nav3FragmentMode
    get() =
      Nav3FragmentMode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_MODE) }
        ?: Nav3FragmentMode.REPLACE

  internal companion object {
    const val EXTRA_MODE = "io.sentry.samples.android.navigation.nav3.FRAGMENT_MODE"
  }

  /** When true, Fragment `replace` uses a slow enter animation. */
  internal var slowTransitions by mutableStateOf(false)

  internal val configuration: NavigationSampleConfig
    get() = (intent.extras ?: Bundle()).nav3SampleConfiguration()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NavigationTransactionHistory.install()
    if (!configuration.enableActivityUiLoadTransaction) {
      cancelCurrentActivityUiLoadTransaction()
    }

    val controls =
      ComposeView(this).apply {
        setContent {
          Nav3SampleTheme {
            MultiFragmentControls(
              mode = mode,
              slowTransitions = slowTransitions,
              onSlowTransitionsChanged = { slowTransitions = it },
            )
          }
        }
      }
    val container = FragmentContainerView(this).apply { id = R.id.nav3_multi_fragment_container }
    // The controls above already pad for the status bar; keep Fragments from padding for it again.
    ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
      WindowInsetsCompat.Builder(insets)
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
        .build()
    }
    setContentView(
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(controls, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
      }
    )

    if (savedInstanceState == null) {
      if (mode == Nav3FragmentMode.REPLACE) {
        supportFragmentManager.commit {
          setReorderingAllowed(true)
          add(R.id.nav3_multi_fragment_container, Nav3RootFragment.products())
        }
      } else {
        // Keep Products beneath the overlay or pager for dismissal and Back handoffs.
        supportFragmentManager.commitNow {
          setReorderingAllowed(true)
          add(R.id.nav3_multi_fragment_container, Nav3RootFragment.products())
        }
        if (mode == Nav3FragmentMode.DIALOG) {
          openCheckoutSheet()
        } else {
          openPager()
        }
      }
    }
  }

  internal fun openCheckoutFragment(productId: String) {
    supportFragmentManager.commit {
      setReorderingAllowed(true)
      if (slowTransitions) {
        setCustomAnimations(
          R.anim.nav3_slow_fade_in,
          R.anim.nav3_slow_hold,
          R.anim.nav3_slow_fade_in,
          R.anim.nav3_slow_hold,
        )
      } else {
        setCustomAnimations(
          android.R.anim.fade_in,
          android.R.anim.fade_out,
          android.R.anim.fade_in,
          android.R.anim.fade_out,
        )
      }
      replace(R.id.nav3_multi_fragment_container, Nav3RootFragment.checkout(productId))
      addToBackStack("checkout")
    }
  }

  private fun openPager() {
    supportFragmentManager.commit {
      setReorderingAllowed(true)
      replace(R.id.nav3_multi_fragment_container, Nav3PagerFragment())
      addToBackStack("pager")
    }
  }

  private fun openCheckoutSheet() {
    Nav3CheckoutSheetFragment().show(supportFragmentManager, "checkout_sheet")
  }

  /** Pops this Activity's Fragment back stack, or finishes the Activity when it's empty. */
  internal fun finishRoot() {
    if (supportFragmentManager.backStackEntryCount > 0) {
      supportFragmentManager.popBackStack()
    } else {
      finish()
    }
  }
}

internal enum class Nav3FragmentMode(val scenario: Nav3Scenario) {
  REPLACE(Nav3Scenario.MULTI_FRAGMENT),
  DIALOG(Nav3Scenario.FRAGMENT_OVERLAY),
  PAGER(Nav3Scenario.VIEW_PAGER2),
}

/** A Fragment that owns one Nav3 root. Pager pages are child Fragments of [Nav3PagerFragment]. */
class Nav3RootFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val args = requireArguments()
    val host = requireNotNull(args.getString(ARG_HOST))
    val activity = requireActivity() as Nav3FragmentsActivity
    val initialRoute =
      when (args.getString(ARG_KIND)) {
        KIND_CHECKOUT -> Nav3Route.Checkout(requireNotNull(args.getString(ARG_PRODUCT_ID)))
        KIND_PAGE -> pageRoots[args.getInt(ARG_PAGE)]
        else -> Nav3Route.ProductList
      }
    val opensCheckoutFragment =
      args.getString(ARG_KIND) == KIND_PRODUCTS && activity.mode == Nav3FragmentMode.REPLACE

    return nav3RootComposeView(
      activity = activity,
      host = host,
      initialRoute = initialRoute,
      configuration = activity.configuration,
      onFinish = activity::finishRoot,
      onCheckout = if (opensCheckoutFragment) activity::openCheckoutFragment else null,
    )
  }

  internal companion object {
    private const val ARG_KIND = "kind"
    private const val ARG_HOST = "host"
    private const val ARG_PRODUCT_ID = "product_id"
    private const val ARG_PAGE = "page"
    private const val KIND_PRODUCTS = "products"
    private const val KIND_CHECKOUT = "checkout"
    private const val KIND_PAGE = "page"

    internal val pageRoots: List<Nav3Route> =
      listOf(Nav3Route.ProductList, Nav3Route.Checkout("7"), Nav3Route.Confirmation("page-2"))

    fun products() =
      Nav3RootFragment().apply {
        arguments = bundleOf(ARG_KIND to KIND_PRODUCTS, ARG_HOST to "ProductsFragment")
      }

    fun checkout(productId: String) =
      Nav3RootFragment().apply {
        arguments =
          bundleOf(
            ARG_KIND to KIND_CHECKOUT,
            ARG_HOST to "CheckoutFragment",
            ARG_PRODUCT_ID to productId,
          )
      }

    fun page(index: Int) =
      Nav3RootFragment().apply {
        arguments = bundleOf(ARG_KIND to KIND_PAGE, ARG_HOST to "Page$index", ARG_PAGE to index)
      }
  }
}

/** A full-screen dialog with its own Nav3 root. The Fragment underneath stays resumed. */
class Nav3CheckoutSheetFragment : DialogFragment() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_TITLE, R.style.AppTheme_Main)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val activity = requireActivity() as Nav3FragmentsActivity
    return nav3RootComposeView(
      activity = activity,
      host = "CheckoutSheet",
      title = "Checkout dialog",
      initialRoute = Nav3Route.Checkout("42"),
      configuration = activity.configuration,
      onFinish = ::dismiss,
      onCheckout = null,
    )
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setLayout(MATCH_PARENT, MATCH_PARENT)
  }
}

/**
 * A `ViewPager2` whose pages each own a Nav3 root. With `offscreenPageLimit = 1`, the neighbors of
 * the current page are created and capped at STARTED, while only the current page is RESUMED.
 */
class Nav3PagerFragment : Fragment() {

  private var currentPage by mutableIntStateOf(0)
  private var pager: ViewPager2? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val viewPager =
      ViewPager2(requireContext()).apply {
        id = R.id.nav3_multi_fragment_pager
        offscreenPageLimit = 1
      }
    pager = viewPager
    val header =
      ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          Nav3SampleTheme {
            PagerHeader(currentPage = currentPage, onSelectPage = { viewPager.currentItem = it })
          }
        }
      }
    return LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
      addView(viewPager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val viewPager = requireNotNull(pager)
    viewPager.adapter =
      object : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {
        override fun getItemCount(): Int = Nav3RootFragment.pageRoots.size

        override fun createFragment(position: Int): Fragment = Nav3RootFragment.page(position)
      }
    viewPager.registerOnPageChangeCallback(
      object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
          currentPage = position
        }
      }
    )
  }

  override fun onDestroyView() {
    pager = null
    super.onDestroyView()
  }
}

@Composable
internal fun FragmentScenarioRoute(
  route: Nav3Route,
  mode: Nav3FragmentMode,
  onOpenFragments: (Nav3FragmentMode) -> Unit,
) {
  RouteScaffold(
    routeSpec = route.routeSpec(),
    testTagPrefix = nav3TestTag("route_${mode.name.lowercase()}_fragments"),
    cardContent = {
      RouteButton(
        when (mode) {
          Nav3FragmentMode.REPLACE -> "Open Products Fragment"
          Nav3FragmentMode.DIALOG -> "Open Checkout dialog"
          Nav3FragmentMode.PAGER -> "Open ViewPager2"
        },
        onClick = { onOpenFragments(mode) },
        testTag =
          nav3TestTag(
            if (mode == Nav3FragmentMode.REPLACE) "multi_fragment_open_fragments"
            else "${mode.name.lowercase()}_open_fragments"
          ),
      )
    },
  )
}

private fun Fragment.nav3RootComposeView(
  activity: Nav3FragmentsActivity,
  host: String,
  title: String = host,
  initialRoute: Nav3Route,
  configuration: NavigationSampleConfig,
  onFinish: () -> Unit,
  onCheckout: ((String) -> Unit)?,
): ComposeView =
  ComposeView(requireContext()).apply {
    // Dispose with the Fragment's view, so the root's SentryNavEffect leaves the composition when
    // the Fragment's view is destroyed (e.g., when it's replaced and put on the back stack).
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
      Nav3SampleTheme {
        CompositionLocalProvider(LocalNav3SampleHost provides host) {
          MultiRootNavDisplay(
            activity = activity,
            title = title,
            initialRoute = initialRoute,
            scenario = activity.mode.scenario,
            configuration = configuration,
            onFinish = onFinish,
            onCheckout = onCheckout,
          )
        }
      }
    }
  }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MultiFragmentControls(
  mode: Nav3FragmentMode,
  slowTransitions: Boolean,
  onSlowTransitionsChanged: (Boolean) -> Unit,
) {
  Surface(
    shadowElevation = 4.dp,
    modifier = Modifier.statusBarsPadding().semantics { testTagsAsResourceId = true },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
      Text(mode.scenario.label, style = MaterialTheme.typography.titleMedium)
      when (mode) {
        Nav3FragmentMode.REPLACE -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Slow Fragment transitions", modifier = Modifier.weight(1f))
            Switch(
              checked = slowTransitions,
              onCheckedChange = onSlowTransitionsChanged,
              modifier = Modifier.testTag(nav3TestTag("multi_fragment_slow_transitions")),
            )
          }
        }
        Nav3FragmentMode.DIALOG,
        Nav3FragmentMode.PAGER -> Unit
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PagerHeader(currentPage: Int, onSelectPage: (Int) -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics {
        testTagsAsResourceId = true
      }
  ) {
    Text(
      "ViewPager2 page $currentPage (neighbors preloaded at STARTED)",
      modifier = Modifier.testTag(nav3TestTag("multi_fragment_current_page")),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Nav3RootFragment.pageRoots.indices.forEach { index ->
        OutlinedButton(
          onClick = { onSelectPage(index) },
          modifier = Modifier.testTag(nav3TestTag("multi_fragment_page_$index")),
        ) {
          Text("Page $index")
        }
      }
    }
  }
}

private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
