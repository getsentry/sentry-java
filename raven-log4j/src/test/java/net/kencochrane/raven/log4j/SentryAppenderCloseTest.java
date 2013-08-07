package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.connection.Connection;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class SentryAppenderCloseTest {

    @Test
    public void testFailedOpenClose() {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        SentryAppender appender = new SentryAppender();
        appender.activateOptions();
        appender.close();
    }

    @Test
    public void testMultipleClose() throws IOException {
        Raven raven = mock(Raven.class);
        Connection connection = mock(Connection.class);
        when(raven.getConnection()).thenReturn(connection);

        SentryAppender appender = new SentryAppender(raven, true);
        appender.close();

        appender.close();
        verify(connection, times(1)).close();
    }
}
