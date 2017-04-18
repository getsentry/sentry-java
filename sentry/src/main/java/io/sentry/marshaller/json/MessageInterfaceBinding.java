package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.MessageInterface;
import io.sentry.util.Util;

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

    @Override
    public void writeInterface(JsonGenerator generator, MessageInterface messageInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(MESSAGE_PARAMETER, Util.trimString(messageInterface.getMessage(), maxMessageLength));
        generator.writeArrayFieldStart(PARAMS_PARAMETER);
        for (String parameter : messageInterface.getParameters()) {
            generator.writeString(parameter);
        }
        generator.writeEndArray();
        if (messageInterface.getFormatted() != null) {
            generator.writeStringField(FORMATTED_PARAMETER,
                Util.trimString(messageInterface.getFormatted(), maxMessageLength));
        }
        generator.writeEndObject();
    }
}
