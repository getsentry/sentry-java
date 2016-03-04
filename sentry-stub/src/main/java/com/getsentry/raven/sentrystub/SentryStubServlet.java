package com.getsentry.raven.sentrystub;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Simple API to access the sentry stub details.
 */
@WebServlet(name = "SentryStubServlet", displayName = "SentryStubServlet", urlPatterns = "/stub/*")
public class SentryStubServlet extends HttpServlet {
    private static final String COUNT_OPERATION = "count";
    private static final String CLEANUP_OPERATION = "cleanup";
    private SentryStub sentryStub = SentryStub.getInstance();
    private JsonFactory jsonFactory = new JsonFactory();

    public void getEventsCounter(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField("count", sentryStub.getEvents().size());
        generator.writeEndObject();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getPathInfo().substring(1);

        JsonGenerator jsonGenerator = jsonFactory.createGenerator(resp.getOutputStream());

        if (COUNT_OPERATION.equals(operation)) {
            getEventsCounter(jsonGenerator);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        jsonGenerator.close();
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getPathInfo().substring(1);

        if (CLEANUP_OPERATION.equals(operation)) {
            sentryStub.removeEvents();
        }
    }
}
