package net.kencochrane.raven.log4j2;

import mockit.Mock;
import mockit.MockUp;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.LogEvent;

public class MockUpErrorHandler extends MockUp<ErrorHandler> {
    private int errorCount = 0;

    @Mock
    public void error(String msg) {
        errorCount++;
        System.err.println(msg);
        System.err.flush();
    }

    @Mock
    public void error(String msg, Throwable t) {
        errorCount++;
        System.err.println(msg);
        t.printStackTrace(System.err);
        System.err.flush();
    }

    @Mock
    public void error(String msg, LogEvent event, Throwable t) {
        errorCount++;
        System.err.println(msg);
        t.printStackTrace(System.err);
        System.err.flush();
    }

    public int getErrorCount() {
        return errorCount;
    }
}
