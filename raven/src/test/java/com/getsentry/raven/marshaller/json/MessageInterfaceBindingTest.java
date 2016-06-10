package com.getsentry.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import com.getsentry.raven.event.interfaces.MessageInterface;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static com.getsentry.raven.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MessageInterfaceBindingTest {
    @Tested
    private MessageInterfaceBinding interfaceBinding = null;
    @Injectable
    private MessageInterface mockMessageInterface = null;

    @Test
    public void testSimpleMessage() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "%s 550ee459-cbb5-438e-91d2-b0bbdefab670 %s";
        final List<String> parameters = Arrays.asList("33ed929b-d803-46b6-a57b-9c0feab1f468",
                "5fc10379-6392-470d-9de5-e4cb805ab78c");
        final String formatted = "33ed929b-d803-46b6-a57b-9c0feab1f468"
            + " 550ee459-cbb5-438e-91d2-b0bbdefab670"
            + " 5fc10379-6392-470d-9de5-e4cb805ab78c";
        new NonStrictExpectations() {{
            mockMessageInterface.getMessage();
            result = message;
            mockMessageInterface.getParameters();
            result = parameters;
            mockMessageInterface.getFormatted();
            result = formatted;
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockMessageInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/Message1.json")));
    }

    @Test
    public void formattedNotIncludedWhenInvalid() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String message = "550ee459-cbb5-438e-91d2-b0bbdefab670 %s %s";
        final List<String> parameters = Arrays.asList("33ed929b-d803-46b6-a57b-9c0feab1f468");

        new NonStrictExpectations() {{
            mockMessageInterface.getMessage();
            result = message;
            mockMessageInterface.getParameters();
            result = parameters;
            mockMessageInterface.getFormatted();
            result = null;
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockMessageInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/Message2.json")));
    }

}
