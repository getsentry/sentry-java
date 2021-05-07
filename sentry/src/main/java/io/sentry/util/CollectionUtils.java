package io.sentry.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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
  public static <K, V> @Nullable Map<K, @NotNull V> shallowCopy(@Nullable Map<K, @NotNull V> map) {
    if (map != null) {
      return new ConcurrentHashMap<>(map);
    } else {
      return null;
    }
  }

  /**
   * Returns a new map which entries match a predicate specified by a parameter.
   *
   * @param map - the map to filter
   * @param predicate - the predicate
   * @param <K> - map entry key type
   * @param <V> - map entry value type
   * @return a new map
   */
  public static @NotNull <K, V> Map<K, V> filterMapEntries(
      final @NotNull Map<K, V> map, final @NotNull Predicate<Map.Entry<K, V>> predicate) {
    final Map<K, V> filteredMap = new HashMap<>();
    for (final Map.Entry<K, V> entry : map.entrySet()) {
      if (predicate.test(entry)) {
        filteredMap.put(entry.getKey(), entry.getValue());
      }
    }
    return filteredMap;
  }

  /**
   * A simplified copy of Java 8 Predicate.
   *
   * @param <T> the type
   */
  public interface Predicate<T> {
    boolean test(T t);
  }
}
