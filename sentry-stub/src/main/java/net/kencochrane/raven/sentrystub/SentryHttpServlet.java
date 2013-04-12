package net.kencochrane.raven.sentrystub;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "SentryHttpServlet", displayName = "SentryHttpServlet", urlPatterns = "/api/*")
public class SentryHttpServlet extends HttpServlet {
    public SentryHttpServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("test");
    }
}
