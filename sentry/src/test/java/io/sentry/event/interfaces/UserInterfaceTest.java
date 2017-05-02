package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UserInterfaceTest extends BaseTest {
    @Test
    public void testListParameters() throws Exception {
        final String id = "a8750a8d-1f67-41d3-a83d-0f04c08a2760";
        final String username = "dfdf5b9e-09e2-4993-89c3-68962b43370a";
        final String ipAddress = "584ed94b-e771-44eb-b722-74db19485efc";
        final String email = "e9c743ad-3b46-4c7e-8d4a-16688c496fc3";

        final UserInterface userInterface = new UserInterface(id, username, ipAddress, email);

        assertThat(userInterface.getId(), is(id));
        assertThat(userInterface.getUsername(), is(username));
        assertThat(userInterface.getIpAddress(), is(ipAddress));
        assertThat(userInterface.getEmail(), is(email));
        assertThat(userInterface.getInterfaceName(), is(UserInterface.USER_INTERFACE));
    }
}

