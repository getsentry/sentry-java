package net.kencochrane.raven.log4j;

import mockit.Mock;
import mockit.MockUp;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;

public class MockUpErrorHandler extends MockUp<ErrorHandler> {
    private int errorCount = 0;

    @Mock
    public void error(String message, Exception e, int errorCode) {
        errorCount++;
        System.err.println(message);
        e.printStackTrace(System.err);
        System.err.flush();
    }

    @Mock
    public void error(String message) {
        errorCount++;
        System.err.println(message);
        System.err.flush();
    }

    @Mock
    public void error(String message, Exception e, int errorCode, LoggingEvent event) {
        errorCount++;
        System.err.println(message);
        e.printStackTrace(System.err);
        System.err.flush();
    }

    public int getErrorCount() {
        return errorCount;
    }
}
