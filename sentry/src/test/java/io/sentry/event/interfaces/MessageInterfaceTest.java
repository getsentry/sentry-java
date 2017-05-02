package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MessageInterfaceTest extends BaseTest {

    @Test
    public void testStandaloneMessage() throws Exception {
        final String message = "3d301be3-2990-4735-9dbf-d1b610c14a84";

        final MessageInterface messageInterface = new MessageInterface(message);

        assertThat(messageInterface.getMessage(), is(message));
        assertThat(messageInterface.getParameters(), is(empty()));
        assertThat(messageInterface.getInterfaceName(), is(MessageInterface.MESSAGE_INTERFACE));
    }

    @Test
    public void testListParameters() throws Exception {
        final String message = "b88145c2-8c46-49fc-81cc-8982f288e5c2";
        final List<String> parameters = Collections.singletonList("e703048a-0084-4306-a04e-04eaca572046");

        final MessageInterface messageInterface = new MessageInterface(message, parameters);

        assertThat(messageInterface.getMessage(), is(message));
        assertThat(messageInterface.getParameters(), equalTo(parameters));
        assertThat(messageInterface.getInterfaceName(), is(MessageInterface.MESSAGE_INTERFACE));
    }

    @Test
    public void testVarargsParameters() throws Exception {
        final String message = "b3b31d87-de49-47fb-8f83-e3be45e7a611";
        final String parameter = "9113953f-3306-4aeb-8d3a-319b1ea83683";

        final MessageInterface messageInterface = new MessageInterface(message, parameter);

        assertThat(messageInterface.getMessage(), is(message));
        assertThat(messageInterface.getParameters(), equalTo(Collections.singletonList(parameter)));
        assertThat(messageInterface.getInterfaceName(), is(MessageInterface.MESSAGE_INTERFACE));
    }
}
