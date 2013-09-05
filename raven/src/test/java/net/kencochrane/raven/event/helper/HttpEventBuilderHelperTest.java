package net.kencochrane.raven.event.helper;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Verifications;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

public class HttpEventBuilderHelperTest {
    private HttpEventBuilderHelper httpEventBuilderHelper;
    @Injectable
    private EventBuilder mockEventBuilder;
    @Mocked("getServletRequest")
    private RavenServletRequestListener ravenServletRequestListener;

    @BeforeMethod
    public void setUp() throws Exception {
        httpEventBuilderHelper = new HttpEventBuilderHelper();
    }

    @Test
    public void testNoRequest() throws Exception {
        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            mockEventBuilder.addSentryInterface((SentryInterface) any);
            times = 0;
        }};
    }

    @Test
    public void testWithRequest(@Injectable final HttpServletRequest mockHttpServletRequest) throws Exception {
        new Expectations() {{
            RavenServletRequestListener.getServletRequest();
            result = mockHttpServletRequest;
        }};

        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            mockEventBuilder.addSentryInterface(this.<HttpInterface>withNotNull());
        }};
    }
}
