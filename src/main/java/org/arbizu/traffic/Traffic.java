package org.arbizu.traffic;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

/**
 * Read traffic data for 92-E from data source
 *
 * @author alan
 */
class Traffic {
    
    private static final int HTTP_PORT = 8888;
    private static final int MAX_QUEUE_SIZE = 32;
    private static final String LOG_FILE_NAME = "trafficApp.log";
    private final AutoflushingLogger logger;
    
    private Traffic() {
        Logger l = Logger.getLogger(this.getClass().getName());
        this.logger = new AutoflushingLogger(l, this.getClass().getName(), LOG_FILE_NAME);
    }
    
    ByteArrayOutputStream process() {
        DataSource source = new DataSource(logger);
        return source.collect();
    }
    
    public static void main(String... args) {
        Traffic traffic = new Traffic();
        Checker trafficChecker = Checker.create(traffic);
        TrafficLoggerTask.createAndSchedule(15, TimeUnit.MINUTES, trafficChecker);
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(HTTP_PORT), MAX_QUEUE_SIZE);
            server.createContext("/t", TrafficRequestHandler.create(trafficChecker));
            server.createContext("/r", TrafficHistoryFileRequestHandler.create());
            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}
