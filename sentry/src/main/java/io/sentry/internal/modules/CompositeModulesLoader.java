package io.sentry.internal.modules;

import io.sentry.ILogger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
@ApiStatus.Internal
public final class CompositeModulesLoader extends ModulesLoader {

  private final List<IModulesLoader> loaders;

  public CompositeModulesLoader(
      final @NotNull List<IModulesLoader> loaders, final @NotNull ILogger logger) {
    super(logger);
    this.loaders = loaders;
  }

  @Override
  protected Map<String, String> loadModules() {
    final @NotNull TreeMap<String, String> allModules = new TreeMap<>();

    for (IModulesLoader loader : this.loaders) {
      final @Nullable Map<String, String> modules = loader.getOrLoadModules();
      if (modules != null) {
        allModules.putAll(modules);
      }
    }

    return allModules;
  }
}
