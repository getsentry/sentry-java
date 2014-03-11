package net.kencochrane.raven.log4j;

import mockit.Mock;
import mockit.MockUp;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;

public class MockUpErrorHandler extends MockUp<ErrorHandler> {
    private int errorCount = 0;

    @Mock
    public void error(String message) {
        errorCount++;
        System.err.println("[RAVEN] ErrorHandler - " + message);
        System.err.flush();
    }

    @Mock
    public void error(String message, Exception e, int errorCode) {
        error(message);
    }

    @Mock
    public void error(String message, Exception e, int errorCode, LoggingEvent event) {
        error(message);
    }

    public int getErrorCount() {
        return errorCount;
    }
}
