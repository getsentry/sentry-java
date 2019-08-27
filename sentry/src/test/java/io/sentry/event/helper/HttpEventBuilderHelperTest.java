package io.sentry.event.helper;

import static java.util.Collections.emptyList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.security.Principal;
import java.util.Collections;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import io.sentry.BaseTest;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.HttpInterface;
import io.sentry.event.interfaces.SentryInterface;
import io.sentry.event.interfaces.UserInterface;
import io.sentry.servlet.SentryServletRequestListener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class HttpEventBuilderHelperTest extends BaseTest {
    private HttpEventBuilderHelper httpEventBuilderHelper;
    private EventBuilder mockEventBuilder = null;
    private ServletRequestEvent mockServletRequestEvent;
    private HttpServletRequest mockServletRequest;

    @Before
    public void setup() {
        httpEventBuilderHelper = new HttpEventBuilderHelper();
        mockEventBuilder = mock(EventBuilder.class);
        mockServletRequestEvent = mock(ServletRequestEvent.class);
        mockServletRequest = mock(HttpServletRequest.class);

        when(mockServletRequest.getRequestURL()).thenReturn(new StringBuffer("request"));
        when(mockServletRequest.getHeaderNames()).thenReturn(Collections.<String>emptyEnumeration());

        when(mockServletRequestEvent.getServletRequest()).thenReturn(mockServletRequest);
    }

    @Test
    public void testNoRequest() throws Exception {
        new SentryServletRequestListener().requestDestroyed(mockServletRequestEvent);
        httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        verify(mockEventBuilder, never()).withSentryInterface(any(SentryInterface.class), anyBoolean());
    }

    @Test
    public void testWithRequest() throws Exception {
        try {
            new SentryServletRequestListener().requestInitialized(mockServletRequestEvent);

            httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

            verify(mockEventBuilder).withSentryInterface(isA(HttpInterface.class), eq(false));
            verify(mockEventBuilder).withSentryInterface(isA(UserInterface.class), eq(false));
        } finally {
            new SentryServletRequestListener().requestDestroyed(null);
        }
    }

    @Test
    public void testWithUserPrincipal() throws Exception {
        try {
            new SentryServletRequestListener().requestInitialized(mockServletRequestEvent);

            Principal mockUserPrincipal = mock(Principal.class);
            when(mockUserPrincipal.getName()).thenReturn("93ad24e4-cad1-4214-af8e-2e48e76b02de");
            when(mockServletRequest.getUserPrincipal()).thenReturn(mockUserPrincipal);

            httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

            ArgumentCaptor<SentryInterface> ifaceCaptor = ArgumentCaptor.forClass(SentryInterface.class);

            verify(mockEventBuilder, times(2)).withSentryInterface(ifaceCaptor.capture(), eq(false));
            SentryInterface sentryInterface = ifaceCaptor.getAllValues().get(1);
            Assert.assertTrue(sentryInterface instanceof UserInterface);

            UserInterface userIface = (UserInterface) sentryInterface;

            Assert.assertEquals("93ad24e4-cad1-4214-af8e-2e48e76b02de", userIface.getUsername());
        } finally {
            new SentryServletRequestListener().requestDestroyed(mockServletRequestEvent);
        }
    }

    @Test
    public void testWithIpAddress() throws Exception {
        try {
            new SentryServletRequestListener().requestInitialized(mockServletRequestEvent);

            when(mockServletRequest.getRemoteAddr()).thenReturn("d90e92cc-1929-4f9e-a44c-3062a8b00c70");

            httpEventBuilderHelper.helpBuildingEvent(mockEventBuilder);

            ArgumentCaptor<SentryInterface> ifaceCaptor = ArgumentCaptor.forClass(SentryInterface.class);

            verify(mockEventBuilder, times(2)).withSentryInterface(ifaceCaptor.capture(), eq(false));
            SentryInterface sentryInterface = ifaceCaptor.getAllValues().get(1);
            Assert.assertTrue(sentryInterface instanceof UserInterface);

            UserInterface userIface = (UserInterface) sentryInterface;

            Assert.assertEquals("d90e92cc-1929-4f9e-a44c-3062a8b00c70", userIface.getIpAddress());
        } finally {
            new SentryServletRequestListener().requestDestroyed(mockServletRequestEvent);
        }
    }
}
