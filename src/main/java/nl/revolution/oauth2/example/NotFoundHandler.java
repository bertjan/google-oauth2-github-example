package nl.revolution.oauth2.example;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class NotFoundHandler extends AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NotFoundHandler.class);
    public static final String RESPONSE_BODY_404 = "404 - Not found.";

    public void handle(String target, Request baseRequest, HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {
        LOG.warn("404 for {}", baseRequest.getRequestURI());
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().write(RESPONSE_BODY_404);
        baseRequest.setHandled(true);
    }

}