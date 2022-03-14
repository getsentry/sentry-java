package io.sentry.android.core.internal.util;

import io.sentry.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CpuInfoUtils {

  private static final @NotNull String SYSTEM_CPU_PATH = "/sys/devices/system/cpu/";
  private static final @NotNull String CPUINFO_MAX_FREQ_PATH = "cpufreq/cpuinfo_max_freq";

  /** Cached max frequencies to avoid reading files multiple times */
  private static @NotNull List<String> cpuMaxFrequenciesMhz = new ArrayList<>();

  /**
   * Read the max frequency of each core of the cpu and returns it in Mhz
   *
   * @return A list with the frequency of each core of the cpu in Mhz
   */
  public static @NotNull List<String> readMaxFrequencies() {
    if (!cpuMaxFrequenciesMhz.isEmpty()) {
      return cpuMaxFrequenciesMhz;
    }
    File[] cpuDirs = new File(SYSTEM_CPU_PATH).listFiles();
    if (cpuDirs == null) {
      return new ArrayList<>();
    }

    for (File cpuDir : cpuDirs) {
      if (!cpuDir.getName().matches("cpu[0-9]+")) continue;
      File cpuMaxFreqFile = new File(cpuDir, CPUINFO_MAX_FREQ_PATH);

      if (!cpuMaxFreqFile.exists() || !cpuMaxFreqFile.canRead()) continue;

      long khz = 0;
      try {
        String content = FileUtils.readText(cpuMaxFreqFile);
        if (content == null) continue;
        khz = Long.parseLong(content.trim());
      } catch (NumberFormatException e) {
        continue;
      } catch (IOException e) {
        continue;
      }
      cpuMaxFrequenciesMhz.add(Long.toString(khz / 1000));
    }
    return cpuMaxFrequenciesMhz;
  }
}
