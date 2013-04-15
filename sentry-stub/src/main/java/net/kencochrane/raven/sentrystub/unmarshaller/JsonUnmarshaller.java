package net.kencochrane.raven.sentrystub.unmarshaller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.kencochrane.raven.sentrystub.event.Event;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonUnmarshaller implements Unmarshaller {
    private static final Logger logger = Logger.getLogger(JsonUnmarshaller.class.getCanonicalName());
    private JsonDecoder jsonDecoder = new JsonDecoder();
    private ObjectMapper om = new ObjectMapper();

    @Override
    public Event unmarshall(InputStream source) {
        Event event = null;
        try {
            InputStream jsonStream = jsonDecoder.decapsulateContent(source);
            event = om.readValue(jsonStream, Event.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't parse some JSON content.", e);
        }
        return event;
    }
}
