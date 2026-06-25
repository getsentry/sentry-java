package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpAppStartExtender implements IAppStartExtender {

  private static final @NotNull NoOpAppStartExtender instance = new NoOpAppStartExtender();

  private NoOpAppStartExtender() {}

  public static @NotNull NoOpAppStartExtender getInstance() {
    return instance;
  }

  @Override
  public void extendAppStart() {}

  @Override
  public void finishExtendedAppStart() {}

  @Override
  public @NotNull ISpan getExtendedAppStartSpan() {
    return NoOpSpan.getInstance();
  }
}
