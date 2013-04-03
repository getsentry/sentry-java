package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.LoggedEvent;

public interface Connection {
    void send(LoggedEvent event);
}
