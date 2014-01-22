package net.kencochrane.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static net.kencochrane.raven.marshaller.json.JsonTestTool.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MessageInterfaceBindingTest {
    private MessageInterfaceBinding interfaceBinding;
    @Injectable
    private MessageInterface mockMessageInterface;

    @BeforeMethod
    public void setUp() throws Exception {
        interfaceBinding = new MessageInterfaceBinding();
    }

    @Test
    public void testSimpleMessage() throws Exception {
        final JsonGeneratorTool generatorTool = newJsonGenerator();
        final String message = "550ee459-cbb5-438e-91d2-b0bbdefab670";
        final List<String> parameters = Arrays.asList("33ed929b-d803-46b6-a57b-9c0feab1f468",
                "5fc10379-6392-470d-9de5-e4cb805ab78c");
        new NonStrictExpectations() {{
            mockMessageInterface.getMessage();
            result = message;
            mockMessageInterface.getParameters();
            result = parameters;
        }};

        interfaceBinding.writeInterface(generatorTool.generator(), mockMessageInterface);

        assertThat(generatorTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/Message1.json")));
    }
}
