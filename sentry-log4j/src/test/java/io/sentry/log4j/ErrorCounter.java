package io.sentry.log4j;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.log4j.Appender;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorCounter {
    private static final Logger logger = LoggerFactory.getLogger("CountingErrorHandler");
    private final ErrorHandler handler;
    private int errorCount;

    public ErrorCounter() {
        handler = mock(ErrorHandler.class);

        Answer<Void> count = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                errorCount++;
                logger.error((String) invocation.getArgument(0));
                return null;
            }
        };

        doAnswer(count).when(handler).error(anyString());
        doAnswer(count).when(handler).error(anyString(), any(Exception.class), anyInt());
        doAnswer(count).when(handler).error(anyString(), any(Exception.class), anyInt(), any(LoggingEvent.class));
    }

    public ErrorHandler getErrorHandler() {
        return handler;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
