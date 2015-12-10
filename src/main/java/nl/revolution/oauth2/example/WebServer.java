package nl.revolution.oauth2.example;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlets.gzip.GzipHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);
    private static final int HTTP_PORT = 8080;

    public Server createServer() {
        HandlerList webHandlers = new HandlerList();
        webHandlers.addHandler(new RequestHandler());
        webHandlers.addHandler(new NotFoundHandler());
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(webHandlers);

        Server webServer = new Server(HTTP_PORT);
        webServer.setHandler(gzipHandler);

        LOG.info("Webserver created, listening on port {}", HTTP_PORT);
        return webServer;
    }


    public static void main(String... args) throws Exception {
        configureLogging();

        Server webServer = new WebServer().createServer();
        webServer.start();

        LOG.info("Webserver running. Press enter to quit.");
        System.in.read();

        webServer.stop();
    }

    private static void configureLogging() {
        LoggerContext logConfig = (LoggerContext) LoggerFactory.getILoggerFactory();
        logConfig.getLogger("ROOT").setLevel(Level.INFO);
        logConfig.getLogger("org.eclipse.jetty").setLevel(Level.INFO);
        logConfig.getLogger("org.eclipse.jetty.server.handler.ContextHandler").setLevel(Level.ERROR);
        logConfig.getLogger("nl.revolution").setLevel(Level.DEBUG);
    }


}
