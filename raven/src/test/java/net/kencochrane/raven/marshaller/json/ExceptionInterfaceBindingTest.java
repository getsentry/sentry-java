package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExceptionInterfaceBindingTest extends AbstractInterfaceBindingTest {
    private ExceptionInterfaceBinding interfaceBinding;
    @Mock
    private ExceptionInterface mockExceptionInterface;
    @Mock
    private InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        interfaceBinding = new ExceptionInterfaceBinding(stackTraceInterfaceBinding);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                JsonGenerator jsonGenerator = (JsonGenerator) invocation.getArguments()[0];
                if (invocation.getArguments()[1] != null) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeEndObject();
                } else {
                    jsonGenerator.writeNull();
                }
                return null;
            }
        }).when(stackTraceInterfaceBinding).writeInterface(any(JsonGenerator.class), any(StackTraceInterface.class));
    }

    @Test
    public void testSimpleException() throws Exception {
        String message = UUID.randomUUID().toString();
        Throwable throwable = new IllegalStateException(message);
        when(mockExceptionInterface.getThrowable()).thenReturn(new ImmutableThrowable(throwable));

        JsonGenerator jsonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jsonGenerator, mockExceptionInterface);
        jsonGenerator.close();

        JsonNode rootNode = getMapper().readValue(getJsonParser(), JsonNode.class);
        assertThat(rootNode.isArray(), is(true));
        JsonNode exceptionNode = rootNode.get(0);
        assertThat(exceptionNode.get("module").asText(), is(throwable.getClass().getPackage().getName()));
        assertThat(exceptionNode.get("type").asText(), is(throwable.getClass().getSimpleName()));
        assertThat(exceptionNode.get("value").asText(), is(message));
        assertThat(exceptionNode.get("stacktrace").isObject(), is(true));
    }
}
