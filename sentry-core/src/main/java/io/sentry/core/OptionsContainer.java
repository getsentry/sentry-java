package io.sentry.core;

import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class OptionsContainer<T> {

  public static <T> OptionsContainer<T> create(final Class<T> clazz) {
    return new OptionsContainer<>(clazz);
  }

  private final Class<T> clazz;

  private OptionsContainer(final Class<T> clazz) {
    super();
    this.clazz = clazz;
  }

  public T createInstance()
      throws InstantiationException, IllegalAccessException, NoSuchMethodException,
          InvocationTargetException {
    return clazz.getDeclaredConstructor().newInstance();
  }
}
