package io.sentry.samples.android.memory

import android.app.ApplicationExitInfo
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
import io.sentry.android.core.MemoryLimiterIntegration
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val chunkSizeMb = 8
private const val chunkDelayMs = 120L
private const val manualLimit = "256"
private const val mib = 1024 * 1024

private val sentryPurple = Color(0xFF7B52FB)
private val sentryPurpleTint = Color(0xFFF2ECFF)
private val cautionTint = Color(0xFFFFF1DB)
private val failureTint = Color(0xFFFFE1E3)

private enum class AllocationOutcome {
  JAVA_OOM
}

/**
 * Sample Activity for verifying the behavior of our [MemoryLimiterIntegration].
 *
 * The sample app can't synthesize an [ApplicationExitInfo] signature on its own, so this Activity
 * pairs a deterministic allocator with the adb commands Android exposes for manually lowering a
 * process' MemoryLimiter threshold.
 *
 * Public Android 17+ emulator images may still report `am memory-limiter status` as disabled even
 * when the shell command surface exists. In that case, this sample still demonstrates the intended
 * manual flow, but a real recovered MemoryLimiter event requires a platform image where the
 * MemoryLimiter service is actually active.
 */
class MemoryLimiterActivity : ComponentActivity() {

  private val retainedAllocations = Collections.synchronizedList(mutableListOf<ByteArray>())
  private var allocationJob: Job? = null

  private var allocatedMb by mutableIntStateOf(0)
  private var isAllocating by mutableStateOf(false)
  private var statusLine by
    mutableStateOf("Idle. Apply the manual limit, then start the memory pressure run.")
  private var allocationOutcome by mutableStateOf<AllocationOutcome?>(null)

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
          allocationOutcome = allocationOutcome,
          onCopy = ::copyCommand,
          onStartAllocating = ::startAllocating,
          onReleaseBuffers = ::releaseBuffers,
          onMoveToBackground = { moveTaskToBack(true) },
        )
      }
    }
  }

  override fun onDestroy() {
    resetAllocatorState()
    super.onDestroy()
  }

  private fun startAllocating() {
    if (!isSupported || allocationJob != null) {
      return
    }

    statusLine = "Applying memory pressure with ${chunkSizeMb} MiB chunks every ${chunkDelayMs} ms."
    isAllocating = true
    allocationOutcome = null

    allocationJob =
      lifecycleScope.launch(Dispatchers.Default) {
        try {
          while (isActive) {
            retainedAllocations.add(ByteArray(chunkSizeMb * mib))
            val total = retainedAllocations.size * chunkSizeMb
            withContext(Dispatchers.Main.immediate) {
              allocatedMb = total
              statusLine = "Holding about $total MiB of retained memory."
            }
            delay(chunkDelayMs.milliseconds)
          }
        } catch (_: OutOfMemoryError) {
          withContext(Dispatchers.Main.immediate) {
            resetAllocatorState(
              cancelJob = false,
              outcome = AllocationOutcome.JAVA_OOM,
              status = "Hit java.lang.OutOfMemoryError before the OS applied a MemoryLimiter kill.",
            )
          }
        }
      }
  }

  private fun releaseBuffers() {
    resetAllocatorState(
      status = "Released retained buffers and requested a fresh start for the next run."
    )
  }

  private fun resetAllocatorState(
    cancelJob: Boolean = true,
    outcome: AllocationOutcome? = null,
    status: String? = null,
  ) {
    if (cancelJob) {
      allocationJob?.cancel()
    }
    allocationJob = null
    isAllocating = false
    retainedAllocations.clear()
    allocatedMb = 0
    allocationOutcome = outcome
    if (status != null) {
      statusLine = status
    }
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
  allocationOutcome: AllocationOutcome?,
  onCopy: (String, String) -> Unit,
  onStartAllocating: () -> Unit,
  onReleaseBuffers: () -> Unit,
  onMoveToBackground: () -> Unit,
) {
  val commands =
    MemoryLimiterCommands(
      resolvePid = "adb shell pidof $packageName",
      inspectLimiter = "adb shell am memory-limiter status",
      applyLimit = "adb shell am memory-limiter manual $pid $manualLimit",
      removeOverride = "adb shell am memory-limiter manual $pid none",
    )
  val commandRows =
    listOf(
      CommandRow("Resolve PID", commands.resolvePid),
      CommandRow("Inspect limiter", commands.inspectLimiter),
      CommandRow("Apply limit", commands.applyLimit),
      CommandRow("Remove override", commands.removeOverride),
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
            text = "Manual test flow",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          StepText("1. Open this screen on an Android 17+ device or emulator.")
          StepText("2. Run the Apply limit command for this launch's PID.")
          StepText("3. Tap Start memory pressure and optionally send the app to the background.")
          StepText(
            "4. Reopen the app after the process dies; Sentry should recover the kill on startup."
          )
        }
      }

      OutlinedCard {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "ADB setup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              "Tap a row to copy it. Re-run the PID-bound commands after each relaunch because the PID changes.",
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
            text = "Run sample",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (allocationOutcome == AllocationOutcome.JAVA_OOM) {
            OutcomeNotice(inspectLimiterCommand = commands.inspectLimiter)
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(onClick = onStartAllocating, enabled = isSupported && !isAllocating) {
              Text("Start memory pressure")
            }
            OutlinedButton(onClick = onReleaseBuffers, enabled = allocatedMb > 0 || isAllocating) {
              Text("Reset run")
            }
            OutlinedButton(onClick = onMoveToBackground, enabled = isSupported) {
              Text("Move app to background")
            }
          }

          HorizontalDivider()

          Text(
            text =
              "This sample validates startup recovery for MemoryLimiter exits from ApplicationExitInfo. It does not upload heap dumps.",
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
private fun OutcomeNotice(inspectLimiterCommand: String) {
  Card(colors = CardDefaults.cardColors(containerColor = failureTint)) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "This was not a MemoryLimiter kill!",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text =
          "The process threw java.lang.OutOfMemoryError and recovered, which means Android did not terminate it through MemoryLimiter.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text =
          "Most commonly on emulator images, MemoryLimiter is not enabled on the device even though the shell command exists.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text =
          "Run `$inspectLimiterCommand`. For this sample to work end to end, it must show active monitoring instead of `disabled`, and you must apply the manual limit before starting memory pressure.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          "If it stays disabled, switch to a platform image where the MemoryLimiter service is active, then retry this screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
        text =
          "Flow for testing a MemoryLimiter kill and the generation of a SentryEvent on next app start.",
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
        text = "Android 17+ required",
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

private data class MemoryLimiterCommands(
  val resolvePid: String,
  val inspectLimiter: String,
  val applyLimit: String,
  val removeOverride: String,
)

private data class CommandRow(val label: String, val command: String)
