package io.sentry.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryOptions;
import java.util.Arrays;
import java.util.List;
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

  /** Report all or only uncaught web exceptions. */
  private int exceptionResolverOrder = 1;

  /**
   * Defines the {@link io.sentry.spring.SentryUserFilter} order. The default value is {@link
   * org.springframework.core.Ordered.LOWEST_PRECEDENCE}, if Spring Security is auto-configured, its
   * guaranteed to run after Spring Security filter chain.
   */
  private @Nullable Integer userFilterOrder;

  /** Logging framework integration properties. */
  private @NotNull Logging logging = new Logging();

  /** Reactive framework (e.g. WebFlux) integration properties */
  private @NotNull Reactive reactive = new Reactive();

  public boolean isUseGitCommitIdAsRelease() {
    return useGitCommitIdAsRelease;
  }

  public void setUseGitCommitIdAsRelease(boolean useGitCommitIdAsRelease) {
    this.useGitCommitIdAsRelease = useGitCommitIdAsRelease;
  }

  /**
   * Returns the order used for Spring SentryExceptionResolver, which determines whether all web
   * exceptions are reported, or only uncaught exceptions.
   *
   * @return order to use for Spring SentryExceptionResolver
   */
  public int getExceptionResolverOrder() {
    return exceptionResolverOrder;
  }

  /**
   * Sets the order to use for Spring SentryExceptionResolver, which determines whether all web
   * exceptions are reported, or only uncaught exceptions.
   *
   * @param exceptionResolverOrder order to use for Spring SentryExceptionResolver
   */
  public void setExceptionResolverOrder(int exceptionResolverOrder) {
    this.exceptionResolverOrder = exceptionResolverOrder;
  }

  public @Nullable Integer getUserFilterOrder() {
    return userFilterOrder;
  }

  public void setUserFilterOrder(final @Nullable Integer userFilterOrder) {
    this.userFilterOrder = userFilterOrder;
  }

  public @NotNull Logging getLogging() {
    return logging;
  }

  public void setLogging(@NotNull Logging logging) {
    this.logging = logging;
  }

  public @NotNull Reactive getReactive() {
    return reactive;
  }

  public void setReactive(@NotNull Reactive reactive) {
    this.reactive = reactive;
  }

  @Open
  public static class Logging {
    /** Enable/Disable logging auto-configuration. */
    private boolean enabled = true;

    /** Minimum logging level for recording breadcrumb. */
    private @Nullable Level minimumBreadcrumbLevel;

    /** Minimum logging level for recording event. */
    private @Nullable Level minimumEventLevel;

    /** List of loggers the SentryAppender should be added to. */
    private @NotNull List<String> loggers = Arrays.asList(org.slf4j.Logger.ROOT_LOGGER_NAME);

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

    @NotNull
    public List<String> getLoggers() {
      return loggers;
    }

    public void setLoggers(final @NotNull List<String> loggers) {
      this.loggers = loggers;
    }
  }

  @Open
  public static class Reactive {
    /**
     * Enable/Disable usage of {@link io.micrometer.context.ThreadLocalAccessor} for Hub propagation
     */
    private boolean threadLocalAccessorEnabled = true;

    public boolean isThreadLocalAccessorEnabled() {
      return threadLocalAccessorEnabled;
    }

    public void setThreadLocalAccessorEnabled(boolean threadLocalAccessorEnabled) {
      this.threadLocalAccessorEnabled = threadLocalAccessorEnabled;
    }
  }
}
