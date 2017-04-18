package io.sentry.log4j2;

import mockit.Mock;
import mockit.MockUp;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockUpErrorHandler extends MockUp<ErrorHandler> {
    private static final Logger logger = LoggerFactory.getLogger("ErrorHandler");
    private int errorCount = 0;

    @Mock
    public void error(String msg) {
        errorCount++;
        logger.error(msg);
    }

    @Mock
    public void error(String msg, Throwable t) {
        error(msg);
    }

    @Mock
    public void error(String msg, LogEvent event, Throwable t) {
        error(msg);
    }

    public int getErrorCount() {
        return errorCount;
    }
}
