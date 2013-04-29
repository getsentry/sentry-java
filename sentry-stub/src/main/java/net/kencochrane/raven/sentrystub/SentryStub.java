package net.kencochrane.raven.sentrystub;

import net.kencochrane.raven.sentrystub.auth.AuthValidator;
import net.kencochrane.raven.sentrystub.event.Event;
import net.kencochrane.raven.sentrystub.unmarshaller.JsonUnmarshaller;
import net.kencochrane.raven.sentrystub.unmarshaller.Unmarshaller;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

public final class SentryStub {
    private static SentryStub instance = new SentryStub();
    private final Collection<Event> events = new LinkedList<Event>();
    private final AuthValidator authValidator = new AuthValidator();
    private final Unmarshaller unmarshaller = new JsonUnmarshaller();

    private SentryStub() {
        authValidator.loadSentryUsers("/net/kencochrane/raven/sentrystub/sentry.properties");
    }

    public static SentryStub getInstance() {
        return instance;
    }

    public void addEvent(Event event) {
        validateEvent(event);
        events.add(event);
    }

    public void validateEvent(Event event) {
    }

    public Event parseEvent(InputStream source) {
        return unmarshaller.unmarshall(source);
    }

    public Collection<Event> getEvents() {
        return Collections.unmodifiableCollection(events);
    }

    public void validateAuth(Map<String, String> authHeader, String projectId) {
        authValidator.validateSentryAuth(authHeader, projectId);
    }

    public void validateAuth(Map<String, String> authHeader) {
        authValidator.validateSentryAuth(authHeader);
    }

    public void removeEvents() {
        events.clear();
    }
}
