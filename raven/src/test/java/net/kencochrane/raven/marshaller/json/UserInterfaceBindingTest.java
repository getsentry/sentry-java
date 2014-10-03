package net.kencochrane.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import net.kencochrane.raven.event.interfaces.UserInterface;
import org.testng.annotations.Test;

import static net.kencochrane.raven.marshaller.json.JsonTestTool.jsonResource;
import static net.kencochrane.raven.marshaller.json.JsonTestTool.newJsonGenerator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UserInterfaceBindingTest {
    @Tested
    private UserInterfaceBinding userInterfaceBinding;
    @Injectable
    private UserInterface mockUserInterface = null;

    @Test
    public void testSimpleMessage() throws Exception {
        final JsonTestTool.JsonGeneratorTool generatorTool = newJsonGenerator();
        final String id = "970e9df6-6e0b-4a27-b2ee-0faf0f368354";
        final String username = "3eaa555a-e813-4778-9852-7c1880bf0fd7";
        final String email = "9bcade34-a58c-4616-9de7-bc8b456c96de";
        final String ipAddress = "9a1a658b-6f74-43ae-9e45-0f89f4c5fcb4";
        new NonStrictExpectations() {{
            mockUserInterface.getId();
            result = id;
            mockUserInterface.getUsername();
            result = username;
            mockUserInterface.getEmail();
            result = email;
            mockUserInterface.getIpAddress();
            result = ipAddress;
        }};

        userInterfaceBinding.writeInterface(generatorTool.generator(), mockUserInterface);

        assertThat(generatorTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/User1.json")));
    }
}
