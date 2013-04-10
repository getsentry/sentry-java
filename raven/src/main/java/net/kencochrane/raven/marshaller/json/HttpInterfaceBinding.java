package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.HttpInterface;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class HttpInterfaceBinding implements InterfaceBinding<HttpInterface> {
    private static final String URL = "url";
    private static final String METHOD = "method";
    private static final String DATA = "data";
    private static final String QUERY_STRING = "query_string";
    private static final String COOKIES = "cookies";
    private static final String HEADERS = "headers";
    private static final String ENVIRONMENT = "env";
    private static final String REMOTE_ADDR = "REMOTE_ADDR";
    private static final String SERVER_NAME = "SERVER_NAME";
    private static final String SERVER_PORT = "SERVER_PORT";
    private static final String SERVER_PROTOCOL = "SERVER_PROTOCOL";

    @Override
    public void writeInterface(JsonGenerator generator, HttpInterface httpInterface) throws IOException {
        HttpServletRequest request = httpInterface.getRequest();

        generator.writeStartObject();
        generator.writeStringField(URL, request.getRequestURL().toString());
        generator.writeStringField(METHOD, request.getMethod());
        generator.writeFieldName(DATA);
        writeData(generator, request.getParameterMap());
        generator.writeStringField(QUERY_STRING, request.getQueryString());
        generator.writeFieldName(COOKIES);
        writeCookies(generator, request.getCookies());
        generator.writeFieldName(HEADERS);
        writeHeaders(generator, request);
        generator.writeFieldName(ENVIRONMENT);
        writeEnvironment(generator, request);
        generator.writeEndObject();
    }

    private void writeEnvironment(JsonGenerator generator, HttpServletRequest request) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(REMOTE_ADDR, request.getRemoteAddr());
        generator.writeStringField(SERVER_NAME, request.getServerName());
        generator.writeNumberField(SERVER_PORT, request.getServerPort());
        generator.writeStringField(SERVER_PROTOCOL, request.getProtocol());
        generator.writeEndObject();
    }

    private void writeHeaders(JsonGenerator generator, HttpServletRequest request) throws IOException {
        generator.writeStartObject();
        for (String header : Collections.list(request.getHeaderNames())) {
            generator.writeArrayFieldStart(header);
            for (String headerValue : request.getParameterValues(header)) {
                generator.writeString(headerValue);
            }
            generator.writeEndArray();
        }
        generator.writeEndObject();
    }

    private void writeCookies(JsonGenerator generator, Cookie[] cookies) throws IOException {
        if (cookies == null) {
            generator.writeNull();
            return;
        }

        generator.writeStartObject();
        for (Cookie cookie : cookies) {
            generator.writeStringField(cookie.getName(), cookie.getValue());
        }
        generator.writeStartObject();
    }

    private void writeData(JsonGenerator generator, Map<String, String[]> parameterMap) throws IOException {
        if (parameterMap == null) {
            generator.writeNull();
            return;
        }

        generator.writeStartObject();
        for (Map.Entry<String, String[]> parameter : parameterMap.entrySet()) {
            generator.writeArrayFieldStart(parameter.getKey());
            for (String parameterValue : parameter.getValue()) {
                generator.writeString(parameterValue);
            }
            generator.writeEndArray();
        }
        generator.writeEndObject();
    }
}
