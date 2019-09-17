package io.sentry.appengine.event.helper;

import static java.util.Collections.singletonMap;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import io.sentry.event.EventBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

public class AppEngineEventBuilderHelperTest {
    private AppEngineEventBuilderHelper eventBuilderHelper = null;
    private EventBuilder mockEventBuilder = null;
    private ApiProxy.Environment mockEnvironment = null;
    private String originalApplicationId;
    private String originalApplicationVersion;

    @Before
    public void setUp() throws Exception {
        originalApplicationId = SystemProperty.applicationId.get();
        originalApplicationVersion = SystemProperty.applicationVersion.get();

        mockEventBuilder = mock(EventBuilder.class);

        mockEnvironment = mock(ApiProxy.Environment.class);
        when(mockEnvironment.getAttributes()).thenReturn(Collections.<String, Object>emptyMap());

        ApiProxy.setEnvironmentForCurrentThread(mockEnvironment);

        eventBuilderHelper = new AppEngineEventBuilderHelper();
    }

    @After
    public void tearDown() {
        ApiProxy.setEnvironmentForCurrentThread(null);
        resetSystemProperty(SystemProperty.applicationId, originalApplicationId);
        resetSystemProperty(SystemProperty.applicationVersion, originalApplicationVersion);
    }

    private static void resetSystemProperty(SystemProperty property, String value) {
        if (value == null) {
            System.clearProperty(property.key());
        } else {
            property.set(value);
        }
    }

    @Test
    public void ensureHostnameDefineByApiProxyEnvironment() throws Exception {
        Object mockHostName = "d7b8f251-ebe1-446f-8549-2b37982bd548";
        when(mockEnvironment.getAttributes())
                .thenReturn(singletonMap("com.google.appengine.runtime.default_version_hostname",
                        mockHostName));

        eventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        ArgumentCaptor<String> hostnameCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockEventBuilder).withServerName(hostnameCaptor.capture());
        String hostname = hostnameCaptor.getValue();
        assertThat(hostname, is(mockHostName));
    }

    @Test
    public void ensureApplicationVersionIsAddedAsTag() throws Exception {
        String version = "dc485fa3-fc23-4e6c-b374-0d05d248e5a5";
        SystemProperty.applicationVersion.set(version);

        eventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        verify(mockEventBuilder).withTag(eq("GAE Application Version"), eq(version));
    }

    @Test
    public void ensureApplicationIdIsAddedAsTag() throws Exception {
        String applicationId = "50a180eb-1484-4a07-9e44-b60d394cad18";
        SystemProperty.applicationId.set(applicationId);

        eventBuilderHelper.helpBuildingEvent(mockEventBuilder);

        verify(mockEventBuilder).withTag(eq("GAE Application Id"), eq(applicationId));
    }
}
