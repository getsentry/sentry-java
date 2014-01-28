package net.kencochrane.raven.appengine.event.helper;

import com.google.apphosting.api.ApiProxy;
import mockit.*;
import net.kencochrane.raven.event.EventBuilder;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AppEngineEventBuilderHelperTest {
    @Tested
    private AppEngineEventBuilderHelper eventBuilderHelper;
    @Injectable
    private EventBuilder mockEventBuilder;
    @Mocked("getCurrentEnvironment")
    private ApiProxy mockApiProxy;

    @Test
    public void ensureHostnameDefineByApiProxyEnvironment(
            @Injectable("d7b8f251-ebe1-446f-8549-2b37982bd548") final String mockHostname,
            @Injectable final ApiProxy.Environment mockEnvironment) {
        new NonStrictExpectations() {{
            mockEnvironment.getAttributes();
            result = Collections.singletonMap("com.google.appengine.runtime.default_version_hostname", mockHostname);
            ApiProxy.getCurrentEnvironment();
            result = mockEnvironment;
        }};

        eventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            String hostname;
            mockEventBuilder.setServerName(hostname = withCapture());
            assertThat(hostname, is(mockHostname));
        }};
    }
}
