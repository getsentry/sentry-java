package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.SentryInterface;

import java.io.IOException;

public interface InterfaceBinding<T extends SentryInterface> {
    void writeInterface(JsonGenerator generator, T sentryInterface) throws IOException;
}
