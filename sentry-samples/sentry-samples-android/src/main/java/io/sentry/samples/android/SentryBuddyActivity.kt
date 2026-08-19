@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)

package io.sentry.samples.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.sqlite.db.SupportSQLiteDatabase
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.compose.SentryTraced
import io.sentry.compose.withSentryObservableEffect
import io.sentry.samples.android.sqlite.SampleDatabases
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SentryBuddyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SentryTravelApp() }
  }
}

@Composable
private fun SentryTravelApp() {
  val navController = rememberNavController().withSentryObservableEffect()
  val context = androidx.compose.ui.platform.LocalContext.current
  val store = remember { TravelStore(context.applicationContext) }
  val telemetry = remember { TravelTelemetry(store) }
  var selectedDestination by remember { mutableStateOf(travelDestinations.first()) }
  var selectedStay by remember { mutableStateOf(selectedDestination.stays.first()) }
  var confirmationId by remember { mutableStateOf<String?>(null) }
  var savedTrips by remember { mutableStateOf(emptyList<TravelTrip>()) }
  var isDemoControlsOpen by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val demoControls =
    TravelDemoControls(
      isOpen = isDemoControlsOpen,
      onOpen = { isDemoControlsOpen = true },
      onDismiss = { isDemoControlsOpen = false },
      onSlowSpanScenario = {
        scope.launch { telemetry.runSlowSpanDemoScenario() }
      },
      onFailedHttpScenario = {
        scope.launch { telemetry.runFailedHttpDemoScenario() }
      },
      onErrorScenario = {
        telemetry.simulateBookingFailure()
      },
    )

  MaterialTheme(
    colorScheme =
      androidx.compose.material3.lightColorScheme(
        primary = TravelNavy,
        secondary = TravelSky,
        tertiary = TravelStamp,
        surface = TravelPaper,
        background = TravelCloud,
        onPrimary = Color.White,
        onSecondary = TravelNavy,
        onSurface = TravelInk,
        onBackground = TravelInk,
      )
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = TravelCloud) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(TravelCloud, TravelPaper, Color(0xFFE7F3FA))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
      ) {
        NavHost(navController = navController, startDestination = TravelRoute.Home.route) {
          composable(TravelRoute.Home.route) {
            TravelHomeScreen(
              savedTripCount = savedTrips.size,
              demoControls = demoControls,
              onExplore = { navController.navigate(TravelRoute.Explore.route) },
              onTrips = {
                scope.launch {
                  savedTrips = telemetry.loadTrips(context)
                  navController.navigate(TravelRoute.MyTrips.route)
                }
              },
              onProfile = { navController.navigate(TravelRoute.Profile.route) },
              onSupport = { navController.navigate(TravelRoute.Support.route) },
              onRefreshDeals = {
                scope.launch { telemetry.refreshDeals("travel-home") }
              },
              onScorePicks = {
                scope.launch { telemetry.scoreRecommendations() }
              },
            )
          }
          composable(TravelRoute.Explore.route) {
            ExploreScreen(
              demoControls = demoControls,
              destinations = travelDestinations,
              onBack = { navController.popBackStack() },
              onSearch = { query ->
                scope.launch { telemetry.searchDestinations(query) }
              },
              onDestinationSelected = { destination ->
                selectedDestination = destination
                telemetry.addBreadcrumb("Opened ${destination.name}")
                navController.navigate(TravelRoute.Destination.route)
              },
            )
          }
          composable(TravelRoute.Destination.route) {
            DestinationScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              onBack = { navController.popBackStack() },
              onAvailability = {
                scope.launch { telemetry.checkAvailability(selectedDestination) }
              },
              onBuildItinerary = {
                scope.launch { telemetry.buildItinerary(selectedDestination) }
              },
              onStaySelected = { stay ->
                selectedStay = stay
                telemetry.addBreadcrumb("Selected stay ${stay.name}")
                navController.navigate(TravelRoute.Stay.route)
              },
            )
          }
          composable(TravelRoute.Stay.route) {
            StayScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              stay = selectedStay,
              onBack = { navController.popBackStack() },
              onReserve = {
                scope.launch {
                  telemetry.reserveStay(selectedDestination, selectedStay)
                  navController.navigate(TravelRoute.Review.route)
                }
              },
            )
          }
          composable(TravelRoute.Review.route) {
            ReviewScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              stay = selectedStay,
              onBack = { navController.popBackStack() },
              onConfirm = {
                scope.launch {
                  val id = telemetry.saveTrip(context, selectedDestination, selectedStay)
                  confirmationId = id
                  savedTrips = telemetry.loadTrips(context)
                  navController.navigate(TravelRoute.Confirmation.route)
                }
              },
            )
          }
          composable(TravelRoute.Confirmation.route) {
            ConfirmationScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              confirmationId = confirmationId ?: "TRAVEL-LOCAL",
              onTrips = {
                scope.launch {
                  savedTrips = telemetry.loadTrips(context)
                  navController.navigate(TravelRoute.MyTrips.route)
                }
              },
              onPlanAnother = {
                navController.popBackStack(TravelRoute.Home.route, inclusive = false)
              },
            )
          }
          composable(TravelRoute.MyTrips.route) {
            LaunchedEffect(Unit) { savedTrips = telemetry.loadTrips(context) }
            MyTripsScreen(
              demoControls = demoControls,
              trips = savedTrips,
              onBack = { navController.popBackStack() },
              onTripSelected = { trip ->
                confirmationId = trip.id
                selectedDestination =
                  travelDestinations.firstOrNull { it.name == trip.destinationName }
                    ?: travelDestinations.first()
                selectedStay = selectedDestination.stays.first()
                telemetry.addBreadcrumb("Opened saved trip ${trip.id}")
                navController.navigate(TravelRoute.TripDetail.route)
              },
            )
          }
          composable(TravelRoute.TripDetail.route) {
            TripDetailScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              confirmationId = confirmationId ?: "TRAVEL-LOCAL",
              onBack = { navController.popBackStack() },
              onDaySelected = { day -> navController.navigate("itinerary/$day") },
            )
          }
          composable(
            TravelRoute.Itinerary.route,
            arguments = listOf(navArgument("day") { type = NavType.IntType }),
          ) { entry ->
            val day = entry.arguments?.getInt("day") ?: 1
            ItineraryDayScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              day = day,
              onBack = { navController.popBackStack() },
              onActivity = { activityId -> navController.navigate("activity/$activityId") },
            )
          }
          composable(
            TravelRoute.Activity.route,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType }),
          ) { entry ->
            val activityId = entry.arguments?.getString("activityId") ?: "arrival"
            ActivityDetailScreen(
              demoControls = demoControls,
              destination = selectedDestination,
              activityId = activityId,
              onBack = { navController.popBackStack() },
            )
          }
          composable(TravelRoute.Profile.route) {
            ProfileScreen(
              demoControls = demoControls,
              onBack = { navController.popBackStack() },
              onSave = { airport, style ->
                scope.launch { telemetry.savePreferences(context, airport, style) }
              },
            )
          }
          composable(TravelRoute.Support.route) {
            SupportScreen(
              demoControls = demoControls,
              onBack = { navController.popBackStack() },
              onContact = {
                scope.launch { telemetry.contactSupport() }
              },
              onSimulateFailure = {
                telemetry.simulateBookingFailure()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TravelHomeScreen(
  demoControls: TravelDemoControls,
  savedTripCount: Int,
  onExplore: () -> Unit,
  onTrips: () -> Unit,
  onProfile: () -> Unit,
  onSupport: () -> Unit,
  onRefreshDeals: () -> Unit,
  onScorePicks: () -> Unit,
) {
  SentryTraced("sentry_travel_home") {
    TravelScaffold(
      title = "Sentry Travel",
      subtitle = "Plan your next traceable trip",
      demoControls = demoControls,
    ) {
      HeroCard(
        title = "Plan your next trip",
        body =
          "Browse destinations, reserve a stay, save a trip, and trigger spans Buddy can explain.",
      )
      TravelMetricRow(
        listOf(
          "${travelDestinations.size} destinations",
          "$savedTripCount saved trips",
          "HTTP + DB + app spans",
        )
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTravelButton("Explore", Modifier.weight(1f), onExplore)
        SecondaryTravelButton("My trips", Modifier.weight(1f), onTrips)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryTravelButton("Profile", Modifier.weight(1f), onProfile)
        SecondaryTravelButton("Support", Modifier.weight(1f), onSupport)
      }
      SectionTitle("Featured escapes")
      travelDestinations.forEach { DestinationCard(it, onClick = { onExplore() }) }
      SectionTitle("Span actions")
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryTravelButton("Refresh deals", Modifier.weight(1f), onRefreshDeals)
        SecondaryTravelButton("Score picks", Modifier.weight(1f), onScorePicks)
      }
    }
  }
}

@Composable
private fun ExploreScreen(
  demoControls: TravelDemoControls,
  destinations: List<TravelDestination>,
  onBack: () -> Unit,
  onSearch: (String) -> Unit,
  onDestinationSelected: (TravelDestination) -> Unit,
) {
  var query by remember { mutableStateOf("coast") }
  SentryTraced("sentry_travel_explore") {
    TravelScaffold(
      title = "Explore",
      subtitle = "Find the next stop",
      onBack = onBack,
      demoControls = demoControls,
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Destination style") },
        singleLine = true,
      )
      PrimaryTravelButton("Search destinations", Modifier.fillMaxWidth()) { onSearch(query) }
      destinations.forEach { destination ->
        DestinationCard(destination, onClick = { onDestinationSelected(destination) })
      }
    }
  }
}

@Composable
private fun DestinationScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  onBack: () -> Unit,
  onAvailability: () -> Unit,
  onBuildItinerary: () -> Unit,
  onStaySelected: (TravelStay) -> Unit,
) {
  SentryTraced("sentry_travel_destination_${destination.id}") {
    TravelScaffold(
      title = destination.name,
      subtitle = destination.tagline,
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(
        title = destination.name,
        body = destination.description,
        accent = destination.accent,
      )
      TravelMetricRow(destination.highlights)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTravelButton("Check availability", Modifier.weight(1f), onAvailability)
        SecondaryTravelButton("Build itinerary", Modifier.weight(1f), onBuildItinerary)
      }
      SectionTitle("Choose your stay")
      destination.stays.forEach { stay -> StayCard(stay) { onStaySelected(stay) } }
    }
  }
}

