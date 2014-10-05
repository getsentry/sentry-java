package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.UserInterface;

import java.io.IOException;

/**
 * Binding allowing to transform a {@link UserInterface} into a JSON stream.
 */
public class UserInterfaceBinding implements InterfaceBinding<UserInterface> {
    private static final String ID = "id";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final String IP_ADDRESS = "ip_address";

    @Override
    public void writeInterface(JsonGenerator generator, UserInterface userInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(ID, userInterface.getId());
        generator.writeStringField(USERNAME, userInterface.getUsername());
        generator.writeStringField(EMAIL, userInterface.getEmail());
        generator.writeStringField(IP_ADDRESS, userInterface.getIpAddress());
        generator.writeEndObject();
    }
}
