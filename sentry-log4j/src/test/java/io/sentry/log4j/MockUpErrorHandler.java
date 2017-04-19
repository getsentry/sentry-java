package io.sentry.log4j;

import mockit.Mock;
import mockit.MockUp;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockUpErrorHandler extends MockUp<ErrorHandler> {
    private static final Logger logger = LoggerFactory.getLogger("ErrorHandler");
    private int errorCount = 0;

    @Mock
    public void error(String message) {
        errorCount++;
        logger.error(message);
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
