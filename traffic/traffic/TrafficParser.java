package traffic;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.*;
import org.json.JSONArray;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.net.httpserver.HttpServer;

/**
 * Read traffic data for 92-E from sigalert.com data structures.
 *
 * @author alan
 */
public class TrafficParser {
    
    private static final int HTTP_PORT = 8888;
    /*
     * Road Section indexes for 92-E in sigalert data:
     * 2645: Ralston
     * 2646: De Anza Blvd
     * 2647: Hillsdale
     * 2648: Alameda de las Pulgas
     * 2649: El Camino (CA Hwy 82)
     * 2650: Delaware
     * 2651: US Hwy 101
     * 2652: Mariners Island
     * 2653: Foster City Blvd
     * 2654: Entr. San Mateo Bridge
     */
    private static final String DEBUG_PARAM = "debug";
    private static final String LOG_FILE_NAME = "trafficApp.log";
    private static boolean DEBUGGING;
    private static final int MAX_QUEUE_SIZE = 32;
    private final static String SENSOR_NAMES = "SensorNames";
    private final static String SPEEDS = "speeds";
    private final static String INCIDENTS = "incidents";
    private static String detailsUrl = "http://cdn-static.sigalert.com/154/Zip/RegionInfo/NoCalStatic.js";
    private static String dataUrl = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
    private static ArrayList<Integer> roadSections = Lists.newArrayList(2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654);
    private final AutoflushingLogger logger;
    private static final JsonParser json = new JsonParser();
    
    private TrafficParser() {
        Logger l = Logger.getLogger(this.getClass().getName());
        this.logger = new AutoflushingLogger(l, this.getClass().getName(), LOG_FILE_NAME);
    }
    
    JSONArray process() {
        JSONArray trafficSummary = getTrafficSummary();
        debugOutput(trafficSummary);
        return trafficSummary;
    }
    
    private void debugOutput(JSONArray data) {
        if (!TrafficParser.isDebugging()) return;
        try (
                FileWriter fw = new FileWriter("analyzed.data");
                BufferedWriter writer = new BufferedWriter(fw)
        ) {
            writer.write(data.toString(2));
            writer.newLine();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
    
    private static boolean isDebugging() {
        return DEBUGGING;
    }
    
    private JSONArray getTrafficSummary() {
        DataSource source = new DataSource(logger);
        // TODO -- this method should now return the json array
        LinkedHashMap<String, String> map = source.collect();
    
        LinkedHashMap<Integer, String> trafficMetadataMap = new LinkedHashMap<>();
        LinkedHashMap<Integer, TrafficDatum> trafficDataMap = new LinkedHashMap<>();
        dispatchLogging(trafficDataMap);
        LinkedHashMap<String, String> dataMap = joinMaps(trafficMetadataMap, trafficDataMap);
        
        return getTrafficDataSummary(dataMap);
    }
    
    private void dispatchLogging(LinkedHashMap<Integer, TrafficDatum> trafficDataMap) {
        TrafficLogging.process(trafficDataMap);
    }
    
    private JSONArray getTrafficDataSummary(LinkedHashMap<String, String> dataMap) {
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, String> e : dataMap.entrySet()) {
            JSONArray subArray = new JSONArray();
            subArray.put(e.getKey());
            subArray.put(e.getValue());
            arr.put(subArray);
        }
        return arr;
    }
    
    private LinkedHashMap<String, String> joinMaps(LinkedHashMap<Integer, String> metadata, LinkedHashMap<Integer, TrafficDatum> data) {
        LinkedHashMap<String, String> retVal = Maps.newLinkedHashMap();
        for (Map.Entry<Integer, String> e : metadata.entrySet()) {
            retVal.put(e.getValue(), data.get(e.getKey()).toString());
        }
        return retVal;
    }
    
    public static void main(String... args) {
        // -Ddebug=true
        String isDebugSet = System.getProperty(DEBUG_PARAM);
        if (isDebugSet != null) {
            DEBUGGING = Boolean.valueOf(isDebugSet.toLowerCase());
        }
        TrafficParser trafficParser = new TrafficParser();
        Checker trafficChecker = Checker.create(trafficParser);
        TrafficLoggerTask.createAndSchedule(15, TimeUnit.MINUTES, trafficChecker);
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(HTTP_PORT), MAX_QUEUE_SIZE);
            server.createContext("/t", TrafficRequestHandler.create(trafficChecker));
            server.createContext("/r", TrafficHistoryFileRequestHandler.create());
            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
}
