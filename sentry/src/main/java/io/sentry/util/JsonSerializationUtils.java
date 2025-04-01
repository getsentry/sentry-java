package io.sentry.util;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.ILogger;
import io.sentry.ISerializer;
import io.sentry.JsonSerializable;
import io.sentry.SentryLevel;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonSerializationUtils {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static @NotNull Map<String, Object> calendarToMap(final @NotNull Calendar calendar) {
    final @NotNull Map<String, Object> map = new HashMap<>();

    map.put("year", calendar.get(Calendar.YEAR));
    map.put("month", calendar.get(Calendar.MONTH));
    map.put("dayOfMonth", calendar.get(Calendar.DAY_OF_MONTH));
    map.put("hourOfDay", calendar.get(Calendar.HOUR_OF_DAY));
    map.put("minute", calendar.get(Calendar.MINUTE));
    map.put("second", calendar.get(Calendar.SECOND));

    return map;
  }

  public static @NotNull List<Object> atomicIntegerArrayToList(
      final @NotNull AtomicIntegerArray array) {
    final int numberOfItems = array.length();
    final @NotNull List<Object> list = new ArrayList<>(numberOfItems);
    for (int i = 0; i < numberOfItems; i++) {
      list.add(array.get(i));
    }
    return list;
  }

  public static @Nullable byte[] bytesFrom(
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final @NotNull JsonSerializable serializable) {
    try {
      try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
          final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {

        serializer.serialize(serializable, writer);

        return stream.toByteArray();
      }
    } catch (Throwable t) {
      if (logger.isEnabled(ERROR)) {
        logger.log(SentryLevel.ERROR, "Could not serialize serializable", t);
      }
      return null;
    }
  }
}
