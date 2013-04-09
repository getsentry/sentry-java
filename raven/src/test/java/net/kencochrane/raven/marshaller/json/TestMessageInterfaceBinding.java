package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestMessageInterfaceBinding extends AbstractTestInterfaceBinding {
    private MessageInterfaceBinding interfaceBinding;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        interfaceBinding = new MessageInterfaceBinding();
    }

    @Test
    public void testSimpleMessage() throws Exception {
        String message = UUID.randomUUID().toString();
        List<String> parameters = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        MessageInterface messageInterface = mock(MessageInterface.class);
        when(messageInterface.getMessage()).thenReturn(message);
        when(messageInterface.getParams()).thenReturn(parameters);

        JsonGenerator jSonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jSonGenerator, messageInterface);
        jSonGenerator.close();

        JsonNode rootNode = getMapper().readValue(getJsonParser(), JsonNode.class);
        assertEquals(message, rootNode.get("message").asText());
        assertEquals(parameters, getMapper().convertValue(rootNode.get("params"), List.class));
    }
}
