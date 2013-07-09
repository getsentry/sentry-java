package net.kencochrane.raven.jul;

import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

public class SentryHandlerTest extends AbstractLoggerTest {
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new SentryHandler(getMockRaven()));
    }

    @Override
    public void logAnyLevel(String message) {
        logger.log(Level.INFO, message);
    }

    @Override
    public void logAnyLevel(String message, Throwable exception) {
        logger.log(Level.SEVERE, message, exception);
    }

    @Override
    public void logAnyLevel(String message, List<String> parameters) {
        logger.log(Level.INFO, message, parameters.toArray());
    }

    @Override
    public String getCurrentLoggerName() {
        return logger.getName();
    }

    @Override
    public String getUnformattedMessage() {
        return "Some content {0} {1} {2}";
    }

    @Override
    @Test
    public void testLogLevelConversions() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.FINEST);
        assertLevelConverted(Event.Level.DEBUG, Level.FINER);
        assertLevelConverted(Event.Level.DEBUG, Level.FINE);
        assertLevelConverted(Event.Level.DEBUG, Level.CONFIG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARNING);
        assertLevelConverted(Event.Level.ERROR, Level.SEVERE);
    }

    @Test
    public void testClosePropagates() throws Exception {
        when(getMockRaven().getConnection()).thenReturn(mock(Connection.class));
        Handler handler = new SentryHandler(getMockRaven(), true);

        handler.close();

        verify(getMockRaven().getConnection()).close();
    }

    @Test
    public void testCloseDoesntPropagate() throws Exception {
        when(getMockRaven().getConnection()).thenReturn(mock(Connection.class));
        Handler handler = new SentryHandler(getMockRaven(), false);

        handler.close();

        verify(getMockRaven().getConnection(), never()).close();
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level) {
        logger.log(level, null);
        assertLogLevel(expectedLevel);
    }
}
