package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpEventBuilderHelperTest {
    @Mock
    private EventBuilder mockEventBuilder;
    private HttpEventBuilderHelper httpEventBuilderHelper;

    private static void simulateRequest() {
        ServletRequestEvent servletRequestEvent = mock(ServletRequestEvent.class);
        when(servletRequestEvent.getServletRequest()).thenReturn(mock(HttpServletRequest.class));
        new RavenServletRequestListener().requestInitialized(servletRequestEvent);
    }

    @Before
    public void setUp() throws Exception {
        httpEventBuilderHelper = new HttpEventBuilderHelper();
    }

    @Test
    public void testNoRequest() throws Exception {
        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        verify(mockEventBuilder, never()).addSentryInterface(any(SentryInterface.class));
    }

    @Test
    public void testWithRequest() throws Exception {
        simulateRequest();
        ArgumentCaptor<SentryInterface> interfaceCaptor = ArgumentCaptor.forClass(SentryInterface.class);

        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        verify(mockEventBuilder).addSentryInterface(interfaceCaptor.capture());
        assertThat(interfaceCaptor.getValue(), is(notNullValue()));
    }
}
