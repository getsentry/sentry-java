package io.sentry.event.interfaces;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Deque;

import io.sentry.BaseTest;
import org.junit.Test;

public class SentryExceptionTest extends BaseTest {
    @Test
    public void ensureConversionToQueueKeepsOrder() throws Exception {
        final String exceptionMessage = "208ea34a-9c99-42d6-a399-59a4c85900dc";
        final String causeMessage = "46a1b2ee-629b-49eb-a2be-f5250c995ea4";
        Throwable exception = new Exception(exceptionMessage, new Exception(causeMessage));

        Deque<SentryException> exceptions = SentryException.extractExceptionQueue(exception);

        assertThat(exceptions.getFirst().getExceptionMessage(), is(exceptionMessage));
        assertThat(exceptions.getLast().getExceptionMessage(), is(causeMessage));
    }

    @Test
    public void ensureInnerClassesAreRepresentedCorrectly() throws Exception {
        InnerClassThrowable exception = new InnerClassThrowable(new InnerClassThrowable());

        Deque<SentryException> exceptions = SentryException.extractExceptionQueue(exception);
        assertThat(exceptions.getFirst().getExceptionClassName(), is("SentryExceptionTest$InnerClassThrowable"));
    }

    @Test
    public void exceptionMechanismThrowerIsUnwrappedViaExtractExceptionQueue() throws Exception {
        Throwable throwableMock = mock(Throwable.class);
        String expectedMessage = "message";
        when(throwableMock.getMessage()).thenReturn(expectedMessage);
        StackTraceElement expectedStackTraceElement = new StackTraceElement("c", "m", "f", 1);
        StackTraceElement[] stackTrace = new StackTraceElement[] { expectedStackTraceElement };
        when(throwableMock.getStackTrace()).thenReturn(stackTrace);
        ExceptionMechanism mechanism = new ExceptionMechanism("type", true);
        ExceptionMechanismThrowable wrapper = new ExceptionMechanismThrowable(mechanism, throwableMock);

        // Act
        Deque<SentryException> exceptionDeque = SentryException.extractExceptionQueue(wrapper);
        SentryException target = exceptionDeque.getFirst();

        assertEquals(expectedStackTraceElement.getFileName(), target.getStackTraceInterface().getStackTrace()[0].getFileName());
        assertEquals(expectedMessage, target.getExceptionMessage());
        assertSame(mechanism, target.getExceptionMechanism());
    }

    @Test
    public void exceptionMechanismThrowerIsUnwrappedInInnerException() throws Exception {

        Throwable firstThrowableMock = mock(Throwable.class);
        String expectedFirstMessage = "first";
        when(firstThrowableMock.getMessage()).thenReturn(expectedFirstMessage);
        StackTraceElement expectedFirstStackTraceElement = new StackTraceElement("c", "m", "f", 1);
        StackTraceElement[] firstStackTrace = new StackTraceElement[] { expectedFirstStackTraceElement };
        when(firstThrowableMock.getStackTrace()).thenReturn(firstStackTrace);

        Throwable secondThrowableMock = mock(Throwable.class);
        String expectedSecondMessage = "second";
        when(secondThrowableMock.getMessage()).thenReturn(expectedSecondMessage);
        StackTraceElement expectedSecondStackTraceElement = new StackTraceElement("d", "n", "g", 1);
        StackTraceElement[] secondStackTrace = new StackTraceElement[] { expectedSecondStackTraceElement };
        when(secondThrowableMock.getStackTrace()).thenReturn(secondStackTrace);

        when(secondThrowableMock.getCause()).thenReturn(firstThrowableMock);

        ExceptionMechanism mechanism = new ExceptionMechanism("type", true);
        ExceptionMechanismThrowable wrapper = new ExceptionMechanismThrowable(mechanism, secondThrowableMock);

        // Act
        Deque<SentryException> exceptionDeque = SentryException.extractExceptionQueue(wrapper);
        SentryException second = exceptionDeque.removeFirst();
        SentryException first = exceptionDeque.removeFirst();

        assertEquals(expectedFirstStackTraceElement.getFileName(), first.getStackTraceInterface().getStackTrace()[0].getFileName());
        assertEquals(expectedFirstMessage, first.getExceptionMessage());
        assertNull(first.getExceptionMechanism());

        assertEquals(expectedSecondStackTraceElement.getFileName(), second.getStackTraceInterface().getStackTrace()[0].getFileName());
        assertEquals(expectedSecondMessage, second.getExceptionMessage());
        assertSame(mechanism, second.getExceptionMechanism());
    }

    private static final class InnerClassThrowable extends Throwable {
        InnerClassThrowable() {
        }

        InnerClassThrowable(Throwable cause) {
            super(cause);
        }
    }
}
