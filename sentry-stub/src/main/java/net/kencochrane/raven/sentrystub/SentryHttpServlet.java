package net.kencochrane.raven.sentrystub;

import net.kencochrane.raven.sentrystub.event.Event;
import net.kencochrane.raven.sentrystub.unmarshaller.JsonUnmarshaller;
import net.kencochrane.raven.sentrystub.unmarshaller.Unmarshaller;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "SentryHttpServlet", displayName = "SentryHttpServlet", urlPatterns = "/api/*")
public class SentryHttpServlet extends HttpServlet {
    //TODO: Hardcoded now, but later it could be enhanced.
    private Unmarshaller unmarshaller = new JsonUnmarshaller();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Event event = unmarshaller.unmarshall(req.getInputStream());
        //TODO: validate event
    }
}
