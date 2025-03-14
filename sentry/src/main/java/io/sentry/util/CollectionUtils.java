package io.sentry.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
  public static int size(final @NotNull Iterable<?> data) {
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
   * Creates a new {@link ConcurrentHashMap} as a shallow copy of map given by parameter. Also makes
   * sure no null keys or values are put into the resulting {@link ConcurrentHashMap}.
   *
   * @param map the map to copy
   * @param <K> the type of map keys
   * @param <V> the type of map values
   * @return the shallow copy of map
   */
  public static <K, V> @Nullable Map<K, @NotNull V> newConcurrentHashMap(
      @Nullable Map<K, @NotNull V> map) {
    if (map != null) {
      Map<K, @NotNull V> concurrentMap = new ConcurrentHashMap<>();

      for (Map.Entry<K, V> entry : map.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          concurrentMap.put(entry.getKey(), entry.getValue());
        }
      }
      return concurrentMap;
    } else {
      return null;
    }
  }

  /**
   * Creates a new {@link HashMap} as a shallow copy of map given by parameter.
   *
   * @param map the map to copy
   * @param <K> the type of map keys
   * @param <V> the type of map values
   * @return a new {@link HashMap} or {@code null} if parameter is {@code null}
   */
  public static <K, V> @Nullable Map<K, @NotNull V> newHashMap(@Nullable Map<K, @NotNull V> map) {
    if (map != null) {
      return new HashMap<>(map);
    } else {
      return null;
    }
  }

  /**
   * Creates a new {@link ArrayList} as a shallow copy of list given by parameter.
   *
   * @param list the list to copy
   * @param <T> the type of list entries
   * @return a new {@link ArrayList} or {@code null} if parameter is {@code null}
   */
  public static <T> @Nullable List<T> newArrayList(@Nullable List<T> list) {
    if (list != null) {
      return new ArrayList<>(list);
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
   * Returns a new list with the results of the function applied to all elements of the original
   * list.
   *
   * @param list - the list to apply the function to
   * @param f - the function
   * @param <T> - original list element type
   * @param <R> - returned list element type
   * @return a new list
   */
  public static @NotNull <T, R> List<R> map(
      final @NotNull List<T> list, final @NotNull Mapper<T, R> f) {
    List<R> mappedList = new ArrayList<>(list.size());
    for (T t : list) {
      mappedList.add(f.map(t));
    }
    return mappedList;
  }

  /**
   * Returns a new list which entries match a predicate specified by a parameter.
   *
   * @param predicate - the predicate
   * @return a new list
   */
  public static @NotNull <T> List<T> filterListEntries(
      final @NotNull List<T> list, final @NotNull Predicate<T> predicate) {
    final List<T> filteredList = new ArrayList<>(list.size());
    for (final T entry : list) {
      if (predicate.test(entry)) {
        filteredList.add(entry);
      }
    }
    return filteredList;
  }

  /**
   * Returns true if the element is present in the array, false otherwise.
   *
   * @param array - the array
   * @param element - the element
   * @return true if the element is present in the array, false otherwise.
   */
  public static <T> boolean contains(final @NotNull T[] array, final @NotNull T element) {
    for (final T t : array) {
      if (element.equals(t)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A simplified copy of Java 8 Predicate.
   *
   * @param <T> the type
   */
  public interface Predicate<T> {
    boolean test(T t);
  }

  /**
   * A simple function to map an object into another.
   *
   * @param <T> the original type
   * @param <R> the returned type
   */
  public interface Mapper<T, R> {
    R map(T t);
  }

  /**
   * Returns a reverse iterator, where the first (resp. last) valid call to `prev` returns the last
   * (resp. first) element that would be returned when iterating forwards. Note that this differs
   * from the behavior of e.g. `org.apache.commons.collections4.iterators.ReverseListIterator`,
   * where you need to iterate using `next` instead. We use the concrete type `CopyOnWriteArrayList`
   * here as we are relying on the fact that its copy constructor only copies the reference to an
   * internal array. We don't want to use this for other `List` implementations, as it could lead to
   * an unnecessary copy of the elements instead.
   *
   * @param list the `CopyOnWriteArrayList` to get the reverse iterator for
   * @param <T> the type
   * @return a reverse iterator over `list`
   */
  public static @NotNull <T> ListIterator<T> reverseListIterator(
      final @NotNull CopyOnWriteArrayList<T> list) {
    final @NotNull CopyOnWriteArrayList<T> copy = new CopyOnWriteArrayList<>(list);
    return copy.listIterator(copy.size());
  }
}
