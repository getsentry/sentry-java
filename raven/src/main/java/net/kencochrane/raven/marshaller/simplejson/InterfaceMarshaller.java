package net.kencochrane.raven.marshaller.simplejson;

import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.json.simple.JSONObject;

interface InterfaceMarshaller {
    JSONObject serialiseInterface(SentryInterface sentryInterface);
}