@Composable
private fun StayScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  stay: TravelStay,
  onBack: () -> Unit,
  onReserve: () -> Unit,
) {
  SentryTraced("sentry_travel_stay_${stay.id}") {
    TravelScaffold(
      title = stay.name,
      subtitle = destination.name,
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(title = stay.name, body = stay.description, accent = TravelGold)
      TravelMetricRow(listOf("${stay.nights} nights", "$${stay.price} total", stay.mood))
      SectionTitle("Included")
      stay.amenities.forEach {
        TimelineRow(title = it, body = "Included in this Sentry Travel plan")
      }
      PrimaryTravelButton("Reserve this stay", Modifier.fillMaxWidth(), onReserve)
    }
  }
}

@Composable
private fun ReviewScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  stay: TravelStay,
  onBack: () -> Unit,
  onConfirm: () -> Unit,
) {
  SentryTraced("sentry_travel_review") {
    TravelScaffold(
      title = "Review trip",
      subtitle = "Validate before saving",
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(
        title = destination.name,
        body = "${stay.name} for ${stay.nights} nights",
        accent = TravelSky,
      )
      TimelineRow("Booking validation", "Custom span checks dates, guests, and itinerary fit.")
      TimelineRow(
        "Local persistence",
        "Confirming writes a saved trip through SQLite instrumentation.",
      )
      PrimaryTravelButton("Confirm trip", Modifier.fillMaxWidth(), onConfirm)
    }
  }
}

