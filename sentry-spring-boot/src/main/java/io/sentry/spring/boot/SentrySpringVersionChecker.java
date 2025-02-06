package io.sentry.spring.boot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.context.ApplicationListener;

final class SentrySpringVersionChecker
    implements ApplicationListener<ApplicationContextInitializedEvent> {

  private static final Log logger = LogFactory.getLog(SentrySpringVersionChecker.class);

  @Override
  public void onApplicationEvent(ApplicationContextInitializedEvent event) {

    if (!SpringBootVersion.getVersion().startsWith("2")) {
      logger.warn("############################### WARNING ###############################");
      logger.warn("##                                                                   ##");
      logger.warn("##            !Incompatible Spring Boot Version detected!            ##");
      logger.warn("##              Please see the sentry docs linked below              ##");
      logger.warn("##                Choose your Spring Boot version and                ##");
      logger.warn("##                  install the matching dependency                  ##");
      logger.warn("##                                                                   ##");
      logger.warn("## https://docs.sentry.io/platforms/java/guides/spring-boot/#install ##");
      logger.warn("##                                                                   ##");
      logger.warn("#######################################################################");
    }
  }
}
