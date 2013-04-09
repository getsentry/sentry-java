package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestStackTraceInterfaceBinding extends AbstractTestInterfaceBinding {
    private StackTraceInterfaceBinding interfaceBinding;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        interfaceBinding = new StackTraceInterfaceBinding();
    }

    @Test
    public void testSingleStackFrame() throws Exception {
        String methodName = UUID.randomUUID().toString();
        String className = UUID.randomUUID().toString();
        int lineNumber = 1;
        Throwable exception = mock(Throwable.class);
        StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, null, lineNumber);
        StackTraceInterface stackTraceInterface = mock(StackTraceInterface.class, RETURNS_DEEP_STUBS);
        when(stackTraceInterface.getThrowable()).thenReturn(new ImmutableThrowable(exception));
        when(exception.getStackTrace()).thenReturn(new StackTraceElement[]{stackTraceElement});

        JsonGenerator jSonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jSonGenerator, stackTraceInterface);
        jSonGenerator.close();

        JsonNode frames = getMapper().readValue(getJsonParser(), JsonNode.class).get("frames");
        assertEquals(1, frames.size());
        assertEquals(className, frames.get(0).get("module").asText());
        assertEquals(methodName, frames.get(0).get("function").asText());
        assertEquals(lineNumber, frames.get(0).get("lineno").asInt());
    }
}
