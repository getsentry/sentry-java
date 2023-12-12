package io.sentry.internal.debugmeta;

import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpDebugMetaLoader implements IDebugMetaLoader {

  private static final NoOpDebugMetaLoader instance = new NoOpDebugMetaLoader();

  public static NoOpDebugMetaLoader getInstance() {
    return instance;
  }

  private NoOpDebugMetaLoader() {}

  @Override
  public @Nullable List<Properties> loadDebugMeta() {
    return null;
  }
}
