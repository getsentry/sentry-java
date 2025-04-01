package io.sentry.internal.modules;

import static io.sentry.util.ClassLoaderUtils.classLoaderOrDefault;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
@ApiStatus.Internal
public final class ManifestModulesLoader extends ModulesLoader {
  private final Pattern URL_LIB_PATTERN = Pattern.compile(".*/(.+)!/META-INF/MANIFEST.MF");
  private final Pattern NAME_AND_VERSION = Pattern.compile("(.*?)-(\\d+\\.\\d+.*).jar");
  private final ClassLoader classLoader;

  public ManifestModulesLoader(final @NotNull ILogger logger) {
    this(ManifestModulesLoader.class.getClassLoader(), logger);
  }

  ManifestModulesLoader(final @Nullable ClassLoader classLoader, final @NotNull ILogger logger) {
    super(logger);
    this.classLoader = classLoaderOrDefault(classLoader);
  }

  @Override
  protected Map<String, String> loadModules() {
    final @NotNull Map<String, String> modules = new HashMap<>();
    List<Module> detectedModules = detectModulesViaManifestFiles();

    for (Module module : detectedModules) {
      modules.put(module.name, module.version);
    }

    return modules;
  }

  private @NotNull List<Module> detectModulesViaManifestFiles() {
    final @NotNull List<Module> modules = new ArrayList<>();
    try {
      final @NotNull Enumeration<URL> manifestUrls =
          classLoader.getResources("META-INF/MANIFEST.MF");
      while (manifestUrls.hasMoreElements()) {
        final @NotNull URL manifestUrl = manifestUrls.nextElement();
        final @Nullable String originalName = extractDependencyNameFromUrl(manifestUrl);
        final @Nullable Module module = convertOriginalNameToModule(originalName);
        if (module != null) {
          modules.add(module);
        }
      }
    } catch (Throwable e) {
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, "Unable to detect modules via manifest files.", e);
      }
    }

    return modules;
  }

  private @Nullable Module convertOriginalNameToModule(@Nullable String originalName) {
    if (originalName == null) {
      return null;
    }

    final @NotNull Matcher matcher = NAME_AND_VERSION.matcher(originalName);
    if (matcher.matches() && matcher.groupCount() == 2) {
      @NotNull String moduleName = matcher.group(1);
      @NotNull String moduleVersion = matcher.group(2);
      return new Module(moduleName, moduleVersion);
    }

    return null;
  }

  private @Nullable String extractDependencyNameFromUrl(final @NotNull URL url) {
    final @NotNull String urlString = url.toString();
    final @NotNull Matcher matcher = URL_LIB_PATTERN.matcher(urlString);
    if (matcher.matches() && matcher.groupCount() == 1) {
      return matcher.group(1);
    }

    return null;
  }

  private static final class Module {
    private final @NotNull String name;
    private final @NotNull String version;

    public Module(final @NotNull String name, final @NotNull String version) {
      this.name = name;
      this.version = version;
    }
  }
}
