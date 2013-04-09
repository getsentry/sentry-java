package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.MessageInterface;

import java.io.IOException;

public class MessageInterfaceBinding implements InterfaceBinding<MessageInterface> {
    /**
     * Maximum length for a message.
     */
    public static final int MAX_MESSAGE_LENGTH = 1000;
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";

    /**
     * Formats a message, ensuring that the maximum length {@link #MAX_MESSAGE_LENGTH} isn't reached.
     *
     * @param message message to format.
     * @return formatted message (shortened if necessary).
     */
    private String formatMessage(String message) {
        if (message == null)
            return null;
        else if (message.length() > MAX_MESSAGE_LENGTH)
            return message.substring(0, MAX_MESSAGE_LENGTH);
        else return message;
    }

    @Override
    public void writeInterface(JsonGenerator generator, MessageInterface messageInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(MESSAGE_PARAMETER, formatMessage(messageInterface.getMessage()));
        generator.writeArrayFieldStart(PARAMS_PARAMETER);
        for (String parameter : messageInterface.getParams()) {
            generator.writeString(parameter);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }
}
