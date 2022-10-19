package io.sentry;

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

  public static @NotNull Map<String, Object> calendarToMap(@NotNull Calendar calendar) {
    Map<String, Object> map = new HashMap<>();

    map.put("year", (long) calendar.get(Calendar.YEAR));
    map.put("month", (long) calendar.get(Calendar.MONTH));
    map.put("dayOfMonth", (long) calendar.get(Calendar.DAY_OF_MONTH));
    map.put("hourOfDay", (long) calendar.get(Calendar.HOUR_OF_DAY));
    map.put("minute", (long) calendar.get(Calendar.MINUTE));
    map.put("second", (long) calendar.get(Calendar.SECOND));

    return map;
  }

  public static @NotNull List<Object> atomicIntegerArrayToList(@NotNull AtomicIntegerArray array) {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      list.add(array.get(i));
    }
    return list;
  }
}
