package io.sentry.core.util;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Util class for Collections */
@ApiStatus.Internal
public final class CollectionUtils {

  private CollectionUtils() {}

  /**
   * Returns an Iterator size
   *
   * @param data the Iterable
   * @return iterator size
   */
  public static int size(Iterable<?> data) {
    if (data instanceof Collection) {
      return ((Collection<?>) data).size();
    }
    int counter = 0;
    for (Object ignored : data) {
      counter++;
    }
    return counter;
  }

  /**
   * Creates a shallow copy of map given by parameter.
   *
   * @param map the map to copy
   * @param <K> the type of map keys
   * @param <V> the type of map values
   * @return the shallow copy of map
   */
  public static <K, V> @Nullable Map<K, V> shallowCopy(@Nullable Map<K, V> map) {
    if (map != null) {
      return new ConcurrentHashMap<>(map);
    } else {
      return null;
    }
  }
}
