package traffic;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.stream.Collectors;


/**
 *
 * Created by alan on 3/18/17.
 */
public class DataSource {
    private static final String DATA_URL = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
    private static final String METADATA_URL = "http://cdn-static.sigalert.com/154/Zip/RegionInfo/NoCalStatic.js";
    private static ArrayList<Integer> roadSections = Lists.newArrayList(2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654);
    private final static String SPEEDS = "speeds";
    private final static String INCIDENTS = "incidents";
    private final static String SENSOR_NAMES = "SensorNames";
    private Splitter semicolonSplitter = Splitter.on(";");
    private Splitter equalsSplitter = Splitter.on("=");
    
    private AutoflushingLogger logger;
    private JsonParser json = new JsonParser();
    
    DataSource(AutoflushingLogger logger) {
        this.logger = logger;
    }
    
    private enum SourceType {
        DATA(DATA_URL),
        METADATA(METADATA_URL);
        
        private String url;
        SourceType(String url) { this.url = url; }
        public String getUrl() {  return this.url;  }
    }
    
    private TrafficDataReader getReaderFor(SourceType type) {
        if (type == SourceType.METADATA) {
            return FileDataReader.create("static-js-resources", logger);
        } else {
            return WebDataReader.create(type.getUrl(), logger);
        }
    }
    
    LinkedHashMap<String, String> collect() {
        TrafficDataReader dataReader = getReaderFor(SourceType.DATA).init().read();
        TrafficDataReader metadataReader = getReaderFor(SourceType.METADATA).init().read();
    
        LinkedHashMap<Integer,TrafficDatum> data = new LinkedHashMap<>();
        LinkedHashMap<String,String> retVal = null;
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
            
            metadataReader.initReader();
            while (metadataReader.hasNext()) {
                String line = metadataReader.nextLine();
                Iterable<String> lineParts = semicolonSplitter.split(line);
                for (String segment : lineParts) {
                    List<String> segmentParts = equalsSplitter.splitToList(segment);
                    if (segmentParts.size() < 2) continue;
                    if (segmentParts.get(0).endsWith(SENSOR_NAMES)) {
                        JsonElement element = json.parse(segmentParts.get(1));
                        roadSections.forEach( locator -> {
                            data.get(locator).setLocationName(element.getAsJsonArray().get(locator).toString());
                        });
                    }
                }
            }
            metadataReader.close();
    
            retVal = data.values()
                    .stream()
                    .collect(Collectors.toMap(TrafficDatum::getLocationName, TrafficDatum::toString, (v1, v2) -> v1, LinkedHashMap::new));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Preconditions.checkNotNull(retVal);
    }
}
