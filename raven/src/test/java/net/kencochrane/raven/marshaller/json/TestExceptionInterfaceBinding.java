package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExceptionInterfaceBinding extends AbstractTestInterfaceBinding {
    private ExceptionInterfaceBinding interfaceBinding;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        interfaceBinding = new ExceptionInterfaceBinding();
    }

    @Test
    public void testSimpleException() throws Exception {
        ExceptionInterface exceptionInterface = mock(ExceptionInterface.class);
        String message = UUID.randomUUID().toString();
        Throwable throwable = new IllegalStateException(message);
        when(exceptionInterface.getThrowable()).thenReturn(new ImmutableThrowable(throwable));

        JsonGenerator jsonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jsonGenerator, exceptionInterface);
        jsonGenerator.close();

        JsonNode rootNode = getMapper().readValue(getJsonParser(), JsonNode.class);
        assertEquals(throwable.getClass().getPackage().getName(), rootNode.get("module").asText());
        assertEquals(throwable.getClass().getSimpleName(), rootNode.get("type").asText());
        assertEquals(message, rootNode.get("value").asText());
    }
}
