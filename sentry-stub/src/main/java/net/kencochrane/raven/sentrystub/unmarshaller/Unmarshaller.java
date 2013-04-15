package net.kencochrane.raven.sentrystub.unmarshaller;

import net.kencochrane.raven.sentrystub.event.Event;

import java.io.InputStream;

public interface Unmarshaller {
    Event unmarshall(InputStream source);
}
