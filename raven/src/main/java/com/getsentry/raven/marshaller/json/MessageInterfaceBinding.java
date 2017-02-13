package com.getsentry.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.getsentry.raven.event.interfaces.MessageInterface;

import java.io.IOException;

/**
 * Binding allowing to transform a {@link MessageInterface} into a JSON stream.
 */
public class MessageInterfaceBinding implements InterfaceBinding<MessageInterface> {
    /**
     * Default maximum length for a message.
     */
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 1000;
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";
    private static final String FORMATTED_PARAMETER = "formatted";

    /**
     * Maximum length for a message.
     */
    private final int maxMessageLength;

    /**
     * Create instance of MessageInterfaceBinding with default message length.
     */
    public MessageInterfaceBinding() {
        maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
    }

    /**
     * Create instance of MessageInterfaceBinding with provided the maximum length of the messages.
     *
     * @param maxMessageLength the maximum message length
     */
    public MessageInterfaceBinding(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    /**
     * Trims a message, ensuring that the maximum length {@link #maxMessageLength} isn't reached.
     *
     * @param message message to format.
     * @return trimmed message (shortened if necessary).
     */
    private String trimMessage(String message) {
        if (message == null) {
            return null;
        } else if (message.length() > maxMessageLength) {
            return message.substring(0, maxMessageLength);
        } else {
            return message;
        }
    }

    @Override
    public void writeInterface(JsonGenerator generator, MessageInterface messageInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(MESSAGE_PARAMETER, trimMessage(messageInterface.getMessage()));
        generator.writeArrayFieldStart(PARAMS_PARAMETER);
        for (String parameter : messageInterface.getParameters()) {
            generator.writeString(parameter);
        }
        generator.writeEndArray();
        if (messageInterface.getFormatted() != null) {
            generator.writeStringField(FORMATTED_PARAMETER, trimMessage(messageInterface.getFormatted()));
        }
        generator.writeEndObject();
    }
}
