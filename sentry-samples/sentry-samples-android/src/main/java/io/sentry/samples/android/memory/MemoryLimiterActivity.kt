package io.sentry.samples.android.memory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val chunkSizeMb = 8
private const val chunkDelayMs = 120L
private const val manualLimit = "256MB"
private const val mib = 1024 * 1024

private val sentryPurple = Color(0xFF7B52FB)
private val sentryPurpleTint = Color(0xFFF2ECFF)
private val cautionTint = Color(0xFFFFF1DB)

/**
 * Manual sample for Android 17's MemoryLimiter flow.
 *
 * The app cannot synthesize the ApplicationExitInfo signature on its own, so this screen pairs a
 * deterministic allocator with the adb commands Android exposes for manually lowering a process'
 * MemoryLimiter threshold.
 */
class MemoryLimiterActivity : ComponentActivity() {

  private val retainedAllocations = Collections.synchronizedList(mutableListOf<ByteArray>())
  private var allocationJob: Job? = null

  private var allocatedMb by mutableIntStateOf(0)
  private var isAllocating by mutableStateOf(false)
  private var statusLine by mutableStateOf("Idle. Apply a manual limit, then start allocating.")

  private val isSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme {
        MemoryLimiterScreen(
          isSupported = isSupported,
          packageName = packageName,
          pid = Process.myPid(),
          allocatedMb = allocatedMb,
          isAllocating = isAllocating,
          statusLine = statusLine,
          onCopy = ::copyCommand,
          onStartAllocating = ::startAllocating,
          onReleaseBuffers = ::releaseBuffers,
          onMoveToBackground = { moveTaskToBack(true) },
        )
      }
    }
  }

  override fun onDestroy() {
    allocationJob?.cancel()
    allocationJob = null
    retainedAllocations.clear()
    super.onDestroy()
  }

  private fun startAllocating() {
    if (!isSupported || allocationJob != null) {
      return
    }

    statusLine = "Allocating ${chunkSizeMb} MiB chunks every ${chunkDelayMs} ms."
    isAllocating = true

    allocationJob =
      lifecycleScope.launch(Dispatchers.Default) {
        try {
          while (isActive) {
            retainedAllocations.add(ByteArray(chunkSizeMb * mib))
            val total = retainedAllocations.size * chunkSizeMb
            withContext(Dispatchers.Main.immediate) {
              allocatedMb = total
              statusLine = "Holding about $total MiB in memory."
            }
            delay(chunkDelayMs)
          }
        } catch (oom: OutOfMemoryError) {
          withContext(Dispatchers.Main.immediate) {
            allocationJob = null
            isAllocating = false
            retainedAllocations.clear()
            allocatedMb = 0
            statusLine =
              "Hit java.lang.OutOfMemoryError before the OS applied a MemoryLimiter kill."
          }
        }
      }
  }

  private fun releaseBuffers() {
    allocationJob?.cancel()
    allocationJob = null
    isAllocating = false
    retainedAllocations.clear()
    allocatedMb = 0
    statusLine = "Released retained buffers and requested a fresh start for the next run."
    Runtime.getRuntime().gc()
  }

  private fun copyCommand(command: String, label: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, command))
    Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
  }
}

@Composable
private fun MemoryLimiterScreen(
  isSupported: Boolean,
  packageName: String,
  pid: Int,
  allocatedMb: Int,
  isAllocating: Boolean,
  statusLine: String,
  onCopy: (String, String) -> Unit,
  onStartAllocating: () -> Unit,
  onReleaseBuffers: () -> Unit,
  onMoveToBackground: () -> Unit,
) {
  val commandRows =
    listOf(
      CommandRow("Resolve PID", "adb shell pidof $packageName"),
      CommandRow("Inspect limiter", "adb shell am memory-limiter status"),
      CommandRow("Apply limit", "adb shell am memory-limiter manual $pid $manualLimit"),
      CommandRow("Remove override", "adb shell am memory-limiter manual $pid none"),
    )

  Scaffold { innerPadding ->
    Column(
      modifier =
        Modifier.statusBarsPadding()
          .verticalScroll(rememberScrollState())
          .padding(innerPadding)
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      HeaderBlock()

      if (!isSupported) {
        SupportNotice(sdkInt = Build.VERSION.SDK_INT)
      }

      RuntimeCard(
        packageName = packageName,
        pid = pid,
        allocatedMb = allocatedMb,
        isAllocating = isAllocating,
        statusLine = statusLine,
      )

      ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = sentryPurpleTint)) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Recommended flow",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          StepText("1. Open this screen on an Android 17+ device or emulator.")
          StepText("2. Copy the commands below and apply the manual limit to the current PID.")
          StepText("3. Tap Start allocating and optionally move the app to the background.")
          StepText(
            "4. Relaunch the app after the process dies; Sentry should recover the kill on startup."
          )
        }
      }

      OutlinedCard {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "ADB commands",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Tap any row to copy it. The manual commands are bound to this launch's PID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          commandRows.forEachIndexed { index, row ->
            CommandCard(label = row.label, command = row.command, onCopy = onCopy)
            if (index != commandRows.lastIndex) {
              Spacer(modifier = Modifier.height(4.dp))
            }
          }
        }
      }

      OutlinedCard {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(onClick = onStartAllocating, enabled = isSupported && !isAllocating) {
              Text("Start allocating")
            }
            OutlinedButton(onClick = onReleaseBuffers, enabled = allocatedMb > 0 || isAllocating) {
              Text("Release buffers")
            }
            OutlinedButton(onClick = onMoveToBackground, enabled = isSupported) {
              Text("Move to background")
            }
          }

          HorizontalDivider()

          Text(
            text =
              "This sample validates MemoryLimiter kill recovery from ApplicationExitInfo. It does not upload heap dumps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              "If the PID changes after a relaunch, rerun the manual commands before starting another pass.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun HeaderBlock() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      color = sentryPurpleTint,
      shape = RoundedCornerShape(20.dp),
      contentColor = sentryPurple,
    ) {
      Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Memory, contentDescription = null)
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = "MemoryLimiter",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "Android 17 manual sample for process kills recovered on the next app start.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SupportNotice(sdkInt: Int) {
  Card(colors = CardDefaults.cardColors(containerColor = cautionTint)) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = "Android 17 required",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text =
          "This device is on API $sdkInt. The sample still shows the exact adb flow, but the MemoryLimiter commands and recovered exit signal require Android 17 or newer.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun RuntimeCard(
  packageName: String,
  pid: Int,
  allocatedMb: Int,
  isAllocating: Boolean,
  statusLine: String,
) {
  ElevatedCard {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "Runtime",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      RuntimeRow(label = "Package", value = packageName)
      RuntimeRow(label = "PID", value = pid.toString())
      RuntimeRow(label = "SDK", value = Build.VERSION.SDK_INT.toString())
      RuntimeRow(label = "Allocator", value = if (isAllocating) "Running" else "Idle")
      RuntimeRow(label = "Retained", value = "$allocatedMb MiB")
      HorizontalDivider()
      Text(
        text = statusLine,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun RuntimeRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun StepText(text: String) {
  Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CommandCard(label: String, command: String, onCopy: (String, String) -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().clickable { onCopy(command, label) },
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = command,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

private data class CommandRow(val label: String, val command: String)
