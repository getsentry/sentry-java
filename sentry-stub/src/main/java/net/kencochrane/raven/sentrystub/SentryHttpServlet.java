package net.kencochrane.raven.sentrystub;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.kencochrane.raven.sentrystub.event.Event;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

@WebServlet(name = "SentryHttpServlet", displayName = "SentryHttpServlet", urlPatterns = "/api/*")
public class SentryHttpServlet extends HttpServlet {
    private JsonDecoder jsonDecoder = new JsonDecoder();
    private ObjectMapper om = new ObjectMapper();

    public SentryHttpServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        InputStream jsonStream = jsonDecoder.decapsulateContent(req.getInputStream());
        Event e = om.readValue(jsonStream, Event.class);
    }
}
