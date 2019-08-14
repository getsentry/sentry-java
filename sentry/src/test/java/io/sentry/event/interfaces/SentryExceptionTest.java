package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import java.util.Deque;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SentryExceptionTest extends BaseTest {

    @Injectable
    private Throwable mockThrowable = null;

    @Injectable
    private InnerClassThrowable mockInnerClassThrowable = null;

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

    @Test
    public void ensureInnerClassesAreRepresentedCorrectly(@Injectable final InnerClassThrowable mockCause) throws Exception {
        new NonStrictExpectations() {{
            mockInnerClassThrowable.getCause();
            result = new Delegate<Throwable>() {
                @SuppressWarnings("unused")
                public Throwable getCause() {
                    return mockCause;
                }
            };
        }};

        Deque<SentryException> exceptions = SentryException.extractExceptionQueue(mockInnerClassThrowable);
        assertThat(exceptions.getFirst().getExceptionClassName(), is("SentryExceptionTest$InnerClassThrowable"));
    }

    @Test
    public void exceptionMechanismThrowerIsUnwrappedViaConstructor() throws Exception {

        Throwable throwableMock = mock(Throwable.class);
        ExceptionMechanism mechanism = new ExceptionMechanism("type", true);
        String expectedMessage = "message";
        when(throwableMock.getMessage()).thenReturn(expectedMessage);
        StackTraceElement expectedStackTraceElement = new StackTraceElement("c", "m", "f", 1);
        StackTraceElement[] stackTrace = new StackTraceElement[] { expectedStackTraceElement };
        when(throwableMock.getStackTrace()).thenReturn(stackTrace);
        ExceptionMechanismThrowable wrapper = new ExceptionMechanismThrowable(mechanism, throwableMock);

        // Act
        SentryException target = new SentryException(wrapper, new StackTraceElement[0]);

        assertEquals(expectedStackTraceElement.getFileName(), target.getStackTraceInterface().getStackTrace()[0].getFileName());
        assertEquals(expectedMessage, target.getExceptionMessage());
        assertSame(mechanism, target.getExceptionMechanism());
    }

    @Test
    public void exceptionMechanismThrowerIsUnwrappedViaExtractExceptionQueue() throws Exception {

        Throwable throwableMock = mock(Throwable.class);
        ExceptionMechanism mechanism = new ExceptionMechanism("type", true);
        String expectedMessage = "message";
        when(throwableMock.getMessage()).thenReturn(expectedMessage);
        StackTraceElement expectedStackTraceElement = new StackTraceElement("c", "m", "f", 1);
        StackTraceElement[] stackTrace = new StackTraceElement[] { expectedStackTraceElement };
        when(throwableMock.getStackTrace()).thenReturn(stackTrace);
        ExceptionMechanismThrowable wrapper = new ExceptionMechanismThrowable(mechanism, throwableMock);

        // Act
        Deque<SentryException> exceptionDeque = SentryException.extractExceptionQueue(wrapper);
        SentryException target = exceptionDeque.getFirst();

        assertEquals(expectedStackTraceElement.getFileName(), target.getStackTraceInterface().getStackTrace()[0].getFileName());
        assertEquals(expectedMessage, target.getExceptionMessage());
        assertSame(mechanism, target.getExceptionMechanism());
    }

    private static final class InnerClassThrowable extends Throwable {

    }
}
