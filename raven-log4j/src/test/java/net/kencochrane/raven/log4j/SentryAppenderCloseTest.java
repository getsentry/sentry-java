package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.connection.Connection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class SentryAppenderCloseTest {

    @Test
    public void testFailedOpenClose() {
        // This checks that even if sentry wasn't setup correctly it's appender can still be closed.
        SentryAppender appender = new SentryAppender();
        appender.activateOptions();
        try {
            appender.close();
        } catch (Exception e) {
            fail("Even if sentry didn't get configured we shouldn't throw an error when closing.");
        }
    }

    @Test
    public void testMultipleClose() throws IOException {
        Raven raven = mock(Raven.class);
        Connection connection = mock(Connection.class);
        when(raven.getConnection()).thenReturn(connection);

        SentryAppender appender = new SentryAppender(raven, true);
        appender.close();
        try {
            appender.close();
        } catch (Exception e) {
            fail("Closing an appender multiple times shouldn't fail.");
        }
        verify(connection, times(1)).close();
    }
}
