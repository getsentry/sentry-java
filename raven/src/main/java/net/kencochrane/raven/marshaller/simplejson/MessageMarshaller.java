package net.kencochrane.raven.marshaller.simplejson;

import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.json.simple.JSONObject;

class MessageMarshaller implements InterfaceMarshaller {
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";

    @Override
    public JSONObject serialiseInterface(SentryInterface sentryInterface) {
        if (!(sentryInterface instanceof MessageInterface)) {
            //TODO: Do something better here!
            throw new IllegalArgumentException();
        }

        MessageInterface messageInterface = (MessageInterface) sentryInterface;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(MESSAGE_PARAMETER, messageInterface.getMessage());
        jsonObject.put(PARAMS_PARAMETER, messageInterface.getParams());
        return jsonObject;
    }
}
