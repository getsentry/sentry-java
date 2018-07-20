package io.sentry.marshaller.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.BaseTest;
import io.sentry.marshaller.json.connector.JsonGenerator;
import io.sentry.marshaller.json.connector.JsonFactoryRuntimeClasspathLocator;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public final class JsonComparisonUtil extends BaseTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonComparisonUtil() {
    }

    public static JsonNode jsonResource(String resourcePath) throws Exception {
        return objectMapper.readTree(JsonComparisonUtil.class.getResourceAsStream(resourcePath));
    }

    public static JsonGeneratorParser newJsonGenerator() throws Exception {
        return new JsonGeneratorParser();
    }

    public static JsonOutputStreamParser newJsonOutputStream() throws Exception {
        return new JsonOutputStreamParser();
    }

    public static abstract class JsonStreamParser {
        protected final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        protected abstract void closeStream();

        public JsonNode value() throws Exception {
            closeStream();
            return objectMapper.readTree(outputStream.toByteArray());
        }

        @Override
        public String toString() {
            closeStream();
            try {
                return outputStream.toString("UTF-8");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class JsonGeneratorParser extends JsonStreamParser {
        private final JsonGenerator jsonGenerator;

        private JsonGeneratorParser() throws Exception {
            jsonGenerator = new JsonFactoryRuntimeClasspathLocator().getInstance().createGenerator(outputStream);
        }


        public JsonGenerator generator() {
            return jsonGenerator;
        }

        @Override
        protected void closeStream() {
            try {
                jsonGenerator.close();
                outputStream.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class JsonOutputStreamParser extends JsonStreamParser {

        public OutputStream outputStream() {
            return outputStream;
        }

        @Override
        protected void closeStream() {
            try {
                outputStream.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