@Composable
private fun ConfirmationScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  confirmationId: String,
  onTrips: () -> Unit,
  onPlanAnother: () -> Unit,
) {
  SentryTraced("sentry_travel_confirmation") {
    TravelScaffold(
      title = "Trip confirmed",
      subtitle = confirmationId,
      demoControls = demoControls,
    ) {
      HeroCard(
        title = "${destination.name} is ready",
        body =
          "Buddy can now correlate this saved trip with HTTP, DB, custom spans, and breadcrumbs.",
        accent = TravelGreen,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTravelButton("View saved trip", Modifier.weight(1f), onTrips)
        SecondaryTravelButton("Plan another", Modifier.weight(1f), onPlanAnother)
      }
    }
  }
}

@Composable
private fun MyTripsScreen(
  demoControls: TravelDemoControls,
  trips: List<TravelTrip>,
  onBack: () -> Unit,
  onTripSelected: (TravelTrip) -> Unit,
) {
  SentryTraced("sentry_travel_my_trips") {
    TravelScaffold(
      title = "My trips",
      subtitle = "Loaded from local storage",
      onBack = onBack,
      demoControls = demoControls,
    ) {
      if (trips.isEmpty()) {
        HeroCard(
          title = "No saved trips yet",
          body = "Confirm a trip to create a database span and see it here.",
        )
      } else {
        trips.forEach { trip ->
          TravelCard(onClick = { onTripSelected(trip) }) {
            Text(
              trip.destinationName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(trip.stayName, color = TravelMuted)
            Spacer(Modifier.height(8.dp))
            Text(trip.id, color = TravelGold, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun TripDetailScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  confirmationId: String,
  onBack: () -> Unit,
  onDaySelected: (Int) -> Unit,
) {
  SentryTraced("sentry_travel_trip_detail") {
    TravelScaffold(
      title = "Trip detail",
      subtitle = confirmationId,
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(title = destination.name, body = "Three days of instrumented itinerary steps.")
      (1..3).forEach { day ->
        TravelCard(onClick = { onDaySelected(day) }) {
          Text(
            "Day $day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(destination.itinerary[day - 1], color = TravelMuted)
        }
      }
    }
  }
}

@Composable
private fun ItineraryDayScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  day: Int,
  onBack: () -> Unit,
  onActivity: (String) -> Unit,
) {
  SentryTraced("sentry_travel_itinerary_day_$day") {
    TravelScaffold(
      title = "Day $day",
      subtitle = destination.name,
      onBack = onBack,
      demoControls = demoControls,
    ) {
      destination.activities.forEach { activity ->
        TimelineRow(activity.title, activity.body, onClick = { onActivity(activity.id) })
      }
    }
  }
}

@Composable
private fun ActivityDetailScreen(
  demoControls: TravelDemoControls,
  destination: TravelDestination,
  activityId: String,
  onBack: () -> Unit,
) {
  val activity =
    destination.activities.firstOrNull { it.id == activityId } ?: destination.activities.first()
  SentryTraced("sentry_travel_activity_${activity.id}") {
    TravelScaffold(
      title = activity.title,
      subtitle = destination.name,
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(title = activity.title, body = activity.body, accent = TravelCoral)
      TravelMetricRow(listOf("Low risk", "2 app spans", "Replay-friendly"))
      TimelineRow("Why Buddy cares", "This deep screen verifies route depth and timeline ordering.")
    }
  }
}

@Composable
private fun ProfileScreen(
  demoControls: TravelDemoControls,
  onBack: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var airport by remember { mutableStateOf("SFO") }
  var style by remember { mutableStateOf("Slow mornings") }
  SentryTraced("sentry_travel_profile") {
    TravelScaffold(
      title = "Traveler profile",
      subtitle = "Preference DB spans",
      onBack = onBack,
      demoControls = demoControls,
    ) {
      OutlinedTextField(
        value = airport,
        onValueChange = { airport = it.uppercase().take(3) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Home airport") },
        singleLine = true,
      )
      OutlinedTextField(
        value = style,
        onValueChange = { style = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Travel style") },
        singleLine = true,
      )
      PrimaryTravelButton("Save preferences", Modifier.fillMaxWidth()) { onSave(airport, style) }
    }
  }
}

@Composable
private fun SupportScreen(
  demoControls: TravelDemoControls,
  onBack: () -> Unit,
  onContact: () -> Unit,
  onSimulateFailure: () -> Unit,
) {
  SentryTraced("sentry_travel_support") {
    TravelScaffold(
      title = "Travel support",
      subtitle = "HTTP and error events",
      onBack = onBack,
      demoControls = demoControls,
    ) {
      HeroCard(
        title = "Need help with the itinerary?",
        body = "Contact support for an HTTP span or simulate a booking failure for error capture.",
      )
      PrimaryTravelButton("Contact support", Modifier.fillMaxWidth(), onContact)
      SecondaryTravelButton("Simulate booking failure", Modifier.fillMaxWidth(), onSimulateFailure)
    }
  }
}

@Composable
private fun TravelScaffold(
  title: String,
  subtitle: String,
  demoControls: TravelDemoControls,
  onBack: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) {
        OutlinedButton(onClick = onBack, modifier = Modifier.width(88.dp)) { Text("Back") }
        Spacer(Modifier.width(12.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(subtitle, color = TravelMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      Surface(
        modifier = Modifier.size(44.dp),
        color = TravelPaper,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, TravelTicketEdge),
      ) {
        IconButton(onClick = demoControls.onOpen) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Open presenter controls",
            tint = TravelNavy,
          )
        }
      }
    }
    content()
    if (demoControls.isOpen) {
      AlertDialog(
        onDismissRequest = demoControls.onDismiss,
        confirmButton = {
          TextButton(onClick = demoControls.onDismiss) { Text("Close") }
        },
        title = { Text("Demo controls", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              "Permanent demo bindings:\n\"Search Destinations\" captures an exception; \"Build Itinerary\" " +
                "creates a slow DB span; and \"Reserve This Stay\" creates a slow HTTP span.",
              color = TravelStamp,
            )
            Text(
              "Extra one-tap triggers are still available below for presenter resets.",
              color = TravelMuted,
            )
            SecondaryTravelButton(
              "Slow span",
              Modifier.fillMaxWidth(),
              demoControls.onSlowSpanScenario,
            )
            SecondaryTravelButton(
              "HTTP 503",
              Modifier.fillMaxWidth(),
              demoControls.onFailedHttpScenario,
            )
            SecondaryTravelButton(
              "Captured error",
              Modifier.fillMaxWidth(),
              demoControls.onErrorScenario,
            )
          }
        },
      )
    }
  }
}

private data class TravelDemoControls(
  val isOpen: Boolean,
  val onOpen: () -> Unit,
  val onDismiss: () -> Unit,
  val onSlowSpanScenario: () -> Unit,
  val onFailedHttpScenario: () -> Unit,
  val onErrorScenario: () -> Unit,
)

@Composable
private fun HeroCard(title: String, body: String, accent: Color = TravelSky) {
  Card(
    colors = CardDefaults.cardColors(containerColor = TravelPaper),
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.fillMaxWidth().border(1.dp, TravelTicketEdge, RoundedCornerShape(24.dp)),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent)))
          .padding(20.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "BOARDING PASS",
          color = TravelStamp,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          title,
          color = TravelInk,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(body, color = TravelMuted)
      }
    }
  }
}

@Composable
private fun DestinationCard(destination: TravelDestination, onClick: () -> Unit) {
  TravelCard(onClick = onClick) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier =
          Modifier.size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(destination.accent, TravelSky)))
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          destination.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(destination.tagline, color = TravelMuted)
      }
      Text(
        "$${destination.stays.minOf { it.price }}+",
        color = TravelGold,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun StayCard(stay: TravelStay, onClick: () -> Unit) {
  TravelCard(onClick = onClick) {
    Text(stay.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(stay.description, color = TravelMuted)
    Spacer(Modifier.height(10.dp))
    TravelMetricRow(listOf("${stay.nights} nights", "$${stay.price}", stay.mood))
  }
}

@Composable
private fun TravelCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    colors = CardDefaults.cardColors(containerColor = TravelPaper),
    shape = RoundedCornerShape(20.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, TravelTicketEdge),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      content()
    }
  }
}

