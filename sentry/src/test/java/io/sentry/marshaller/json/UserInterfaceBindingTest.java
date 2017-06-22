package io.sentry.marshaller.json;

import io.sentry.BaseTest;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import io.sentry.event.interfaces.UserInterface;
import io.sentry.marshaller.json.JsonComparisonUtil.JsonGeneratorParser;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.sentry.marshaller.json.JsonComparisonUtil.jsonResource;
import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonGenerator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UserInterfaceBindingTest extends BaseTest {
    @Tested
    private UserInterfaceBinding userInterfaceBinding = null;
    @Injectable
    private UserInterface mockUserInterface = null;

    @Test
    public void testSimpleMessage() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String id = "970e9df6-6e0b-4a27-b2ee-0faf0f368354";
        final String username = "3eaa555a-e813-4778-9852-7c1880bf0fd7";
        final String email = "9bcade34-a58c-4616-9de7-bc8b456c96de";
        final String ipAddress = "9a1a658b-6f74-43ae-9e45-0f89f4c5fcb4";
        final Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        data.put("baz", 2);
        data.put("qux", null);

        new NonStrictExpectations() {{
            mockUserInterface.getId();
            result = id;
            mockUserInterface.getUsername();
            result = username;
            mockUserInterface.getEmail();
            result = email;
            mockUserInterface.getIpAddress();
            result = ipAddress;
            mockUserInterface.getData();
            result = data;
        }};

        userInterfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockUserInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/User1.json")));
    }
}
