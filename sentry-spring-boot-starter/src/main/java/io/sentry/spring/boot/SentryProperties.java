package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for Sentry integration. */
@ConfigurationProperties("sentry")
@Open
public class SentryProperties extends SentryOptions {

  /** Weather to use Git commit id as a release. */
  private boolean useGitCommitIdAsRelease = true;

  /** Turns tracing on or off. Default is disabled. */
  private boolean enableTracing;

  /** Logging framework integration properties. */
  private @NotNull Logging logging = new Logging();

  public boolean isUseGitCommitIdAsRelease() {
    return useGitCommitIdAsRelease;
  }

  public void setUseGitCommitIdAsRelease(boolean useGitCommitIdAsRelease) {
    this.useGitCommitIdAsRelease = useGitCommitIdAsRelease;
  }

  public boolean isEnableTracing() {
    return enableTracing;
  }

  public void setEnableTracing(boolean enableTracing) {
    this.enableTracing = enableTracing;
  }

  public Logging getLogging() {
    return logging;
  }

  public void setLogging(@NotNull Logging logging) {
    this.logging = logging;
  }

  @Open
  public static class Logging {
    /** Enable/Disable logging auto-configuration. */
    private boolean enabled = true;

    /** Minimum logging level for recording breadcrumb. */
    private @Nullable Level minimumBreadcrumbLevel;

    /** Minimum logging level for recording event. */
    private @Nullable Level minimumEventLevel;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public @Nullable Level getMinimumBreadcrumbLevel() {
      return minimumBreadcrumbLevel;
    }

    public void setMinimumBreadcrumbLevel(@Nullable Level minimumBreadcrumbLevel) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }

    public @Nullable Level getMinimumEventLevel() {
      return minimumEventLevel;
    }

    public void setMinimumEventLevel(@Nullable Level minimumEventLevel) {
      this.minimumEventLevel = minimumEventLevel;
    }
  }
}
