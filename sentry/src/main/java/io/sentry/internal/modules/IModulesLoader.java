package io.sentry.internal.modules;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IModulesLoader {
  @Nullable
  Map<String, String> getOrLoadModules();
}
