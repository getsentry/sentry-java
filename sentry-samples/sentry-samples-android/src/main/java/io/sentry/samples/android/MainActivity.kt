@file:OptIn(ExperimentalComposeUiApi::class)

package io.sentry.samples.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sentry.Attachment
import io.sentry.MeasurementUnit
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.UpdateStatus
import io.sentry.compose.SentryTraced
import io.sentry.compose.SentryUserFeedbackButton
import io.sentry.protocol.User
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : AppCompatActivity() {

  private var screenLoadCount = 0
  internal lateinit var imageFile: File

  @SuppressLint("NewApi")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SharedState.isOrientationChange = intent.getBooleanExtra("isOrientationChange", false)

    imageFile = createSentryImageFile()

    val image = Attachment(imageFile.absolutePath, "sentry.png", "image/png")
    Sentry.configureScope { scope -> scope.addAttachment(image) }

    Sentry.logger().log(SentryLogLevel.INFO, "Creating content view")

    setContent {
      val colorScheme =
        if (isSystemInDarkTheme())
          darkColorScheme(
            primary = Color(resources.getColor(R.color.colorPrimary, theme)),
            secondary = Color(resources.getColor(R.color.colorAccent, theme)),
            tertiary = Color(resources.getColor(R.color.colorPrimary, theme)),
          )
        else
          lightColorScheme(
            primary = Color(resources.getColor(R.color.colorPrimary, theme)),
            secondary = Color(resources.getColor(R.color.colorAccent, theme)),
            tertiary = Color(resources.getColor(R.color.colorPrimary, theme)),
          )
      MaterialTheme(colorScheme = colorScheme) { MainScreen() }
    }

    Sentry.logger().log(SentryLogLevel.INFO, "MainActivity created")
  }

  override fun onResume() {
    super.onResume()
    screenLoadCount++
    val span = Sentry.getSpan()
    if (span != null) {
      val measurementSpan = span.startChild("screen_load_measurement", "test measurement")
      measurementSpan.setMeasurement(
        "screen_load_count",
        screenLoadCount,
        MeasurementUnit.Custom("test"),
      )
      measurementSpan.finish()
    }
    Sentry.reportFullyDisplayed()
  }

  private fun createSentryImageFile(): File {
    val file = applicationContext.getFileStreamPath("sentry.png")
    try {
      applicationContext.resources.openRawResource(R.raw.sentry).use { inputStream ->
        FileOutputStream(file).use { outputStream ->
          val bytes = ByteArray(1024)
          var length = inputStream.read(bytes)
          while (length != -1) {
            outputStream.write(bytes, 0, length)
            length = inputStream.read(bytes)
          }
          outputStream.flush()
        }
      }
    } catch (e: IOException) {
      Sentry.captureException(e)
    }
    return file
  }
}

enum class Category(val displayName: String, val icon: ImageVector) {
  ERRORS("Errors", Icons.Filled.Error),
  TRACING("Tracing", Icons.Filled.Speed),
  SESSION_REPLAY("Session Replay", Icons.Filled.Videocam),
  USER_FEEDBACK("User & Feedback", Icons.Filled.Person),
  INTEGRATIONS("Integrations", Icons.Filled.Extension),
  UPDATES("Updates", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
  var selectedCategory by remember { mutableStateOf(Category.ERRORS) }

  Surface(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
      CategoryNavigationRail(
        selectedCategory = selectedCategory,
        onCategorySelected = { selectedCategory = it },
      )
      Surface(modifier = Modifier.fillMaxSize()) {
        when (selectedCategory) {
          Category.ERRORS -> ErrorsScreen()
          Category.TRACING -> TracingScreen()
          Category.SESSION_REPLAY -> SessionReplayScreen()
          Category.USER_FEEDBACK -> UserFeedbackScreen()
          Category.INTEGRATIONS -> IntegrationsScreen()
          Category.UPDATES -> UpdatesScreen()
        }
      }
    }
  }
}

@Composable
fun CategoryNavigationRail(
  selectedCategory: Category,
  onCategorySelected: (Category) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  NavigationRail(
    modifier =
      modifier.fillMaxHeight().defaultMinSize(minWidth = 100.dp).verticalScroll(scrollState),
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Spacer(Modifier.height(16.dp))
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(1f) }

    Icon(
      painterResource(R.drawable.sentry_glyph),
      contentDescription = "Sentry Logo",
      tint = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier =
        Modifier.size(48.dp)
          .shadow(4.dp, shape = CircleShape)
          .background(color = MaterialTheme.colorScheme.surfaceBright, shape = CircleShape)
          .clickable { scope.launch { rotation.animateTo(rotation.targetValue + 360.0f) } }
          .padding(12.dp)
          .rotate(rotation.value),
    )
    Spacer(Modifier.height(16.dp))
    Category.entries.forEach { category ->
      NavigationRailItem(
        modifier = Modifier.defaultMinSize(minWidth = 100.dp),
        selected = selectedCategory == category,
        onClick = { onCategorySelected(category) },
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
          ),
        icon = { Icon(imageVector = category.icon, contentDescription = category.displayName) },
        alwaysShowLabel = true,
        label = {
          Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
          )
        },
      )
    }
  }
}

