package org.arbizu.traffic;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.*;
import java.util.*;
import java.util.logging.Level;

/**
 * @author alan
 * @since 3/19/2017
 */
class DataSource {
    private static final String DATA_URL = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
    private static final String METADATA_URL = "http://cdn-static.sigalert.com/154/Zip/RegionInfo/NoCalStatic.js";
    private static final ArrayList<Integer> roadSections = Lists.newArrayList(2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654);
    private final static String SPEEDS = "speeds";
    private final static String INCIDENTS = "incidents";
    private final static String SENSOR_NAMES = "SensorNames";
    private final Splitter semicolonSplitter = Splitter.on(";");
    private final Splitter equalsSplitter = Splitter.on("=");
    private final JsonParser json = new JsonParser();
    private final Gson gson;
    private final AutoflushingLogger logger;
    
    DataSource(AutoflushingLogger logger) {
        this.logger = logger;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(TrafficDatum.class, new TrafficDatumTypeAdapter());
        this.gson = builder.create();
    }
    
    enum SourceType {
        DATA(DATA_URL),
        METADATA(METADATA_URL, "/static-js-resources");
        
        private final String url;
        private final String fallbackFile;
    
        SourceType(String url) { this.url = url; this.fallbackFile = null;}
        SourceType(String url, String fallBackFile) { this.url = url; this.fallbackFile = fallBackFile; }
        public String getUrl() {  return this.url;  }
        public String getFallback() { return this.fallbackFile; }
    }
    
    private TrafficDataReader getReaderFor(SourceType type) {
        return WebDataReader.create(type, logger);
    }
    
    ByteArrayOutputStream collect() {
        TrafficDataReader dataReader = getReaderFor(SourceType.DATA).init().read();
        TrafficDataReader metadataReader = getReaderFor(SourceType.METADATA).init().read();
    
        LinkedHashMap<Integer,TrafficDatum> data = new LinkedHashMap<>();
        try {
            dataReader.initReader();
            while (dataReader.hasNext()) {
                String line = dataReader.nextLine();
                JsonElement parsedElements = json.parse(line);
                JsonObject jsonObject = parsedElements.getAsJsonObject();
            
                roadSections.forEach( locator -> {
                    int speed = jsonObject.get(SPEEDS).getAsJsonArray().get(locator).getAsJsonArray().get(0).getAsInt();
                    TrafficDatum d = new TrafficDatum();
                    d.setSpeed(speed);
                    data.put(locator, d);
                });
            
                for(JsonElement e : jsonObject.get(INCIDENTS).getAsJsonArray()) {
                    JsonArray jsonArray = e.getAsJsonArray();
                    int loc = jsonArray.get(0).getAsInt();
                    if (roadSections.contains(loc)) {
                        String incident = jsonArray.get(2).getAsString() + jsonArray.get(3).getAsString() + jsonArray.get(4).getAsString();
                        data.get(loc).setIncident(incident);
                    }
                }
            }
            dataReader.close();
            
            // log the traffic data to csv format, asynchronously
            TrafficLogging.process(data);
            
            metadataReader.initReader();
            while (metadataReader.hasNext()) {
                String line = metadataReader.nextLine();
                Iterable<String> lineParts = semicolonSplitter.split(line);
                for (String segment : lineParts) {
                    List<String> segmentParts = equalsSplitter.splitToList(segment);
                    if (segmentParts.size() < 2) continue;
                    if (segmentParts.get(0).endsWith(SENSOR_NAMES)) {
                        JsonElement element = json.parse(segmentParts.get(1));
                        roadSections.forEach( locator -> data.get(locator).setLocationName(element.getAsJsonArray().get(locator).getAsString()));
                    }
                }
            }
            metadataReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toJson(data);
    }
    
    private ByteArrayOutputStream toJson(LinkedHashMap<Integer,TrafficDatum> rawData) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1024);
        try (
            Writer w = new BufferedWriter(new OutputStreamWriter(b))
        ) {
            JsonArray jsonArr = rawData.values()
                    .stream()
                    .map(datum -> {
                        JsonArray a = new JsonArray();
                        a.add(datum.getLocationName());
                        a.add(datum.speedAndMaybeIncident());
                        return a;
                    })
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
            gson.toJson(jsonArr, w);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "writing json", new Object[] { e });
        }
        return b;
    }
    
    static class TrafficDatumTypeAdapter extends TypeAdapter<TrafficDatum> {
        @Override
        public void write(JsonWriter jsonWriter, TrafficDatum trafficDatum) throws IOException {
            if (trafficDatum == null) {
                jsonWriter.nullValue();
                return;
            }
            jsonWriter.beginArray();
            jsonWriter.value(trafficDatum.getLocationName());
            jsonWriter.value(trafficDatum.speedAndMaybeIncident());
            jsonWriter.endArray();
        }
    
        @Override
        public TrafficDatum read(JsonReader jsonReader) throws IOException {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull();
                return null;
            }
            String token = jsonReader.nextString();
            String[] parts = token.split(",");
            TrafficDatum datum = new TrafficDatum();
            String locationName = parts[0];
            String[] speedAndMaybeIncident = token.split("-");
            int speed = Integer.parseInt(speedAndMaybeIncident[0]);
            datum.setLocationName(locationName);
            datum.setSpeed(speed);
            if (speedAndMaybeIncident.length > 1) {
                datum.setIncident(speedAndMaybeIncident[1]);
            }
            return datum;
        }
    }
}
