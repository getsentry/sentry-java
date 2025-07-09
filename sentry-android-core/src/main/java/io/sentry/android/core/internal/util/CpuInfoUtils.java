package io.sentry.android.core.internal.util;

import io.sentry.ISentryLifecycleToken;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class CpuInfoUtils {

  private static final CpuInfoUtils instance = new CpuInfoUtils();
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public static CpuInfoUtils getInstance() {
    return instance;
  }

  private CpuInfoUtils() {}

  private static final @NotNull String SYSTEM_CPU_PATH = "/sys/devices/system/cpu";

  @VisibleForTesting
  static final @NotNull String CPUINFO_MAX_FREQ_PATH = "cpufreq/cpuinfo_max_freq";

  /** Cached max frequencies to avoid reading files multiple times */
  private final @NotNull List<Integer> cpuMaxFrequenciesMhz = new ArrayList<>();

  /**
   * Read the max frequency of each core of the cpu and returns it in Mhz
   *
   * @return A list with the frequency of each core of the cpu in Mhz
   */
  public @NotNull List<Integer> readMaxFrequencies() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (!cpuMaxFrequenciesMhz.isEmpty()) {
        return cpuMaxFrequenciesMhz;
      }
      File[] cpuDirs = new File(getSystemCpuPath()).listFiles();
      if (cpuDirs == null) {
        return new ArrayList<>();
      }

      for (File cpuDir : cpuDirs) {
        if (!cpuDir.getName().matches("cpu[0-9]+")) continue;
        File cpuMaxFreqFile = new File(cpuDir, CPUINFO_MAX_FREQ_PATH);

        long khz;
        try {
          String content = FileUtils.readText(cpuMaxFreqFile);
          if (content == null) continue;
          khz = Long.parseLong(content.trim());
        } catch (NumberFormatException e) {
          continue;
        } catch (IOException e) {
          continue;
        }
        cpuMaxFrequenciesMhz.add((int) (khz / 1000));
      }
      return cpuMaxFrequenciesMhz;
    }
  }

  @VisibleForTesting
  @NotNull
  String getSystemCpuPath() {
    return SYSTEM_CPU_PATH;
  }

  @TestOnly
  public void setCpuMaxFrequencies(List<Integer> frequencies) {
    cpuMaxFrequenciesMhz.clear();
    cpuMaxFrequenciesMhz.addAll(frequencies);
  }

  @TestOnly
  final void clear() {
    cpuMaxFrequenciesMhz.clear();
  }
}
