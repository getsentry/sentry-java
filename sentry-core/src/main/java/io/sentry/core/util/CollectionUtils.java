package io.sentry.core.util;

import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;

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
}
