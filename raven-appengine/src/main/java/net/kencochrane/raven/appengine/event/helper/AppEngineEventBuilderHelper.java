package net.kencochrane.raven.appengine.event.helper;

import com.google.apphosting.api.ApiProxy;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;

/**
 * EventBuildHelper defining Google App Engine specific properties (hostname).
 */
public class AppEngineEventBuilderHelper implements EventBuilderHelper {
    /**
     * Property used internally by GAE to define the hostname.
     *
     * @see <a href="https://developers.google.com/appengine/docs/java/appidentity/">GAE: App Identity Java API</a>
     */
    private static final String CURRENT_VERSION_HOSTNAME_PROPERTY = "com.google.appengine.runtime.default_version_hostname";

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
        // Set the hostname to the actual application hostname
        eventBuilder.setServerName((String) env.getAttributes().get(CURRENT_VERSION_HOSTNAME_PROPERTY));
    }
}
