package com.getsentry.raven.servlet;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import static mockit.Deencapsulation.getField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class RavenServletRequestListenerTest {
    @Tested
    private RavenServletRequestListener ravenServletRequestListener = null;
    @Injectable
    private ServletRequestEvent mockServletRequestEvent = null;

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the threadLocal value
        ((ThreadLocal) getField(RavenServletRequestListener.class, "THREAD_REQUEST")).remove();
    }

    @Test
    public void requestListenerEmptyByDefault() throws Exception {
        assertThat(RavenServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerContainsTheCurrentRequest(@Injectable final HttpServletRequest mockHttpServletRequest)
            throws Exception {
        new NonStrictExpectations() {{
            mockServletRequestEvent.getServletRequest();
            result = mockHttpServletRequest;
        }};

        ravenServletRequestListener.requestInitialized(mockServletRequestEvent);

        assertThat(RavenServletRequestListener.getServletRequest(), is(mockHttpServletRequest));
    }

    @Test
    public void requestListenerDoesntWorkWithNonHttpRequests(@Injectable final ServletRequest mockServletRequest)
            throws Exception {
        new NonStrictExpectations() {{
            mockServletRequestEvent.getServletRequest();
            result = mockServletRequest;
        }};

        ravenServletRequestListener.requestInitialized(mockServletRequestEvent);

        assertThat(RavenServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerDestroyRemovesTheCurrentRequest(@Injectable final HttpServletRequest mockHttpServletRequest)
            throws Exception {
        new NonStrictExpectations() {{
            mockServletRequestEvent.getServletRequest();
            result = mockHttpServletRequest;
        }};
        ravenServletRequestListener.requestInitialized(mockServletRequestEvent);

        ravenServletRequestListener.requestDestroyed(mockServletRequestEvent);

        assertThat(RavenServletRequestListener.getServletRequest(), is(nullValue()));
    }

    @Test
    public void requestListenerSpecificToLocalThread(@Injectable final HttpServletRequest mockHttpServletRequest)
            throws Exception {
        new NonStrictExpectations() {{
            mockServletRequestEvent.getServletRequest();
            result = mockHttpServletRequest;
        }};

        new Thread() {
            @Override
            public void run() {
                ravenServletRequestListener.requestInitialized(mockServletRequestEvent);
            }
        }.start();

        assertThat(RavenServletRequestListener.getServletRequest(), is(nullValue()));
    }
}