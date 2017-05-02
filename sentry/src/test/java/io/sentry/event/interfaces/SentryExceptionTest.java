package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.Test;

import java.util.Deque;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryExceptionTest extends BaseTest {
    @Injectable
    private Throwable mockThrowable = null;

    @Test
    public void ensureConversionToQueueKeepsOrder(@Injectable final Throwable mockCause) throws Exception {
        final String exceptionMessage = "208ea34a-9c99-42d6-a399-59a4c85900dc";
        final String causeMessage = "46a1b2ee-629b-49eb-a2be-f5250c995ea4";
        new NonStrictExpectations() {{
            mockThrowable.getCause();
            result = new Delegate<Throwable>() {
                @SuppressWarnings("unused")
                public Throwable getCause() {
                    return mockCause;
                }
            };
            mockThrowable.getMessage();
            result = exceptionMessage;
            mockCause.getMessage();
            result = causeMessage;
        }};

        Deque<SentryException> exceptions = SentryException.extractExceptionQueue(mockThrowable);

        assertThat(exceptions.getFirst().getExceptionMessage(), is(exceptionMessage));
        assertThat(exceptions.getLast().getExceptionMessage(), is(causeMessage));
    }
}
