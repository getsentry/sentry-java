package io.sentry;

import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OptionsContainer<T> {

  public @NotNull static <T> OptionsContainer<T> create(final @NotNull Class<T> clazz) {
    return new OptionsContainer<>(clazz);
  }

  private final @NotNull Class<T> clazz;

  private OptionsContainer(final @NotNull Class<T> clazz) {
    super();
    this.clazz = clazz;
  }

  public @NotNull T createInstance()
      throws InstantiationException,
          IllegalAccessException,
          NoSuchMethodException,
          InvocationTargetException {
    return clazz.getDeclaredConstructor().newInstance();
  }
}
