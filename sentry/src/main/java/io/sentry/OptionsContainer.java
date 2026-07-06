package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.util.Objects;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@Open
public class OptionsContainer<T> {

  public @NotNull static <T> OptionsContainer<T> create(final @NotNull Class<T> clazz) {
    return new OptionsContainer<>(clazz);
  }

  private final @Nullable Class<T> clazz;

  private OptionsContainer(final @NotNull Class<T> clazz) {
    super();
    this.clazz = clazz;
  }

  /** Constructor for subclasses that create the instance directly without reflection. */
  protected OptionsContainer() {
    super();
    this.clazz = null;
  }

  public @NotNull T createInstance()
      throws InstantiationException,
          IllegalAccessException,
          NoSuchMethodException,
          InvocationTargetException {
    return Objects.requireNonNull(clazz, "OptionsContainer clazz is required")
        .getDeclaredConstructor()
        .newInstance();
  }
}
