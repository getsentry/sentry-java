package io.sentry.util;

import io.sentry.MonitorConfig;
import io.sentry.MonitorSchedule;
import io.sentry.MonitorScheduleUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Converts scheduler definitions into a {@link MonitorConfig}. */
@ApiStatus.Internal
public final class MonitorConfigUtils {
  private static final @NotNull String CRON_DISABLED = "-";
  private static final long MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1);
  private static final @NotNull Map<String, String> CRON_MACROS = new HashMap<>();

  static {
    CRON_MACROS.put("@yearly", "0 0 1 1 *");
    CRON_MACROS.put("@annually", "0 0 1 1 *");
    CRON_MACROS.put("@monthly", "0 0 1 * *");
    CRON_MACROS.put("@weekly", "0 0 * * 0");
    CRON_MACROS.put("@daily", "0 0 * * *");
    CRON_MACROS.put("@midnight", "0 0 * * *");
    CRON_MACROS.put("@hourly", "0 * * * *");
  }

  private MonitorConfigUtils() {}

  /**
   * Builds a monitor config from exactly one of a cron expression, a fixed rate or a fixed delay.
   *
   * @param cron a 6 field cron expression with seconds first (Spring style), a macro such as
   *     {@code @daily}, {@code "-"} (disabled), or null/empty when not set
   * @param zone time zone of the cron expression, ignored for fixed rates and delays
   * @param fixedRateMillis fixed rate in milliseconds, or null when not set
   * @param fixedDelayMillis fixed delay in milliseconds, or null when not set
   * @return the monitor config, or null if the schedule cannot be expressed as a Sentry monitor
   *     schedule (sub-minute schedules, more than one schedule kind, unsupported cron)
   */
  public static @Nullable MonitorConfig fromSchedule(
      final @Nullable String cron,
      final @Nullable String zone,
      final @Nullable Long fixedRateMillis,
      final @Nullable Long fixedDelayMillis) {
    final boolean hasCron = cron != null && !cron.isEmpty();

    if (hasCron && cron != null && fixedRateMillis == null && fixedDelayMillis == null) {
      final @Nullable String crontab = toCrontab(cron);
      if (crontab == null) {
        return null;
      }
      final @NotNull MonitorConfig config = new MonitorConfig(MonitorSchedule.crontab(crontab));
      if (zone != null && !zone.isEmpty()) {
        config.setTimezone(zone);
      }
      return config;
    }

    final @Nullable Long period = fixedRateMillis != null ? fixedRateMillis : fixedDelayMillis;
    if (!hasCron && period != null && (fixedRateMillis == null || fixedDelayMillis == null)) {
      final long millis = period;
      if (millis < MINUTE_MILLIS || millis % MINUTE_MILLIS != 0) {
        return null;
      }
      final long minutes = millis / MINUTE_MILLIS;
      if (minutes > Integer.MAX_VALUE) {
        return null;
      }
      return new MonitorConfig(MonitorSchedule.interval((int) minutes, MonitorScheduleUnit.MINUTE));
    }
    return null;
  }

  /**
   * Converts a 6 field cron expression (seconds first) to a 5 field crontab. Returns null unless
   * the seconds field is a single fixed number, since crontab cannot express sub-minute schedules.
   */
  static @Nullable String toCrontab(final @NotNull String cron) {
    final @NotNull String trimmed = cron.trim();
    if (CRON_DISABLED.equals(trimmed)) {
      return null;
    }
    if (trimmed.startsWith("@")) {
      return CRON_MACROS.get(trimmed.toLowerCase(Locale.ROOT));
    }
    final @NotNull String[] fields = trimmed.split("\\s+", -1);
    if (fields.length != 6 || !fields[0].matches("\\d{1,2}")) {
      return null;
    }
    final @NotNull StringBuilder crontab = new StringBuilder();
    for (int i = 1; i < fields.length; i++) {
      if (i > 1) {
        crontab.append(' ');
      }
      // '?' means the same as '*' in Spring and Quartz cron expressions
      crontab.append("?".equals(fields[i]) ? "*" : fields[i]);
    }
    return crontab.toString();
  }
}
