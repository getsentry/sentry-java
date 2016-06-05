package com.getsentry.raven.event.helper;

import com.getsentry.raven.Raven;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.EventBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@link EventBuilderHelper} that extracts and sends any data attached to the
 * provided {@link Raven}'s {@link com.getsentry.raven.RavenContext}.
 */
public class ContextBuilderHelper implements EventBuilderHelper {

    /**
     * Raven object where the RavenContext comes from.
     */
    private Raven raven;

    /**
     * {@link EventBuilderHelper} that extracts context data from the provided {@link Raven} client.
     *
     * @param raven Raven client which holds RavenContext to be used.
     */
    public ContextBuilderHelper(Raven raven) {
        this.raven = raven;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        Iterator<Breadcrumb> iter = raven.getContext().getBreadcrumbs();
        while (iter.hasNext()) {
            breadcrumbs.add(iter.next());
        }
        eventBuilder.withBreadcrumbs(breadcrumbs);
    }

}
