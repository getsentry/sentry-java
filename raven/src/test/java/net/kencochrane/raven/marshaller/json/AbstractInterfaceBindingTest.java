package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class AbstractInterfaceBindingTest {
    private JsonFactory jsonFactory;
    private ObjectMapper mapper;
    private ByteArrayOutputStream jsonContentStream;

    @BeforeMethod
    protected void setUp() throws Exception {
        jsonFactory = new JsonFactory();
        mapper = new ObjectMapper();
    }

    protected JsonGenerator getJsonGenerator() throws IOException {
        jsonContentStream = new ByteArrayOutputStream();
        return jsonFactory.createGenerator(jsonContentStream);
    }

    protected JsonParser getJsonParser() throws IOException {
        return jsonFactory.createParser(jsonContentStream.toByteArray());
    }

    protected ObjectMapper getMapper() {
        return mapper;
    }
}
