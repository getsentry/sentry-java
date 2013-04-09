package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.MessageInterface;

import java.io.IOException;

public class MessageInterfaceBinding implements InterfaceBinding<MessageInterface> {
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";

    @Override
    public void writeInterface(JsonGenerator generator, MessageInterface messageInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(MESSAGE_PARAMETER, messageInterface.getMessage());
        generator.writeArrayFieldStart(PARAMS_PARAMETER);
        for (String parameter : messageInterface.getParams()) {
            generator.writeString(parameter);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }
}
