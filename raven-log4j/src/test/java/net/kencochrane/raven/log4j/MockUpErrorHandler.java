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
    }

    @Mock
    public void error(String message) {
        errorCount++;
    }

    @Mock
    public void error(String message, Exception e, int errorCode, LoggingEvent event) {
        errorCount++;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
