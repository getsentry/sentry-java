package net.kencochrane.raven.event.helper;

import mockit.*;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.event.interfaces.UserInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

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
            mockEventBuilder.addSentryInterface(withInstanceOf(SentryInterface.class), anyBoolean);
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
            new UserInterface(null, null, null, null);
            mockEventBuilder.addSentryInterface(this.<HttpInterface>withNotNull(), false);
            mockEventBuilder.addSentryInterface(this.<UserInterface>withNotNull(), false);
        }};
    }

    @Test
    public void testWithUserPrincipal(@Injectable final HttpServletRequest mockHttpServletRequest,
                                      @Injectable final Principal mockUserPrincipal,
                                      @Injectable("93ad24e4-cad1-4214-af8e-2e48e76b02de") final String mockUsername)
            throws Exception {
        new NonStrictExpectations(RavenServletRequestListener.class) {{
            RavenServletRequestListener.getServletRequest();
            result = mockHttpServletRequest;
            mockHttpServletRequest.getUserPrincipal();
            result = mockUserPrincipal;
            mockUserPrincipal.getName();
            result = mockUsername;
        }};

        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            new UserInterface(null, mockUsername, null, null);
            mockEventBuilder.addSentryInterface(this.<UserInterface>withNotNull(), false);
        }};
    }

    @Test
    public void testWithIpAddress(@Injectable final HttpServletRequest mockHttpServletRequest,
                                  @Injectable("d90e92cc-1929-4f9e-a44c-3062a8b00c70") final String mockIpAddress)
            throws Exception {
        new NonStrictExpectations(RavenServletRequestListener.class) {{
            RavenServletRequestListener.getServletRequest();
            result = mockHttpServletRequest;
            mockHttpServletRequest.getRemoteAddr();
            result = mockIpAddress;
        }};

        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        new Verifications() {{
            new UserInterface(null, null, mockIpAddress, null);
            mockEventBuilder.addSentryInterface(this.<UserInterface>withNotNull(), false);
        }};
    }
}
