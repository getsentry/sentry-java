package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.*;

public class HttpEventBuilderHelperTest {
    @Mock
    private EventBuilder eventBuilder;
    private HttpEventBuilderHelper httpEventBuilderHelper;

    private static void simulateRequest(HttpServletRequest request) {
        RavenServletRequestListener ravenServletRequestListener = new RavenServletRequestListener();
        ServletRequestEvent servletRequestEvent = mock(ServletRequestEvent.class);
        when(servletRequestEvent.getServletRequest()).thenReturn(request);
        ravenServletRequestListener.requestInitialized(servletRequestEvent);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        httpEventBuilderHelper = new HttpEventBuilderHelper();
    }

    @Test
    public void testNoRequest() throws Exception {
        httpEventBuilderHelper.helpBuildingEvent(eventBuilder);

        verify(eventBuilder, never()).addSentryInterface(any(SentryInterface.class));
    }

    @Test
    public void testWithRequest() throws Exception {
        simulateRequest(mock(HttpServletRequest.class));
        ArgumentCaptor<SentryInterface> interfaceCaptor = ArgumentCaptor.forClass(SentryInterface.class);

        httpEventBuilderHelper.helpBuildingEvent(eventBuilder);

        verify(eventBuilder).addSentryInterface(interfaceCaptor.capture());
        assertThat(interfaceCaptor.getValue(), is(notNullValue()));
    }
}
