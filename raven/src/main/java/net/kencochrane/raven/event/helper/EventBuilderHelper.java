package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;

/**
 * Helper allowing to add extra information to the {@link EventBuilder} before creating the
 * {@link net.kencochrane.raven.event.Event} itself.
 */
public interface EventBuilderHelper {
    void helpBuildingEvent(EventBuilder eventBuilder);
}
