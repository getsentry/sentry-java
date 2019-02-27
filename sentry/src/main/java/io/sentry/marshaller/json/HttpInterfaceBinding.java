package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.HttpInterface;
import io.sentry.util.Util;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Binding system allowing to convert an {@link HttpInterface} into a JSON stream.
 */
public class HttpInterfaceBinding implements InterfaceBinding<HttpInterface> {
    /**
     * Maximum length for the HTTP request body.
     *
     * See <a href="https://docs.sentry.io/clientdev/data-handling/#variable-size">the SDK reference</a>
     * for more information.
     */
    public static final int MAX_BODY_LENGTH = 2048;

    private static final String URL = "url";
    private static final String METHOD = "method";
    private static final String DATA = "data";
    private static final String BODY = "body";
    private static final String QUERY_STRING = "query_string";
    private static final String COOKIES = "cookies";
    private static final String HEADERS = "headers";
    private static final String ENVIRONMENT = "env";
    private static final String ENV_REMOTE_ADDR = "REMOTE_ADDR";
    private static final String ENV_SERVER_NAME = "SERVER_NAME";
    private static final String ENV_SERVER_PORT = "SERVER_PORT";
    private static final String ENV_LOCAL_ADDR = "LOCAL_ADDR";
    private static final String ENV_LOCAL_NAME = "LOCAL_NAME";
    private static final String ENV_LOCAL_PORT = "LOCAL_PORT";
    private static final String ENV_SERVER_PROTOCOL = "SERVER_PROTOCOL";
    private static final String ENV_REQUEST_SECURE = "REQUEST_SECURE";
    private static final String ENV_REQUEST_ASYNC = "REQUEST_ASYNC";
    private static final String ENV_AUTH_TYPE = "AUTH_TYPE";
    private static final String ENV_REMOTE_USER = "REMOTE_USER";

    @Override
    public void writeInterface(JsonGenerator generator, HttpInterface httpInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(URL, httpInterface.getRequestUrl());
        generator.writeStringField(METHOD, httpInterface.getMethod());
        generator.writeFieldName(DATA);
        writeData(generator, httpInterface.getParameters(), httpInterface.getBody());
        generator.writeStringField(QUERY_STRING, httpInterface.getQueryString());
        generator.writeFieldName(COOKIES);
        writeCookies(generator, httpInterface.getCookies());
        generator.writeFieldName(HEADERS);
        writeHeaders(generator, httpInterface.getHeaders());
        generator.writeFieldName(ENVIRONMENT);
        writeEnvironment(generator, httpInterface);
        generator.writeEndObject();
    }

    private void writeEnvironment(JsonGenerator generator, HttpInterface httpInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(ENV_REMOTE_ADDR, httpInterface.getRemoteAddr());
        generator.writeStringField(ENV_SERVER_NAME, httpInterface.getServerName());
        generator.writeNumberField(ENV_SERVER_PORT, httpInterface.getServerPort());
        generator.writeStringField(ENV_LOCAL_ADDR, httpInterface.getLocalAddr());
        generator.writeStringField(ENV_LOCAL_NAME, httpInterface.getLocalName());
        generator.writeNumberField(ENV_LOCAL_PORT, httpInterface.getLocalPort());
        generator.writeStringField(ENV_SERVER_PROTOCOL, httpInterface.getProtocol());
        generator.writeBooleanField(ENV_REQUEST_SECURE, httpInterface.isSecure());
        generator.writeBooleanField(ENV_REQUEST_ASYNC, httpInterface.isAsyncStarted());
        generator.writeStringField(ENV_AUTH_TYPE, httpInterface.getAuthType());
        //TODO: Should that be really displayed here ? Consider the user interface?
        generator.writeStringField(ENV_REMOTE_USER, httpInterface.getRemoteUser());
        generator.writeEndObject();
    }

    private void writeHeaders(JsonGenerator generator, Map<String, Collection<String>> headers) throws IOException {
        generator.writeStartArray();
        for (Map.Entry<String, Collection<String>> headerEntry : headers.entrySet()) {
            for (String value : headerEntry.getValue()) {
                generator.writeStartArray();
                generator.writeString(headerEntry.getKey());
                generator.writeString(value);
                generator.writeEndArray();
            }
        }
        generator.writeEndArray();
    }

    private void writeCookies(JsonGenerator generator, Map<String, String> cookies) throws IOException {
        //TODO: Cookies shouldn't be sent by default
        if (cookies.isEmpty()) {
            generator.writeNull();
            return;
        }

        generator.writeStartObject();
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            generator.writeStringField(cookie.getKey(), cookie.getValue());
        }
        generator.writeEndObject();
    }

    private void writeData(JsonGenerator generator,
                           Map<String, Collection<String>> parameterMap,
                           String body) throws IOException {
        if (parameterMap == null && body == null) {
            generator.writeNull();
            return;
        }

        if ((parameterMap == null || parameterMap.isEmpty()) && body != null) {
            generator.writeString(Util.trimString(body, MAX_BODY_LENGTH));
        } else {
            generator.writeStartObject();
            if (body != null) {
                generator.writeStringField(BODY, Util.trimString(body, MAX_BODY_LENGTH));
            }
            if (parameterMap != null) {
                for (Map.Entry<String, Collection<String>> parameter : parameterMap.entrySet()) {
                    generator.writeArrayFieldStart(parameter.getKey());
                    for (String parameterValue : parameter.getValue()) {
                        generator.writeString(parameterValue);
                    }
                    generator.writeEndArray();
                }
            }
            generator.writeEndObject();
        }
    }
}
