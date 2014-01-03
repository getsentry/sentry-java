package net.kencochrane.raven.event.interfaces;

import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.Test;

import java.util.Deque;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ExceptionWithStackTraceTest {
    @Injectable
    private Throwable mockThrowable;

    @Test
    public void ensureConversionToQueueKeepsOrder(@Injectable final Throwable mockCause) throws Exception {
        final String exceptionMessage = UUID.randomUUID().toString();
        final String causeMessage = UUID.randomUUID().toString();
        new NonStrictExpectations() {{
            mockThrowable.getCause();
            result = new Delegate<Throwable>() {
                public Throwable getCause() {
                    return mockCause;
                }
            };
            mockThrowable.getMessage();
            result = exceptionMessage;
            mockCause.getMessage();
            result = causeMessage;
        }};

        Deque<ExceptionWithStackTrace> exceptions = ExceptionWithStackTrace.extractExceptionQueue(mockThrowable);

        assertThat(exceptions.getFirst().getExceptionMessage(), is(exceptionMessage));
        assertThat(exceptions.getLast().getExceptionMessage(), is(causeMessage));
    }
}
