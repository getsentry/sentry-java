package net.kencochrane.raven.event.helper;

import mockit.*;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

public class HttpEventBuilderHelperTest {
    @Tested
    private HttpEventBuilderHelper httpEventBuilderHelper = null;
    @Injectable
    private EventBuilder mockEventBuilder = null;
    @SuppressWarnings("unused")
    @Mocked
    private HttpInterface mockHttpInterface = null;

    @Test
    public void testNoRequest() throws Exception {
        new NonStrictExpectations(RavenServletRequestListener.class) {{
            RavenServletRequestListener.getServletRequest();
            result = null;
        }};
        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            new HttpInterface(withInstanceOf(HttpServletRequest.class));
            times = 0;
            mockEventBuilder.addSentryInterface(withInstanceOf(SentryInterface.class));
            times = 0;
        }};
    }

    @Test
    public void testWithRequest(@Injectable final HttpServletRequest mockHttpServletRequest) throws Exception {
        new NonStrictExpectations(RavenServletRequestListener.class) {{
            RavenServletRequestListener.getServletRequest();
            result = mockHttpServletRequest;
        }};

        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            new HttpInterface(mockHttpServletRequest);
            mockEventBuilder.addSentryInterface(this.<HttpInterface>withNotNull());
        }};
    }
}
