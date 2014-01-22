package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static net.kencochrane.raven.marshaller.json.JsonTestTool.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ExceptionInterfaceBindingTest {
    private ExceptionInterfaceBinding interfaceBinding;
    @Injectable
    private ExceptionInterface mockExceptionInterface;
    @Injectable
    private InterfaceBinding<StackTraceInterface> mockStackTraceInterfaceBinding;

    @BeforeMethod
    public void setUp() throws Exception {
        interfaceBinding = new ExceptionInterfaceBinding(mockStackTraceInterfaceBinding);

        new NonStrictExpectations() {{
            mockStackTraceInterfaceBinding.writeInterface(withInstanceOf(JsonGenerator.class), (StackTraceInterface) any);
            forEachInvocation = new Delegate() {
                public void writeInterface(JsonGenerator jsonGenerator, StackTraceInterface stackTraceInterface)
                        throws IOException {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeEndObject();
                }
            };
        }};
    }

    @Test
    public void testSimpleException() throws Exception {
        final JsonGeneratorTool generatorTool = newJsonGenerator();
        final String message = "6e65f60d-9f22-495a-9556-7a61eeea2a14";
        final Throwable throwable = new IllegalStateException(message);
        new NonStrictExpectations() {{
            mockExceptionInterface.getThrowable();
            result = new Delegate<Void>() {
                public ImmutableThrowable getThrowable() {
                    return new ImmutableThrowable(throwable);
                }
            };
        }};

        interfaceBinding.writeInterface(generatorTool.generator(), mockExceptionInterface);

        assertThat(generatorTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/Exception1.json")));
    }

    @Test
    public void testClassInDefaultPackage() throws Exception {
        Deencapsulation.setField((Object) DefaultPackageException.class, "name",
                DefaultPackageException.class.getSimpleName());
        final JsonGeneratorTool generatorTool = newJsonGenerator();
        final Throwable throwable = new DefaultPackageException();
        new NonStrictExpectations() {{
            mockExceptionInterface.getThrowable();
            result = new Delegate<Void>() {
                public ImmutableThrowable getThrowable() {
                    return new ImmutableThrowable(throwable);
                }
            };
        }};

        interfaceBinding.writeInterface(generatorTool.generator(), mockExceptionInterface);

        assertThat(generatorTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/Exception2.json")));
    }
}

/**
 * Exception used to test exceptions defined in the default package.
 * <p>
 * Obviously we can't use an Exception which is really defined in the default package within those tests
 * (can't import it), so instead set the name of the class to remove the package name.<br />
 * {@code Deencapsulation.setField(Object) DefaultPackageException.class, "name", "DefaultPackageClass")}
 * </p>
 */
class DefaultPackageException extends Exception {
}
