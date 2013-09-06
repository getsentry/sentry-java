package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;

/**
 * Helper allowing to add extra information to the {@link EventBuilder} before creating the
 * {@link net.kencochrane.raven.event.Event} itself.
 */
public interface EventBuilderHelper {
    /**
     * Adds extra elements to the {@link EventBuilder} before calling {@link EventBuilder#build()}.
     * <p>
     * EventBuilderHelper are supposed to only add details to the Event before it's built. Calling the
     * {@link EventBuilder#build()} method from the helper will prevent the event from being built properly.
     * </p>
     *
     * @param eventBuilder event builder to enhance before the event is built.
     */
    void helpBuildingEvent(EventBuilder eventBuilder);
}
