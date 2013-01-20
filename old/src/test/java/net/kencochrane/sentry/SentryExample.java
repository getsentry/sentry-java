package net.kencochrane.sentry;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 11:35 AM
 */

// Import log4j classes.

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

/**
 * Simple example used to test out the sentry logger.
 */
public class SentryExample {

    // Define a static logger variable so that it references the
    // Logger instance named "MyApp".
    static final Logger logger = Logger.getLogger(SentryExample.class);

    public void triggerRuntimeException() {
        try {
            triggerNullPointer();
        } catch (Exception e) {
            throw new RuntimeException("Error triggering null pointer", e);
        }
    }

    public String triggerNullPointer() {
        String c = null;
        return c.toLowerCase();
    }

    @Test
    public void test_simple() {

        // PropertyConfigurator.
        PropertyConfigurator.configure(getClass().getResource("/log4j_configuration.txt"));

        logger.debug("Debug example");
        logger.error("Error example");
        logger.trace("Trace Example");
        logger.fatal("Fatal Example");
        logger.info("info Example");
        logger.warn("Warn Example");
        try {
            triggerRuntimeException();
        } catch (RuntimeException e) {
            logger.error("Error example with stacktrace", e);
        }
        // This really shouldn't be necessary
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
