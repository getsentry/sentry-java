package io.sentry.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JsonSerializationUtils {

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
}
