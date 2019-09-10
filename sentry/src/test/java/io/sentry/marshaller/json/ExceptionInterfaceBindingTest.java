package io.sentry.marshaller.json;

import static io.sentry.marshaller.json.JsonComparisonUtil.JsonGeneratorParser;
import static io.sentry.marshaller.json.JsonComparisonUtil.jsonResource;
import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonGenerator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Deque;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.BaseTest;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.ExceptionMechanism;
import io.sentry.event.interfaces.ExceptionMechanismThrowable;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.StackTraceInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ExceptionInterfaceBindingTest extends BaseTest {
    private ExceptionInterfaceBinding interfaceBinding = null;
    private ExceptionInterface mockExceptionInterface = null;
    private InterfaceBinding<StackTraceInterface> mockStackTraceInterfaceBinding = null;

    @Before
    public void setUp() throws Exception {
        mockExceptionInterface = mock(ExceptionInterface.class);
        //noinspection unchecked
        mockStackTraceInterfaceBinding = mock(InterfaceBinding.class);

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                JsonGenerator generator = invocation.getArgument(0);
                generator.writeStartObject();
                generator.writeEndObject();
                return null;
            }
        }).when(mockStackTraceInterfaceBinding).writeInterface(any(JsonGenerator.class), any(StackTraceInterface.class));

        interfaceBinding = new ExceptionInterfaceBinding(mockStackTraceInterfaceBinding);

    }

    @Test
    public void testSimpleException() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "6e65f60d-9f22-495a-9556-7a61eeea2a14";
        final Throwable throwable = new IllegalStateException(message);

        when(mockExceptionInterface.getExceptions()).thenAnswer(new Answer<Deque<SentryException>>() {
            @Override
            public Deque<SentryException> answer(InvocationOnMock invocation) throws Throwable {
                return SentryException.extractExceptionQueue(throwable);
            }
        });

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Exception1.json")));
    }

    @Test
    public void testClassInDefaultPackage() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final Throwable throwable = (Throwable) Class.forName("DefaultPackageException").newInstance();

        when(mockExceptionInterface.getExceptions()).thenAnswer(new Answer<Deque<SentryException>>() {
            @Override
            public Deque<SentryException> answer(InvocationOnMock invocation) throws Throwable {
                return SentryException.extractExceptionQueue(throwable);
            }
        });

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Exception2.json")));
    }

    @Test
    public void testChainedException() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message1 = "a71e6132-9867-457d-8b04-5021cd7a251f";
        final Throwable throwable1 = new IllegalStateException(message1);
        final String message2 = "f1296959-5b86-45f7-853a-cdc25196710b";
        final Throwable throwable2 = new IllegalStateException(message2, throwable1);

        when(mockExceptionInterface.getExceptions()).thenAnswer(new Answer<Deque<SentryException>>() {
            @Override
            public Deque<SentryException> answer(InvocationOnMock invocation) throws Throwable {
                return SentryException.extractExceptionQueue(throwable2);
            }
        });

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Exception3.json")));
    }

    @Test
    public void testExceptionWithMechanism() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "6e65f60d-9f22-495a-9556-7a61eeea2a14";
        final Throwable originalThrowable = new IllegalStateException(message);

        EventBuilder builder = new EventBuilder();
        ExceptionMechanism mechanism = new ExceptionMechanism("the type", true);
        final Throwable throwable = new ExceptionMechanismThrowable(mechanism, originalThrowable);
        builder.withSentryInterface(new ExceptionInterface(throwable));

        when(mockExceptionInterface.getExceptions()).thenAnswer(new Answer<Deque<SentryException>>() {
            @Override
            public Deque<SentryException> answer(InvocationOnMock invocation) throws Throwable {
                return SentryException.extractExceptionQueue(throwable);
            }
        });

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/ExceptionWithMechanism.json")));
    }

}
