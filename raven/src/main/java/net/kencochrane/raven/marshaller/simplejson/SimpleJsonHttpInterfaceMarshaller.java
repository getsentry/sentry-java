package net.kencochrane.raven.marshaller.simplejson;

import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.json.simple.JSONObject;

public class SimpleJsonHttpInterfaceMarshaller implements SimpleJsonInterfaceMarshaller {
    @Override
    public JSONObject serialiseInterface(SentryInterface sentryInterface) {
        if (!(sentryInterface instanceof HttpInterface)) {
            //TODO: Do something better here!
            throw new IllegalArgumentException();
        }

        JSONObject jsonObject = new JSONObject();
        //TODO: make an actual implementation!
        return jsonObject;
    }
}