@Composable
private fun TimelineRow(title: String, body: String, onClick: (() -> Unit)? = null) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.size(12.dp).background(TravelStamp, CircleShape))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.Bold)
      Text(body, color = TravelMuted)
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text.uppercase(Locale.ROOT),
    color = TravelInk,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.ExtraBold,
  )
}

@Composable
private fun TravelMetricRow(values: List<String>) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    values.take(3).forEach { value ->
      FilterChip(selected = true, onClick = {}, label = { Text(value, maxLines = 1) })
    }
  }
}

@Composable
private fun PrimaryTravelButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = modifier.height(52.dp),
    colors = ButtonDefaults.buttonColors(containerColor = TravelNavy),
    shape = RoundedCornerShape(12.dp),
  ) {
    Text(text, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun SecondaryTravelButton(
  text: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.height(52.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = TravelNavy),
  ) {
    Text(text, fontWeight = FontWeight.Bold)
  }
}

private class TravelTelemetry(private val store: TravelStore) {
  suspend fun refreshDeals(source: String): String =
    withAppSpan("travel.http.refresh_deals", "Refresh Sentry Travel deals") {
      addBreadcrumb("Refresh deals from $source")
      runHttpAction("Refreshed sample travel deals")
    }

  suspend fun checkAvailability(destination: TravelDestination): String =
    withAppSpan("travel.http.availability", "Check availability for ${destination.name}") {
      addBreadcrumb("Checked availability for ${destination.name}")
      runHttpAction("Availability loaded for ${destination.name}")
    }

  suspend fun contactSupport(): String =
    withAppSpan("travel.http.support", "Contact Sentry Travel support") {
      addBreadcrumb("Contacted travel support")
      runHttpAction("Support case hydrated from HTTP")
    }

  suspend fun searchDestinations(query: String): String =
    withAppSpan("travel.search", "Search destinations") {
      delay(120)
      Sentry.captureException(IllegalStateException("Simulated Sentry Travel search failure"))
      addBreadcrumb("Searched destinations for $query")
      "Captured a simulated search failure for '$query'."
    }

  suspend fun scoreRecommendations(): String =
    withAppSpan("travel.recommendations.score", "Score travel recommendations") {
      repeat(8_000) { (it * 31).hashCode() }
      addBreadcrumb("Scored recommended destinations")
      "Recommendation scoring completed."
    }

  suspend fun runSlowSpanDemoScenario(): String =
    withAppSpan("travel.demo.slow_span", "Run slow span Buddy demo flow") {
      addBreadcrumb("Ran slow span Buddy demo scenario")
      delay(1200)
      "Slow span ready. Buddy should flag a medium-severity performance issue."
    }

  suspend fun runFailedHttpDemoScenario(): String =
    withAppSpan("travel.demo.failed_http", "Run failed HTTP Buddy demo flow") {
      addBreadcrumb("Ran failed HTTP Buddy demo scenario")
      Sentry.addBreadcrumb(
        Breadcrumb.http("https://demo.sentry.dev/travel/availability", "GET", 503)
      )
      delay(120)
      "HTTP 503 ready. Buddy should flag a high-severity failed HTTP request."
    }

  suspend fun buildItinerary(destination: TravelDestination): String =
    withAppSpan("db.sql.query", "Load itinerary recommendations for ${destination.name}") {
      delay(1200)
      addBreadcrumb("Built itinerary for ${destination.name}")
      "Generated a three-day itinerary for ${destination.name}."
    }

  suspend fun reserveStay(destination: TravelDestination, stay: TravelStay): String =
    withAppSpan("http.client", "Reserve ${stay.name} for ${destination.name}") {
      delay(1200)
      addBreadcrumb("Reserved ${stay.name} for ${destination.name}")
      runHttpAction("Reservation hold created for ${stay.name}")
    }

  suspend fun saveTrip(
    context: Context,
    destination: TravelDestination,
    stay: TravelStay,
  ): String =
    withAppSpan("travel.db.save_trip", "Save Sentry Travel trip") {
      addBreadcrumb("Saved trip for ${destination.name}")
      store.saveTrip(context, destination, stay)
    }

  suspend fun loadTrips(context: Context): List<TravelTrip> =
    withAppSpan("travel.db.load_trips", "Load Sentry Travel trips") { store.loadTrips(context) }

  suspend fun savePreferences(context: Context, airport: String, style: String): String =
    withAppSpan("travel.db.save_preferences", "Save traveler preferences") {
      addBreadcrumb("Saved traveler preferences")
      store.savePreferences(context, airport, style)
      "Saved $airport preferences for $style."
    }

  fun simulateBookingFailure(): String {
    val exception = IllegalStateException("Simulated Sentry Travel booking failure")
    addBreadcrumb("Simulated booking failure")
    Sentry.captureException(exception)
    return "Captured a simulated booking failure."
  }

  fun addBreadcrumb(message: String) {
    Sentry.addBreadcrumb(message, "sentry-travel")
  }

  private suspend fun runHttpAction(successMessage: String): String =
    try {
      val repos = GithubAPI.service.listReposAsync("getsentry", 3)
      "$successMessage (${repos.size} network rows)."
    } catch (exception: Exception) {
      Sentry.captureException(exception)
      "HTTP request failed gracefully: ${exception.javaClass.simpleName}."
    }

  private suspend fun <T> withAppSpan(op: String, description: String, block: suspend () -> T): T {
    val span =
      Sentry.getSpan()?.startChild(op, description) ?: Sentry.startTransaction(description, op)
    return try {
      block().also { span.finish(SpanStatus.OK) }
    } catch (exception: Exception) {
      span.finish(SpanStatus.INTERNAL_ERROR)
      throw exception
    }
  }
}

private class TravelStore(private val appContext: Context) {
  suspend fun saveTrip(context: Context, destination: TravelDestination, stay: TravelStay): String =
    withContext(Dispatchers.IO) {
      val id = "TRIP-${System.currentTimeMillis().toString().takeLast(5)}"
      val db = writableDb(context)
      ensureSchema(db)
      db.execSQL(
        "INSERT OR REPLACE INTO sentry_travel_trips(id, destination, stay, price) VALUES (?, ?, ?, ?)",
        arrayOf<Any>(id, destination.name, stay.name, stay.price),
      )
      id
    }

  suspend fun loadTrips(context: Context): List<TravelTrip> =
    withContext(Dispatchers.IO) {
      val db = writableDb(context)
      ensureSchema(db)
      db
        .query("SELECT id, destination, stay, price FROM sentry_travel_trips ORDER BY id DESC")
        .use { cursor ->
          buildList {
            while (cursor.moveToNext()) {
              add(
                TravelTrip(
                  id = cursor.getString(0),
                  destinationName = cursor.getString(1),
                  stayName = cursor.getString(2),
                  price = cursor.getInt(3),
                )
              )
            }
          }
        }
    }

  suspend fun savePreferences(context: Context, airport: String, style: String) {
    withContext(Dispatchers.IO) {
      val db = writableDb(context)
      ensureSchema(db)
      db.execSQL(
        "INSERT OR REPLACE INTO sentry_travel_preferences(id, airport, style) VALUES (1, ?, ?)",
        arrayOf(airport, style),
      )
    }
  }

  private fun writableDb(context: Context): SupportSQLiteDatabase =
    SampleDatabases.directHelper(context.applicationContext ?: appContext).writableDatabase

  private fun ensureSchema(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS sentry_travel_trips(" +
        "id TEXT PRIMARY KEY, destination TEXT NOT NULL, stay TEXT NOT NULL, price INTEGER NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS sentry_travel_preferences(" +
        "id INTEGER PRIMARY KEY, airport TEXT NOT NULL, style TEXT NOT NULL)"
    )
  }
}

private sealed class TravelRoute(val route: String) {
  object Home : TravelRoute("home")

  object Explore : TravelRoute("explore")

  object Destination : TravelRoute("destination")

  object Stay : TravelRoute("stay")

  object Review : TravelRoute("review")

  object Confirmation : TravelRoute("confirmation")

  object MyTrips : TravelRoute("my-trips")

  object TripDetail : TravelRoute("trip-detail")

  object Itinerary : TravelRoute("itinerary/{day}")

  object Activity : TravelRoute("activity/{activityId}")

  object Profile : TravelRoute("profile")

  object Support : TravelRoute("support")
}

private data class TravelDestination(
  val id: String,
  val name: String,
  val tagline: String,
  val description: String,
  val accent: Color,
  val highlights: List<String>,
  val itinerary: List<String>,
  val stays: List<TravelStay>,
  val activities: List<TravelActivity>,
)

private data class TravelStay(
  val id: String,
  val name: String,
  val description: String,
  val nights: Int,
  val price: Int,
  val mood: String,
  val amenities: List<String>,
)

private data class TravelActivity(val id: String, val title: String, val body: String)

private data class TravelTrip(
  val id: String,
  val destinationName: String,
  val stayName: String,
  val price: Int,
)

private val travelDestinations =
  listOf(
    TravelDestination(
      id = "kyoto",
      name = "Kyoto Trace Garden",
      tagline = "Temples, tea, and tidy spans",
      description =
        "A slow, scenic itinerary through lantern streets, moss gardens, and calm cafes.",
      accent = Color(0xFF7EC8E3),
      highlights = listOf("3 days", "8 spans", "Great for scroll replay"),
      itinerary = listOf("Arrival and tea lanes", "Garden walk and market", "Sunrise shrine loop"),
      stays =
        listOf(
          TravelStay(
            "ryokan",
            "Telemetry Ryokan",
            "Quiet rooms, garden breakfast, and explicit app spans around every plan change.",
            3,
            1280,
            "Calm",
            listOf("Garden breakfast", "Late checkout", "Rail pass planning"),
          ),
          TravelStay(
            "loft",
            "Span Loft Kyoto",
            "Modern base near the market, built for fast itinerary changes.",
            2,
            940,
            "Central",
            listOf("Market access", "Workspace", "Evening ramen map"),
          ),
        ),
      activities =
        listOf(
          TravelActivity("tea", "Tea lane walk", "A gentle intro that creates clean route depth."),
          TravelActivity(
            "market",
            "Nishiki market pass",
            "Dense interaction area for breadcrumbs.",
          ),
          TravelActivity(
            "shrine",
            "Sunrise shrine loop",
            "A deep final detail screen for Buddy ordering.",
          ),
        ),
    ),
    TravelDestination(
      id = "lisbon",
      name = "Lisbon Release Coast",
      tagline = "Tiles, trams, and golden-hour traces",
      description = "A coastal city break with food stops and a high-signal booking flow.",
      accent = Color(0xFFE35D52),
      highlights = listOf("4 days", "HTTP actions", "DB save path"),
      itinerary = listOf("Tile walk", "Coastline train", "Fado dinner"),
      stays =
        listOf(
          TravelStay(
            "alfama",
            "Alfama Error Budget Inn",
            "Compact rooms above the old town with fast support escalation.",
            4,
            1160,
            "Lively",
            listOf("Tram pass", "Breakfast", "Support desk"),
          )
        ),
      activities =
        listOf(
          TravelActivity("tiles", "Azulejo route", "Colorful route useful for UI polish checks."),
          TravelActivity("tram", "Tram 28 checkpoint", "Tap-heavy itinerary stop."),
          TravelActivity("fado", "Fado dinner", "Final detail screen with human-readable context."),
        ),
    ),
  )

private val TravelNavy = Color(0xFF102A43)
private val TravelSky = Color(0xFF7EC8E3)
private val TravelStamp = Color(0xFFE35D52)
private val TravelCoral = Color(0xFFF3A261)
private val TravelGold = Color(0xFFD8A441)
private val TravelGreen = Color(0xFF2A9D8F)
private val TravelPaper = Color(0xFFFFFCF4)
private val TravelCloud = Color(0xFFF3F8FB)
private val TravelInk = Color(0xFF162334)
private val TravelMuted = Color(0xFF66798A)
private val TravelTicketEdge = Color(0xFFD9E5EC)
