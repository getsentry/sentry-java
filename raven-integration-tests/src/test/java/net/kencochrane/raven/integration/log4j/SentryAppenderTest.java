package net.kencochrane.raven.integration.log4j;

import net.kencochrane.raven.SentryApi;
import net.kencochrane.raven.Utils;
import net.kencochrane.raven.integration.IntegrationContext;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link net.kencochrane.raven.log4j.SentryAppender}.
 */
public class SentryAppenderTest {

    private SentryApi api;

    @Test
    public void debug() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is a debug message";
        Logger logger = Logger.getLogger(loggerName);
        logger.debug(message);

        verify(loggerName, message, "debug", loggerName);
    }

    @Test
    public void debug_withThrowable() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is a debug message";
        Logger logger = Logger.getLogger(loggerName);
        logger.debug(message, new NullPointerException("omg!"));

        final String levelName = "debug";
        final String title = this.getClass().getName() + ".debug_withThrowable";
        verify(loggerName, message, levelName, title);
    }

    @Test
    public void info() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is an info message";
        Logger logger = Logger.getLogger(loggerName);
        logger.info(message);

        verify(loggerName, message, "info", loggerName);
    }

    @Test
    public void info_withThrowable() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is an info message";
        Logger logger = Logger.getLogger(loggerName);
        logger.info(message, new NullPointerException("omg!"));

        verify(loggerName, message, "info", this.getClass().getName() + ".info_withThrowable");
    }

    @Test
    public void warn() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is a warning!";
        Logger logger = Logger.getLogger(loggerName);
        logger.warn(message);

        verify(loggerName, message, "warning", loggerName);
    }

    @Test
    public void warn_withThrowable() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is another warning";
        Logger logger = Logger.getLogger(loggerName);
        logger.warn(message, new NullPointerException("no no no!"));

        verify(loggerName, message, "warning", this.getClass().getName() + ".warn_withThrowable");
    }

    @Test
    public void error() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is an error!";
        Logger logger = Logger.getLogger(loggerName);
        logger.error(message);
        verify(loggerName, message, "error", loggerName);
    }

    @Test
    public void error_withThrowable() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "This is an... err.. or";
        Logger logger = Logger.getLogger(loggerName);
        logger.error(message, new NullPointerException("no no no!"));
        verify(loggerName, message, "error", this.getClass().getName() + ".error_withThrowable");
    }

    @Test
    public void fatal() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "Head for the lifeboat!";
        Logger logger = Logger.getLogger(loggerName);
        logger.fatal(message);
        verify(loggerName, message, "fatal", loggerName);
    }

    @Test
    public void fatal_withThrowable() throws IOException {
        final String loggerName = this.getClass().getName();
        final String message = "What lifeboat?";
        Logger logger = Logger.getLogger(loggerName);
        logger.fatal(message, new NullPointerException("no no no!"));
        verify(loggerName, message, "fatal", this.getClass().getName() + ".fatal_withThrowable");
    }

    @Before
    public void setUp() throws IOException {
        IntegrationContext.init();
        api = IntegrationContext.api;
        api.clear(IntegrationContext.httpDsn.projectId);
        System.setProperty(Utils.SENTRY_DSN, IntegrationContext.httpDsn.toString());
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
    }

    @After
    public void tearDown() throws IOException {
        System.setProperty(Utils.SENTRY_DSN, "");
    }

    private void verify(String loggerName, String message, String levelName, String title) throws IOException {
        List<SentryApi.Event> events = api.getEvents(IntegrationContext.projectId);
        assertEquals(1, events.size());
        SentryApi.Event event = events.get(0);
        assertTrue(event.count > 0);
        assertEquals(levelName, event.levelName);
        assertEquals(message, event.message);
        assertEquals(title, event.title);
        assertEquals(loggerName, event.logger);
    }

}
