package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.BaseTest;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.StackTraceInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Deque;

import static mockit.Deencapsulation.setField;
import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ExceptionInterfaceBindingTest extends BaseTest {
    @Tested
    private ExceptionInterfaceBinding interfaceBinding = null;
    @Injectable
    private ExceptionInterface mockExceptionInterface = null;
    @Injectable
    private InterfaceBinding<StackTraceInterface> mockStackTraceInterfaceBinding = null;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            mockStackTraceInterfaceBinding.writeInterface(withInstanceOf(JsonGenerator.class), (StackTraceInterface) any);
            result = new Delegate<Void>() {
                @SuppressWarnings("unused")
                public void writeInterface(JsonGenerator generator, StackTraceInterface sentryInterface)
                        throws IOException {
                    generator.writeStartObject();
                    generator.writeEndObject();
                }
            };
        }};
    }

    @Test
    public void testSimpleException() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "6e65f60d-9f22-495a-9556-7a61eeea2a14";
        final Throwable throwable = new IllegalStateException(message);
        new NonStrictExpectations() {{
            mockExceptionInterface.getExceptions();
            result = new Delegate<Deque<SentryException>>() {
                @SuppressWarnings("unused")
                public Deque<SentryException> getExceptions() {
                    return SentryException.extractExceptionQueue(throwable);
                }
            };
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Exception1.json")));
    }

    @Test
    public void testClassInDefaultPackage() throws Exception {
        setField((Object) DefaultPackageException.class, "name", DefaultPackageException.class.getSimpleName());
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final Throwable throwable = new DefaultPackageException();
        new NonStrictExpectations() {{
            mockExceptionInterface.getExceptions();
            result = new Delegate<Deque<SentryException>>() {
                @SuppressWarnings("unused")
                public Deque<SentryException> getExceptions() {
                    return SentryException.extractExceptionQueue(throwable);
                }
            };
        }};

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
        new NonStrictExpectations() {{
            mockExceptionInterface.getExceptions();
            result = new Delegate<Deque<SentryException>>() {
                @SuppressWarnings("unused")
                public Deque<SentryException> getExceptions() {
                    return SentryException.extractExceptionQueue(throwable2);
                }
            };
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockExceptionInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Exception3.json")));
    }

}

/**
 * Exception used to test exceptions defined in the default package.
 * <p>
 * Obviously we can't use an Exception which is really defined in the default package within those tests
 * (can't import it), so instead set the name of the class to remove the package name.<br>
 * {@code Deencapsulation.setField(Object) DefaultPackageException.class, "name", "DefaultPackageClass")}
 */
class DefaultPackageException extends Exception {
}
