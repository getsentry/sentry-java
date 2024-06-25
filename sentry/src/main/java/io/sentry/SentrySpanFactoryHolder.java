package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * NOTE: This just exists as a workaround for a bug.
 *
 * <p>What bug? When using sentry-opentelemetry-agent with SENTRY_AUTO_INIT=false a global storage
 * for spans does not work correctly since it's loaded multiple times. Once for bootstrap
 * classloader (a.k.a null) and once for the agent classloader. Since the agent is currently loading
 * these classes into the agent classloader, there should not be a noticable problem, when using the
 * default of SENTRY_AUTO_INIT=true. In the future we plan to have the agent also load the classes
 * into the bootstrap classloader, then this hack should no longer be necessary.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class SentrySpanFactoryHolder {

  private static ISpanFactory spanFactory = new DefaultSpanFactory();

  public static ISpanFactory getSpanFactory() {
    return spanFactory;
  }

  @ApiStatus.Internal
  public static void setSpanFactory(final @NotNull ISpanFactory factory) {
    spanFactory = factory;
  }
}
