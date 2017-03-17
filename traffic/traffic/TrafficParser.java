package traffic;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
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
    private static final Gson gson = new Gson();
    private static final JsonParser json = new JsonParser();
    
    private enum SourceType {
        DETAILS(detailsUrl, "details.dat"),
        DATA(dataUrl, "data.dat");
        
        private String url;
        private String fileName;
        
        SourceType(String url, String debuggingFile) {
            this.url = url;
            this.fileName = debuggingFile;
        }
        
        public String getUrl() {
            return this.url;
        }
        
        public String getDebuggingFile() {
            return this.fileName;
        }
    }
    
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
        TrafficDataReader details = createGetter(SourceType.DETAILS, this.logger)
                .setPersist(true)
                .init();
        
        TrafficDataReader data = createGetter(SourceType.DATA, this.logger)
                .init();
        
        if (details != null) {
            details.get();
        }
        
        if (data != null) {
            data.get();
        }
        
        SensorLocationNameMapExtractor locationMapByRoadSection = SensorLocationNameMapExtractor.create(details);
        TrafficDetails dataParser = TrafficDetails.create(data);
        
        LinkedHashMap<Integer, String> trafficMetadataMap = locationMapByRoadSection.parse();
        LinkedHashMap<Integer, TrafficDatum> trafficDataMap = dataParser.parse();
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
    
    private enum JSONType {
        OBJECT,
        ARRAY,
        UNKNOWN;
        
        public static JSONType getTypeFromString(String value) {
            if (value.startsWith("{")) {
                return OBJECT;
            } else if (value.startsWith("[")) {
                return ARRAY;
            }
            return UNKNOWN;
        }
    }
    
    private TrafficDataReader createGetter(SourceType type, AutoflushingLogger logger) {
        if (type == SourceType.DETAILS) {
            return FileDataReader.create("static-js-resources", logger);
        } else {
            return WebDataReader.create(type.getUrl(), logger);
        }
    }
    
    /**
     * The traffic details is a JSONObject containing speed and incident information
     */
    private static class TrafficDetails {
        private Splitter dataSplitter = Splitter.on(";");
        private Joiner dashJoiner = Joiner.on("-");
        private TrafficDataReader data;
        
        TrafficDetails(TrafficDataReader detailsGetter) {
            this.data = detailsGetter;
        }
        
        static TrafficDetails create(TrafficDataReader detailsGetter) {
            return new TrafficDetails(detailsGetter);
        }
        
        LinkedHashMap<Integer, TrafficDatum> parse() {
            LinkedHashMap<Integer, TrafficDatum> map = initializeTrafficDataMap();
            Preconditions.checkState(map.size() == roadSections.size());
            try {
                data.initReader();
                while (data.hasNext()) {
                    String line = data.nextLine();
                    Iterable<String> fields = dataSplitter.split(line);
                    for (String field : fields) {
                        JSONType type = JSONType.getTypeFromString(field);
                        
                        // JSON Object should contain 'speeds' and 'incidents' JSONArrays
                        if (type == JSONType.OBJECT) {
                            JSONObject jsonObject = new JSONObject(field);
                            @SuppressWarnings("unchecked")
                            Iterator<Object> keys = jsonObject.keys();
                            while (keys.hasNext()) {
                                String keyName = (String) keys.next();
                                JSONArray jsonArray = jsonObject.getJSONArray(keyName);
                                if (keyName.equals(SPEEDS)) {
                                    roadSections.stream()
                                            .filter(i -> !jsonArray.isNull(i))
                                            .forEach(i -> {
                                                        JSONArray values = jsonArray.getJSONArray(i);
                                                        map.get(i).setSpeed(values.getInt(0));
                                                    }
                                            );
                                }
                                if (keyName.equals(INCIDENTS)) {
                                    for (int i = 0; i < jsonArray.length(); ++i) {
                                        JSONArray values = jsonArray.getJSONArray(i);
                                        int roadSectionNum = values.getInt(0);
                                        if (roadSections.contains(roadSectionNum)) {
                                            String roadCondition = dashJoiner.join(values.getString(2), values.getString(3), values.getString(4));
                                            map.get(roadSectionNum).setIncident(roadCondition);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                data.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return map;
        }
        
        private LinkedHashMap<Integer, TrafficDatum> initializeTrafficDataMap() {
            LinkedHashMap<Integer, TrafficDatum> map = Maps.newLinkedHashMap();
            for (Integer i : roadSections) {
                map.put(i, new TrafficDatum());
            }
            return map;
        }
    }
    
    /**
     * The metadata from sigalert.com contains several JSON structures (objects and arrays).
     * Split out the objects into a String,String map
     */
    private static class SensorLocationNameMapExtractor {
        private TrafficDataReader data;
        private Splitter semicolonSplitter = Splitter.on(";");
        private Splitter equalsSplitter = Splitter.on("=");
        
        private SensorLocationNameMapExtractor(TrafficDataReader metadataGetter) {
            this.data = metadataGetter;
        }
        
        static SensorLocationNameMapExtractor create(TrafficDataReader metadataGetter) {
            return new SensorLocationNameMapExtractor(metadataGetter);
        }
        
        /**
         * Returns a LinkedHashMap to preserve the order, which is in turn based on
         * the order of roadSection ids
         */
        LinkedHashMap<Integer, String> parse() {
            LinkedHashMap<Integer, String> map = Maps.newLinkedHashMap();
            try {
                data.initReader();
                while (data.hasNext()) {
                    String line = data.nextLine();
                    Iterable<String> lineParts = semicolonSplitter.split(line);
                    for (String segment : lineParts) {
                        List<String> segmentParts = equalsSplitter.splitToList(segment);
                        if (segmentParts.size() < 2) continue;
                        if (segmentParts.get(0).endsWith(SENSOR_NAMES)) {
                            JsonElement element = json.parse(segmentParts.get(1));
                            map = roadSections
                                    .stream()
                                    .collect(Collectors.toMap(Function.identity(), i -> element.getAsJsonArray().get(i).toString(),
                                             (u,v) -> { throw new IllegalStateException("Dup. key val " + u); },
                                             LinkedHashMap::new));
    
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return map;
        }
        
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
