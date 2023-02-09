package io.sentry;

import io.sentry.protocol.SentryPackage;
import io.sentry.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryIntegrationPackageStorage {
  //  private static volatile @Nullable SentryIntegrationPackageStorage INSTANCE;

  //  public static @NotNull SentryIntegrationPackageStorage getInstance() {
  //    if (INSTANCE == null) {
  //      synchronized (SentryIntegrationPackageStorage.class) {
  //        if (INSTANCE == null) {
  //          INSTANCE = new SentryIntegrationPackageStorage();
  //        }
  //      }
  //    }
  //
  //    return INSTANCE;
  //  }

  /**
   * List of integrations that are enabled in the SDK. _Optional._
   *
   * <p>The list should have all enabled integrations, including default integrations. Default
   * integrations are included because different SDK releases may contain different default
   * integrations.
   */
  private static Set<String> integrations = new CopyOnWriteArraySet<>();

  /**
   * List of installed and loaded SDK packages. _Optional._
   *
   * <p>A list of packages that were installed as part of this SDK or the activated integrations.
   * Each package consists of a name in the format `source:identifier` and `version`. If the source
   * is a Git repository, the `source` should be `git`, the identifier should be a checkout link and
   * the version should be a Git reference (branch, tag or SHA).
   */
  private static Set<SentryPackage> packages = new CopyOnWriteArraySet<>();

  private SentryIntegrationPackageStorage() {}

  public static void addIntegration(final @NotNull String integration) {
    Objects.requireNonNull(integration, "integration is required.");
    integrations.add(integration);
  }

  public static @Nullable Set<String> getIntegrations() {
    return integrations != null ? new CopyOnWriteArraySet<>(integrations) : null;
  }

  public static void setIntegrations(final @NotNull List<String> integrationList) {
    Objects.requireNonNull(integrationList, "integrationList is required.");
    integrations.clear();
    integrations.addAll(integrationList);
  }

  public static void addPackage(final @NotNull String name, final @NotNull String version) {
    Objects.requireNonNull(name, "name is required.");
    Objects.requireNonNull(version, "version is required.");

    SentryPackage newPackage = new SentryPackage(name, version);
    packages.add(newPackage);
  }

  public static @Nullable List<SentryPackage> getPackages() {
    return packages != null ? new CopyOnWriteArrayList<>(packages) : null;
  }

  public static void setPackages(final @NotNull List<SentryPackage> packageList) {
    Objects.requireNonNull(packageList, "packageList is required.");

    packages.clear();
    packages.addAll(packageList);
  }

  @TestOnly
  public static void clearStorage() {
    integrations.clear();
    packages.clear();
  }
}
