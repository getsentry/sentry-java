package io.sentry.samples.log4j2;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import io.sentry.ScopeType;
import io.sentry.Sentry;

public class Main {
  private static final Logger LOGGER = LogManager.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    Sentry.getGlobalScope().setTag("globalTag", "globalValue");
    Sentry.configureScope(ScopeType.ISOLATION, scope -> scope.setTag("isolationScopeTag", "isolationScopeValue"));
    Sentry.configureScope(ScopeType.CURRENT, scope -> scope.setTag("currentScopeTag", "currentScopeValue"));
    // The SDK was initialized through the appender configuration because a DSN was set there.
    // Update the DSN in log4j2.xml to see these events in your Sentry dashboard.
    LOGGER.debug("Hello Sentry!");

    // ThreadContext tags listed in log4j2.xml are converted to Sentry Event tags
    ThreadContext.put("userId", UUID.randomUUID().toString());
    ThreadContext.put("requestId", UUID.randomUUID().toString());
    // ThreadContext tag not listed in log4j2.xml
    ThreadContext.put("context-tag", "context-tag-value");

    // logging arguments are converted to Sentry Event parameters
    LOGGER.info("User has made a purchase of product: {}", 445);
    // because minimumEventLevel is set to WARN this raises an event
    LOGGER.warn("Important warning");

    Thread thread = new Thread(new DemoRunnable());
    thread.start();
    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Throwable e) {
      LOGGER.error("Something went wrong " + Thread.currentThread().getName() + " " + Thread.currentThread().getId(), e);
    }

    Thread.sleep(5000);
  }

  static class DemoRunnable implements Runnable {

    @Override
    public void run() {
      Sentry.getGlobalScope().setTag("globalTag2", "globalValue");
      Sentry.configureScope(ScopeType.ISOLATION, scope -> scope.setTag("isolationScopeTag2", "isolationScopeValue"));
      Sentry.configureScope(ScopeType.CURRENT, scope -> scope.setTag("currentScopeTag2", "currentScopeValue"));
      try {
        throw new RuntimeException("Another invalid productId=445");
      } catch (Throwable e) {
        LOGGER.error("Another thing went wrong " + Thread.currentThread().getName() + " " + Thread.currentThread().getId(), e);
      }
    }
  }
}
