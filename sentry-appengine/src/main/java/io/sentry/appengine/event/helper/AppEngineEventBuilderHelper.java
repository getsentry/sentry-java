package io.sentry.appengine.event.helper;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;

/**
 * EventBuildHelper defining Google App Engine specific properties (hostname).
 */
public class AppEngineEventBuilderHelper implements EventBuilderHelper {
    /**
     * Property used internally by GAE to define the hostname.
     *
     * @see <a href="https://developers.google.com/appengine/docs/java/appidentity/">GAE: App Identity Java API</a>
     */
    private static final String CURRENT_VERSION_HOSTNAME_PROPERTY =
        "com.google.appengine.runtime.default_version_hostname";

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
        // Set the hostname to the actual application hostname
        eventBuilder.withServerName((String) env.getAttributes().get(CURRENT_VERSION_HOSTNAME_PROPERTY));

        eventBuilder.withTag("GAE Application Version", SystemProperty.applicationVersion.get());
        eventBuilder.withTag("GAE Application Id", SystemProperty.applicationId.get());
    }
}