@Composable
fun ErrorsScreen() {
  val crashCount = remember { mutableIntStateOf(0) }
  val mutex = remember { Object() }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("crash_from_java") {
        OutlinedButton(onClick = { throw RuntimeException("Uncaught Exception from Java.") }) {
          Text("Crash from Java", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("capture_exception") {
        OutlinedButton(
          onClick = { Sentry.captureException(Exception(Exception(Exception("Some exception.")))) },
          modifier = Modifier,
        ) {
          Text("Capture Exception", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("breadcrumb") {
        OutlinedButton(
          onClick = {
            Sentry.addBreadcrumb("Breadcrumb")
            Sentry.setExtra("extra", "extra")
            Sentry.setFingerprint(listOf("fingerprint"))
            Sentry.setTransaction("transaction")
            Sentry.captureException(Exception("Some exception with scope."))
          },
          modifier = Modifier,
        ) {
          Text("Breadcrumb", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("stack_overflow") {
        OutlinedButton(onClick = { stackOverflow() }, modifier = Modifier) {
          Text("Stack Overflow", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("native_crash") {
        OutlinedButton(onClick = { NativeSample.crash() }, modifier = Modifier) {
          Text("Native Crash", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("native_capture") {
        OutlinedButton(onClick = { NativeSample.message() }, modifier = Modifier) {
          Text("Native Capture", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("anr") {
        OutlinedButton(
          onClick = {
            Thread {
                synchronized(mutex) {
                  while (true) {
                    try {
                      Thread.sleep(10000)
                    } catch (e: InterruptedException) {
                      e.printStackTrace()
                    }
                  }
                }
              }
              .start()

            Handler(Looper.getMainLooper())
              .postDelayed({ synchronized(mutex) { throw IllegalStateException() } }, 1000)
          },
          modifier = Modifier,
        ) {
          Text("ANR", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("native_anr") {
        OutlinedButton(
          onClick = {
            Thread { NativeSample.freezeMysteriously(mutex) }.start()

            Handler(Looper.getMainLooper())
              .postDelayed({ synchronized(mutex) { throw IllegalStateException() } }, 1000)
          },
          modifier = Modifier,
        ) {
          Text("ANR (native)", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("out_of_memory") {
        OutlinedButton(
          onClick = {
            val latch = CountDownLatch(1)
            for (i in 0 until 20) {
              Thread {
                  val data = ArrayList<String>()
                  try {
                    latch.await()
                    for (j in 0 until 1_000_000) {
                      data.add(String(ByteArray(1024 * 8)))
                    }
                  } catch (e: InterruptedException) {
                    e.printStackTrace()
                  }
                }
                .start()
            }
            latch.countDown()
          },
          modifier = Modifier,
        ) {
          Text("Out of Memory", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("send_message") {
        OutlinedButton(onClick = { Sentry.captureMessage("Some message.") }, modifier = Modifier) {
          Text("Send Message", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("test_timber") {
        OutlinedButton(
          onClick = {
            crashCount.intValue++
            Timber.i("Some info here")
            Timber.e(
              RuntimeException("Uncaught Exception from Java."),
              "Something wrong happened ${crashCount.intValue} times",
            )
          },
          modifier = Modifier,
        ) {
          Text("Test Timber", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
fun TracingScreen() {
  val activity = LocalContext.current.getActivity()

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("open_second_activity") {
        OutlinedButton(
          onClick = {
            activity.finish()
            activity.startActivity(Intent(activity, SecondActivity::class.java))
          },
          modifier = Modifier,
        ) {
          Text("Open Second Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_gestures_activity") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, GesturesActivity::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Gestures Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_frame_data") {
        OutlinedButton(
          onClick = {
            activity.startActivity(Intent(activity, FrameDataForSpansActivity::class.java))
          },
          modifier = Modifier,
        ) {
          Text("Open Frame Data for Spans", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_profiling") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, ProfilingActivity::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Profiling Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun SessionReplayScreen() {
  val activity = LocalContext.current.getActivity()
  var showDialog by remember { mutableStateOf(false) }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("enable_replay_debug") {
        OutlinedButton(
          onClick = { Sentry.replay().enableDebugMaskingOverlay() },
          modifier = Modifier,
        ) {
          Text("Enable Replay Debug Mode", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("show_dialog") {
        OutlinedButton(onClick = { showDialog = true }, modifier = Modifier) {
          Text("Show Dialog", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }

  // AlertDialog managed by local state
  if (showDialog) {
    AlertDialog(
      onDismissRequest = {
        if (SharedState.isOrientationChange) {
          val currentOrientation = activity.resources.configuration.orientation
          if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
          } else if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
          }
        } else {
          showDialog = false
        }
      },
      title = { Text("Example Title") },
      text = { Text("Example Message") },
      confirmButton = {
        TextButton(
          onClick = {
            if (SharedState.isOrientationChange) {
              val currentOrientation = activity.resources.configuration.orientation
              if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
              } else if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
              }
            } else {
              showDialog = false
            }
          }
        ) {
          Text("Close")
        }
      },
    )
  }
}

@Composable
fun UserFeedbackScreen() {
  val activity = LocalContext.current.getActivity()

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("set_user") {
        OutlinedButton(
          onClick = {
            Sentry.setTag("user_set", "instance")
            val user =
              User().apply {
                username = "username_from_java"
                email = "email_from_java"
                ipAddress = "{{auto}}"
              }
            Sentry.setUser(user)
          },
          modifier = Modifier,
        ) {
          Text("Set User", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("unset_user") {
        OutlinedButton(
          onClick = {
            Sentry.setTag("user_set", "null")
            Sentry.setUser(null)
          },
          modifier = Modifier,
        ) {
          Text("Unset User", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("add_attachment") {
        OutlinedButton(
          onClick = {
            val fileName = Calendar.getInstance().timeInMillis.toString() + "_file.txt"
            val file = activity.application.getFileStreamPath(fileName)
            try {
              io.sentry.instrumentation.file.SentryFileOutputStream.Factory.create(
                  FileOutputStream(file),
                  file,
                )
                .use { fos -> fos.write("Hello, World!".toByteArray()) }
            } catch (e: IOException) {
              Sentry.captureException(e)
            }

            Sentry.configureScope { scope ->
              val json = "{ \"number\": 10 }"
              val attachment = Attachment(json.toByteArray(), "log.json")
              scope.addAttachment(attachment)
              scope.addAttachment(Attachment(file.path))
            }
          },
          modifier = Modifier,
        ) {
          Text("Add Attachment", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    // SentryUserFeedbackButton as a special item
    item(span = { GridItemSpan(maxLineSpan) }) { SentryUserFeedbackButton(modifier = Modifier) }
  }
}

@Composable
fun IntegrationsScreen() {
  val activity = LocalContext.current.getActivity()

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("open_compose_activity") {
        OutlinedButton(
          onClick = {
            activity.startActivity(
              Intent(activity, io.sentry.samples.android.compose.ComposeActivity::class.java)
            )
          },
          modifier = Modifier,
        ) {
          Text("Open Compose Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_sample_fragment") {
        OutlinedButton(
          onClick = {
            SampleFragment.newInstance()
              .show((activity as AppCompatActivity).supportFragmentManager, null)
          },
          modifier = Modifier,
        ) {
          Text("Open Sample Fragment", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_third_fragment") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, ThirdActivityFragment::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Third Fragment", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_permissions_activity") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, PermissionsActivity::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Permissions Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_custom_tabs_activity") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, CustomTabsActivity::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Custom Tabs Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_camera_activity") {
        OutlinedButton(
          onClick = { activity.startActivity(Intent(activity, CameraXActivity::class.java)) },
          modifier = Modifier,
        ) {
          Text("Open Camera Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("open_http_request_activity") {
        OutlinedButton(
          onClick = {
            activity.startActivity(Intent(activity, TriggerHttpRequestActivity::class.java))
          },
          modifier = Modifier,
        ) {
          Text("Open HTTP Request Activity", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item {
      SentryTraced("throw_in_coroutine") {
        OutlinedButton(onClick = { CoroutinesUtil.throwInCoroutine() }, modifier = Modifier) {
          Text("Throw in Coroutine", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
fun UpdatesScreen() {
  val activity = LocalContext.current.getActivity()

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 180.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SentryTraced("check_for_update") {
        OutlinedButton(
          onClick = {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show()
            val future = Sentry.distribution().checkForUpdate()

            Thread {
                try {
                  val result = future.get()
                  activity.runOnUiThread {
                    val message =
                      when (result) {
                        is UpdateStatus.NewRelease -> {
                          "Update available: ${result.info.buildVersion} " +
                            "(Build ${result.info.buildNumber})\n" +
                            "Download URL: ${result.info.downloadUrl}"
                        }

                        is UpdateStatus.UpToDate -> "App is up to date!"
                        is UpdateStatus.NoNetwork -> "No network connection: ${result.message}"
                        is UpdateStatus.UpdateError ->
                          "Error checking for updates: ${result.message}"

                        else -> "Unknown status"
                      }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                  }
                } catch (e: Exception) {
                  activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Error checking for updates: ${e.message}",
                        Toast.LENGTH_LONG,
                      )
                      .show()
                  }
                }
              }
              .start()
          },
          modifier = Modifier,
        ) {
          Text("Check for Update", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

fun Context.getActivity(): ComponentActivity {
  var currentContext = this
  while (currentContext is ContextWrapper) {
    if (currentContext is ComponentActivity) {
      return currentContext
    }
    currentContext = currentContext.baseContext
  }
  if (currentContext is ComponentActivity) {
    return currentContext
  }
  throw IllegalArgumentException("Context is not an Activity.")
}

fun stackOverflow() {
  stackOverflow()
}
