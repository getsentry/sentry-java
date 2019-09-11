package io.sentry.servlet;

import io.sentry.BaseTest;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SentryServletRequestListenerTest extends BaseTest {
    private SentryServletRequestListener sentryServletRequestListener = null;
    private ServletRequestEvent mockServletRequestEvent = null;

    @Before
    public void setup() {
        mockServletRequestEvent = mock(ServletRequestEvent.class);
        sentryServletRequestListener = new SentryServletRequestListener();
        SentryServletRequestListener.reset();
    }

    @Test
    public void requestListenerEmptyByDefault() throws Exception {
        assertThat(SentryServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerContainsTheCurrentRequest() throws Exception {
        HttpServletRequest mockHttpServletRequest = mock(HttpServletRequest.class);
        when(mockServletRequestEvent.getServletRequest()).thenReturn(mockHttpServletRequest);

        sentryServletRequestListener.requestInitialized(mockServletRequestEvent);

        assertThat(SentryServletRequestListener.getServletRequest(), is(mockHttpServletRequest));
    }

    @Test
    public void requestListenerDoesntWorkWithNonHttpRequests() throws Exception {
        ServletRequest mockServletRequest = mock(ServletRequest.class);
        when(mockServletRequestEvent.getServletRequest()).thenReturn(mockServletRequest);

        sentryServletRequestListener.requestInitialized(mockServletRequestEvent);

        assertThat(SentryServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerDestroyRemovesTheCurrentRequest() throws Exception {
        HttpServletRequest mockHttpServletRequest = mock(HttpServletRequest.class);
        when(mockServletRequestEvent.getServletRequest()).thenReturn(mockHttpServletRequest);

        sentryServletRequestListener.requestInitialized(mockServletRequestEvent);

        sentryServletRequestListener.requestDestroyed(mockServletRequestEvent);

        assertThat(SentryServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerSpecificToLocalThread() throws Exception {
        HttpServletRequest mockHttpServletRequest = mock(HttpServletRequest.class);
        when(mockServletRequestEvent.getServletRequest()).thenReturn(mockHttpServletRequest);

        new Thread() {
            @Override
            public void run() {
                sentryServletRequestListener.requestInitialized(mockServletRequestEvent);
            }
        }.start();

        assertThat(SentryServletRequestListener.getServletRequest(), is(nullValue()));
    }
}
