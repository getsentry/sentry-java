package io.sentry.util;

import io.sentry.MonitorConfig;
import io.sentry.MonitorSchedule;
import io.sentry.MonitorScheduleUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Converts scheduler definitions into a {@link MonitorConfig}. */
@ApiStatus.Internal
public final class MonitorConfigUtils {
  private static final @NotNull String CRON_DISABLED = "-";
  private static final long MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1);
  private static final @NotNull Map<String, String> CRON_MACROS;
  private static final @NotNull Pattern SIMPLE_DURATION =
      Pattern.compile("^([+-]?\\d+)([a-zA-Z]{0,2})$");
  private static final @NotNull Pattern FIXED_OFFSET =
      Pattern.compile("^(?:GMT|UTC)?([+-])(\\d{1,2})(?::?(\\d{2}))?$");
  private static final @NotNull Pattern DIGITS = Pattern.compile("\\d+");

  private static final int DAY_OF_MONTH = 2;
  private static final int MONTH = 3;
  private static final int DAY_OF_WEEK = 4;
  private static final int[] FIELD_MIN = {0, 0, 1, 1, 0};
  private static final int[] FIELD_MAX = {59, 23, 31, 12, 7};
  private static final int LAST = -1;
  private static final int[] DAYS_IN_MONTH = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  private static final @NotNull List<String> MONTH_NAMES =
      Arrays.asList(
          "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");
  private static final @NotNull List<String> DAY_NAMES =
      Arrays.asList("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");

  /** IANA zones without a slash; Java also knows non-IANA short IDs like PST. */
  private static final @NotNull Set<String> TOP_LEVEL_ZONES =
      new HashSet<>(
          Arrays.asList(
              ("CET CST6CDT Cuba EET EST EST5EDT Egypt Eire GB GB-Eire GMT GMT+0 GMT-0 GMT0 "
                      + "Greenwich HST Hongkong Iceland Iran Israel Jamaica Japan Kwajalein Libya "
                      + "MET MST MST7MDT NZ NZ-CHAT Navajo PRC PST8PDT Poland Portugal ROC ROK "
                      + "Singapore Turkey UCT UTC Universal W-SU WET Zulu")
                  .split(" ")));

  private static volatile @Nullable Set<String> availableZoneIds;

  static {
    final @NotNull Map<String, String> macros = new HashMap<>();
    macros.put("@yearly", "0 0 1 1 *");
    macros.put("@annually", "0 0 1 1 *");
    macros.put("@monthly", "0 0 1 * *");
    macros.put("@weekly", "0 0 * * 0");
    macros.put("@daily", "0 0 * * *");
    macros.put("@midnight", "0 0 * * *");
    macros.put("@hourly", "0 * * * *");
    CRON_MACROS = Collections.unmodifiableMap(macros);
  }

  private MonitorConfigUtils() {}

  /**
   * Builds a monitor config from exactly one of a cron or a fixed interval.
   *
   * @param cron 6 field cron (seconds first) or macro, null or empty if unset
   * @param zone cron time zone, ignored for intervals
   * @param intervalMillis fixed rate, null if unset
   * @return null if the schedule can't be expressed as a Sentry monitor schedule
   */
  public static @Nullable MonitorConfig fromSchedule(
      final @Nullable String cron,
      final @Nullable String zone,
      final @Nullable Long intervalMillis) {
    final boolean hasCron = cron != null && !cron.isEmpty();

    if (hasCron && cron != null && intervalMillis == null) {
      final @Nullable String crontab = toCrontab(cron);
      if (crontab == null) {
        return null;
      }
      final @NotNull MonitorConfig config = new MonitorConfig(MonitorSchedule.crontab(crontab));
      if (zone != null && !zone.isEmpty()) {
        final @Nullable String timezone = toTimezone(zone);
        if (timezone == null) {
          return null;
        }
        config.setTimezone(timezone);
      }
      return config;
    }

    if (!hasCron && intervalMillis != null) {
      final long millis = intervalMillis;
      if (millis < MINUTE_MILLIS || millis % MINUTE_MILLIS != 0) {
        return null;
      }
      long value = millis / MINUTE_MILLIS;
      @NotNull MonitorScheduleUnit unit = MonitorScheduleUnit.MINUTE;
      if (value % (24 * 60) == 0) {
        value /= 24 * 60;
        unit = MonitorScheduleUnit.DAY;
      } else if (value % 60 == 0) {
        value /= 60;
        unit = MonitorScheduleUnit.HOUR;
      }
      if (value > Integer.MAX_VALUE) {
        return null;
      }
      return new MonitorConfig(MonitorSchedule.interval((int) value, unit));
    }
    return null;
  }

  /**
   * Builds a monitor config from Spring {@code @Scheduled} values with placeholders resolved. Reads
   * the cron and a cron without zone the way Spring does.
   *
   * @param cron Spring cron, null or empty if unset
   * @param zone cron time zone, null or empty for the JVM default zone
   * @param fixedRateMillis null if unset
   * @param fixedDelayMillis null if unset; a fixed delay never gets a config
   * @param legacyCronParser true for Spring before 5.3, which parses crons with {@code
   *     CronSequenceGenerator}
   * @return null if the schedule can't be expressed as a Sentry monitor schedule
   */
  public static @Nullable MonitorConfig fromSpringScheduled(
      final @Nullable String cron,
      final @Nullable String zone,
      final @Nullable Long fixedRateMillis,
      final @Nullable Long fixedDelayMillis,
      final boolean legacyCronParser) {
    // runs start a delay after the previous run ends, so they drift from any interval
    if (fixedDelayMillis != null) {
      return null;
    }
    final @Nullable String crontabCron =
        legacyCronParser ? toCrontabLegacy(cron) : toCrontabDaysOfWeek(cron);
    @Nullable String cronZone = null;
    if (crontabCron != null && !crontabCron.isEmpty()) {
      cronZone = zone == null || zone.isEmpty() ? TimeZone.getDefault().getID() : zone;
    }
    return fromSchedule(crontabCron, cronZone, fixedRateMillis);
  }

  /**
   * Spring before 5.3 steps day of month {@code *}/n from 0, so it runs on n, 2n, ... Days of week
   * already match crontab. Null if both day fields are set, as it then sometimes runs at midnight.
   */
  static @Nullable String toCrontabLegacy(final @Nullable String cron) {
    if (cron == null) {
      return null;
    }
    final @NotNull String[] fields = cron.trim().split("\\s+", -1);
    if (fields.length != 6) {
      return cron;
    }
    if (!isAnyDay(fields[3]) && !isAnyDay(fields[5])) {
      return null;
    }
    final @NotNull StringBuilder daysOfMonth = new StringBuilder();
    for (@NotNull String item : fields[3].split(",", -1)) {
      if (item.startsWith("*/")) {
        item = item.substring(2) + "-31" + item.substring(1);
      }
      if (daysOfMonth.length() > 0) {
        daysOfMonth.append(',');
      }
      daysOfMonth.append(item);
    }
    fields[3] = daysOfMonth.toString();
    return join(fields);
  }

  private static boolean isAnyDay(final @NotNull String field) {
    return "*".equals(field) || "?".equals(field);
  }

  /**
   * Spring numbers days of week from Monday with 0 or 7 for Sunday, and starts {@code *} on Monday.
   * Rewrites them so crontab reads them the same way. Null for {@code #5}, which Spring 5.3 also
   * runs in months without a fifth weekday.
   */
  static @Nullable String toCrontabDaysOfWeek(final @Nullable String cron) {
    if (cron == null) {
      return null;
    }
    final @NotNull String[] fields = cron.trim().split("\\s+", -1);
    if (fields.length != 6) {
      return cron;
    }
    final @NotNull StringBuilder daysOfWeek = new StringBuilder();
    for (@NotNull String item : fields[5].toUpperCase(Locale.ROOT).split(",", -1)) {
      for (int i = 0; i < DAY_NAMES.size(); i++) {
        item = item.replace(DAY_NAMES.get(i), String.valueOf(i == 0 ? 7 : i));
      }
      if (item.matches(".*#0*5")) {
        return null;
      }
      if (item.startsWith("*/")) {
        item = "1-7" + item.substring(1);
      } else if (item.startsWith("7-")) {
        // Sunday starting a range is 0 in Spring
        item = "0" + item.substring(1);
      }
      if (daysOfWeek.length() > 0) {
        daysOfWeek.append(',');
      }
      daysOfWeek.append(item);
    }
    fields[5] = daysOfWeek.toString();
    return join(fields);
  }

  private static @NotNull String join(final @NotNull String[] fields) {
    final @NotNull StringBuilder result = new StringBuilder();
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        result.append(' ');
      }
      result.append(fields[i]);
    }
    return result.toString();
  }

  /**
   * Parses a plain number in {@code defaultUnit} or a Spring 6.1 simple duration like {@code 30s}.
   * ISO-8601 is not supported.
   *
   * @return milliseconds, or null if unparseable
   */
  public static @Nullable Long parsePeriodMillis(
      final @Nullable String value, final @NotNull TimeUnit defaultUnit) {
    if (value == null) {
      return null;
    }
    final @NotNull Matcher matcher = SIMPLE_DURATION.matcher(value.trim());
    if (!matcher.matches()) {
      return null;
    }
    final long amount;
    try {
      amount = Long.parseLong(matcher.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
    final @Nullable TimeUnit unit = timeUnit(matcher.group(2), defaultUnit);
    return unit == null ? null : unit.toMillis(amount);
  }

  private static @Nullable TimeUnit timeUnit(
      final @NotNull String suffix, final @NotNull TimeUnit defaultUnit) {
    switch (suffix.toLowerCase(Locale.ROOT)) {
      case "":
        return defaultUnit;
      case "ns":
        return TimeUnit.NANOSECONDS;
      case "us":
        return TimeUnit.MICROSECONDS;
      case "ms":
        return TimeUnit.MILLISECONDS;
      case "s":
        return TimeUnit.SECONDS;
      case "m":
        return TimeUnit.MINUTES;
      case "h":
        return TimeUnit.HOURS;
      case "d":
        return TimeUnit.DAYS;
      default:
        return null;
    }
  }

  /**
   * Returns a zone ID Sentry accepts: IANA region IDs as is, whole-hour fixed offsets like {@code
   * GMT+2} as {@code Etc/GMT-2}, null otherwise.
   */
  static @Nullable String toTimezone(final @NotNull String zone) {
    final @NotNull Matcher offset = FIXED_OFFSET.matcher(zone);
    if (offset.matches()) {
      final @Nullable String minutes = offset.group(3);
      if (minutes != null && !"00".equals(minutes)) {
        return null;
      }
      final int hours = Integer.parseInt(offset.group(2));
      if (hours == 0) {
        return "Etc/GMT";
      }
      final boolean east = "+".equals(offset.group(1));
      if (hours > (east ? 14 : 12)) {
        return null;
      }
      // Etc/GMT signs are inverted
      return "Etc/GMT" + (east ? "-" : "+") + hours;
    }
    if (TOP_LEVEL_ZONES.contains(zone)) {
      return zone;
    }
    if (zone.indexOf('/') > 0 && !zone.startsWith("SystemV/") && availableZones().contains(zone)) {
      return zone;
    }
    return null;
  }

  private static @NotNull Set<String> availableZones() {
    @Nullable Set<String> zones = availableZoneIds;
    if (zones == null) {
      zones = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
      availableZoneIds = zones;
    }
    return zones;
  }

  /**
   * Drops a fixed seconds field. Returns null if Sentry would reject the result or read it
   * differently, as crontab has no seconds and ORs day of month and day of week.
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
    if (fields.length != 6 || !fields[0].matches("\\d{1,2}") || Integer.parseInt(fields[0]) > 59) {
      return null;
    }
    final @NotNull String[] crontab = new String[5];
    for (int i = 0; i < crontab.length; i++) {
      final @NotNull String field = fields[i + 1];
      // '?' means '*' in Spring and Quartz cron
      final boolean dayField = i == DAY_OF_MONTH || i == DAY_OF_WEEK;
      crontab[i] = dayField && "?".equals(field) ? "*" : field;
    }
    // crontab ORs day of month and day of week unless one starts with '*', Spring and Quartz AND
    if (!crontab[DAY_OF_MONTH].startsWith("*") && !crontab[DAY_OF_WEEK].startsWith("*")) {
      return null;
    }
    final @NotNull List<Set<Integer>> values = new ArrayList<>();
    for (int i = 0; i < crontab.length; i++) {
      final @Nullable Set<Integer> parsed = parseField(crontab[i].toUpperCase(Locale.ROOT), i);
      if (parsed == null) {
        return null;
      }
      values.add(parsed);
    }
    if (!dayExistsInMonths(values.get(DAY_OF_MONTH), values.get(MONTH))) {
      return null;
    }
    final @NotNull StringBuilder result = new StringBuilder();
    for (int i = 0; i < crontab.length; i++) {
      if (i > 0) {
        result.append(' ');
      }
      result.append(crontab[i]);
    }
    return result.toString();
  }

  // Mirrors the parser Sentry uses (cronsim). Returns null for anything it rejects or that we
  // can't send as is. Day of week L and # entries are not expanded, as only the count matters.
  private static @Nullable Set<Integer> parseField(final @NotNull String field, final int index) {
    final @NotNull Set<Integer> result = new HashSet<>();
    for (final @NotNull String term : field.split(",", -1)) {
      final @Nullable Set<Integer> parsed = parseTerm(term, index);
      if (parsed == null) {
        return null;
      }
      result.addAll(parsed);
    }
    return result;
  }

  private static @Nullable Set<Integer> parseTerm(final @NotNull String term, final int index) {
    if ("*".equals(term)) {
      return range(FIELD_MIN[index], FIELD_MAX[index], 1);
    }
    if (index == DAY_OF_WEEK && term.indexOf('L') >= 0) {
      final @NotNull String day = term.substring(0, term.length() - 1);
      if (!term.endsWith("L") || !DIGITS.matcher(day).matches() || value(day, index) == null) {
        return null;
      }
      return Collections.singleton(LAST);
    }
    if (index == DAY_OF_WEEK && term.indexOf('#') >= 0) {
      final int hash = term.indexOf('#');
      final @Nullable Integer nth = number(term.substring(hash + 1));
      if (nth == null || nth < 1 || nth > 5 || value(term.substring(0, hash), index) == null) {
        return null;
      }
      return Collections.singleton(LAST);
    }
    if (term.indexOf('/') >= 0) {
      final int slash = term.indexOf('/');
      final @Nullable Integer step = number(term.substring(slash + 1));
      final @NotNull String base = term.substring(0, slash);
      if (step == null || step == 0 || "L".equals(base) || "LW".equals(base)) {
        return null;
      }
      final @Nullable Set<Integer> items = parseTerm(base, index);
      if (items == null) {
        return null;
      }
      if (items.size() == 1) {
        // cronsim steps a single value range like 10-10/2 to the field max
        if (base.indexOf('-') >= 0) {
          return null;
        }
        return range(items.iterator().next(), FIELD_MAX[index], step);
      }
      final @NotNull List<Integer> sorted = new ArrayList<>(items);
      Collections.sort(sorted);
      final @NotNull Set<Integer> result = new HashSet<>();
      for (int i = 0; i < sorted.size(); i += step) {
        result.add(sorted.get(i));
      }
      return result;
    }
    if (term.indexOf('-') >= 0) {
      final int dash = term.indexOf('-');
      final @Nullable Integer start = value(term.substring(0, dash), index);
      final @Nullable Integer end = value(term.substring(dash + 1), index);
      if (start == null || end == null || end < start) {
        return null;
      }
      return range(start, end, 1);
    }
    if (index == DAY_OF_MONTH && ("L".equals(term) || "LW".equals(term))) {
      return Collections.singleton(LAST);
    }
    final @Nullable Integer value = value(term, index);
    return value == null ? null : Collections.singleton(value);
  }

  private static @Nullable Integer value(final @NotNull String value, final int index) {
    if (index == MONTH && MONTH_NAMES.contains(value)) {
      return MONTH_NAMES.indexOf(value) + 1;
    }
    if (index == DAY_OF_WEEK && DAY_NAMES.contains(value)) {
      return DAY_NAMES.indexOf(value);
    }
    final @Nullable Integer number = number(value);
    if (number == null || number < FIELD_MIN[index] || number > FIELD_MAX[index]) {
      return null;
    }
    return number;
  }

  private static @Nullable Integer number(final @NotNull String value) {
    if (value.isEmpty() || value.length() > 9 || !DIGITS.matcher(value).matches()) {
      return null;
    }
    return Integer.parseInt(value);
  }

  private static @NotNull Set<Integer> range(final int start, final int end, final int step) {
    final @NotNull Set<Integer> result = new HashSet<>();
    for (int i = start; i <= end; i += step) {
      result.add(i);
    }
    return result;
  }

  private static boolean dayExistsInMonths(
      final @NotNull Set<Integer> days, final @NotNull Set<Integer> months) {
    final int firstDay = Collections.min(days);
    if (firstDay <= 29) {
      return true;
    }
    for (final int month : months) {
      if (firstDay <= DAYS_IN_MONTH[month - 1]) {
        return true;
      }
    }
    return false;
  }
}
