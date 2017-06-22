package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.UserInterface;

import java.io.IOException;
import java.util.Map;

/**
 * Binding allowing to transform a {@link UserInterface} into a JSON stream.
 */
public class UserInterfaceBinding implements InterfaceBinding<UserInterface> {
    private static final String ID = "id";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final String IP_ADDRESS = "ip_address";
    private static final String DATA = "data";

    @Override
    public void writeInterface(JsonGenerator generator, UserInterface userInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(ID, userInterface.getId());
        generator.writeStringField(USERNAME, userInterface.getUsername());
        generator.writeStringField(EMAIL, userInterface.getEmail());
        generator.writeStringField(IP_ADDRESS, userInterface.getIpAddress());

        if (userInterface.getData() != null && !userInterface.getData().isEmpty()) {
            generator.writeObjectFieldStart(DATA);
            for (Map.Entry<String, Object> entry : userInterface.getData().entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    generator.writeNullField(name);
                } else {
                    generator.writeObjectField(name, value);
                }
            }
            generator.writeEndObject();
        }

        generator.writeEndObject();
    }
}
