package io.sentry.marshaller.json;

import io.sentry.BaseTest;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import io.sentry.event.interfaces.MessageInterface;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MessageInterfaceBindingTest extends BaseTest {
    @Tested
    private MessageInterfaceBinding interfaceBinding = null;
    @Injectable
    private MessageInterface mockMessageInterface = null;

    @Test
    public void testSimpleMessage() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "550ee459-cbb5-438e-91d2-b0bbdefab670";
        final List<String> parameters = Arrays.asList("33ed929b-d803-46b6-a57b-9c0feab1f468",
                "5fc10379-6392-470d-9de5-e4cb805ab78c");
        new NonStrictExpectations() {{
            mockMessageInterface.getMessage();
            result = message;
            mockMessageInterface.getParameters();
            result = parameters;
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockMessageInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Message1.json")));
    }
}
