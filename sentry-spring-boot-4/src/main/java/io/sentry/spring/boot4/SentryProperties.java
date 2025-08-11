package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for Sentry integration. */
@ConfigurationProperties("sentry")
@Open
public class SentryProperties extends SentryOptions {

  /** Whether to use Git commit id as a release. */
  private boolean useGitCommitIdAsRelease = true;

  /** Report all or only uncaught web exceptions. */
  private int exceptionResolverOrder = 1;

  /**
   * Defines the {@link io.sentry.spring.SentryUserFilter} order. The default value is {@link
   * org.springframework.core.Ordered.LOWEST_PRECEDENCE}, if Spring Security is auto-configured, its
   * guaranteed to run after Spring Security filter chain.
   */
  private @Nullable Integer userFilterOrder;

  @ApiStatus.Experimental private boolean keepTransactionsOpenForAsyncResponses = false;

  /** Logging framework integration properties. */
  private @NotNull Logging logging = new Logging();

  /** Reactive framework (e.g. WebFlux) integration properties */
  private @NotNull Reactive reactive = new Reactive();

  /**
   * If set to true, this flag disables all AOP related features (e.g. {@link
   * io.sentry.spring7.tracing.SentryTransaction}, {@link io.sentry.spring7.tracing.SentrySpan}) to
   * successfully compile to GraalVM
   */
  private boolean enableAotCompatibility = false;

  /** Graphql integration properties. */
  private @NotNull Graphql graphql = new Graphql();

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

  public boolean isEnableAotCompatibility() {
    return enableAotCompatibility;
  }

  public void setEnableAotCompatibility(boolean enableAotCompatibility) {
    this.enableAotCompatibility = enableAotCompatibility;
  }

  public boolean isKeepTransactionsOpenForAsyncResponses() {
    return keepTransactionsOpenForAsyncResponses;
  }

  public void setKeepTransactionsOpenForAsyncResponses(
      boolean keepTransactionsOpenForAsyncResponses) {
    this.keepTransactionsOpenForAsyncResponses = keepTransactionsOpenForAsyncResponses;
  }

  public @NotNull Graphql getGraphql() {
    return graphql;
  }

  public void setGraphql(@NotNull Graphql graphql) {
    this.graphql = graphql;
  }

  @Open
  public static class Logging {
    /** Enable/Disable logging auto-configuration. */
    private boolean enabled = true;

    /** Minimum logging level for recording breadcrumbs. */
    private @Nullable Level minimumBreadcrumbLevel;

    /** Minimum logging level for recording events. */
    private @Nullable Level minimumEventLevel;

    /** Minimum logging level for recording log events. */
    private @Nullable Level minimumLevel;

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

    public @Nullable Level getMinimumLevel() {
      return minimumLevel;
    }

    public void setMinimumLevel(@Nullable Level minimumLevel) {
      this.minimumLevel = minimumLevel;
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
     * Enable/Disable usage of {@link io.micrometer.context.ThreadLocalAccessor} for Scopes
     * propagation
     */
    private boolean threadLocalAccessorEnabled = true;

    public boolean isThreadLocalAccessorEnabled() {
      return threadLocalAccessorEnabled;
    }

    public void setThreadLocalAccessorEnabled(boolean threadLocalAccessorEnabled) {
      this.threadLocalAccessorEnabled = threadLocalAccessorEnabled;
    }
  }

  @Open
  public static class Graphql {

    /** List of error types the Sentry Graphql integration should ignore. */
    private @NotNull List<String> ignoredErrorTypes = new ArrayList<>();

    @NotNull
    public List<String> getIgnoredErrorTypes() {
      return ignoredErrorTypes;
    }

    public void setIgnoredErrorTypes(final @NotNull List<String> ignoredErrorTypes) {
      this.ignoredErrorTypes = ignoredErrorTypes;
    }
  }
}
