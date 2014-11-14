/**
 * 
 */
package traffic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author alan
 *
 */
public class TrafficParser {
	
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
	private static boolean DEBUGGING;
	private final static String SENSOR_NAMES = "SensorNames";
	private final static String SPEEDS = "speeds";
	private final static String INCIDENTS = "incidents";
	private static String detailsUrl = "http://cdn-static.sigalert.com/129/Zip/RegionInfo/NoCalStatic.js";
	private static String dataUrl = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
	private static ArrayList<Integer> roadSections = Lists.newArrayList( 2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654 );
	
	public enum SourceType {
		DETAILS(detailsUrl, "details.dat"),
		DATA(dataUrl, "data.dat");
		
		private String url;
		private String fileName;
		
		private SourceType(String url, String debuggingFile) {
			this.url = url;
			this.fileName = debuggingFile;
		}
		
		public String getUrl() { return this.url; }
		public String getDebuggingFile() { return this.fileName; }
	}
	
	private JSONArray process() {
		JSONArray trafficSummary = getTrafficSummary();
		debugOutput(trafficSummary);
		return trafficSummary;
	}

	private void debugOutput(JSONArray data) {
		if (!TrafficParser.isDebugging()) return;
		try (
				FileWriter fw = new FileWriter("analyzed.data");
				BufferedWriter writer = new BufferedWriter(fw);
			) 
		{
			writer.write(data.toString(2));
			writer.newLine();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	static private boolean isDebugging() {
		return DEBUGGING;
	}

	private JSONArray getTrafficSummary() {
		Getter details = createGetter(SourceType.DETAILS);
		Getter data = createGetter(SourceType.DATA);
		
		if (details != null) {
			details.get();
		}
		
		if (data != null) {
			data.get();
		}
		
		TrafficMetadata detailsParser = TrafficMetadata.create(details);
		TrafficDetails dataParser = TrafficDetails.create(data);
		
		LinkedHashMap<Integer,String> trafficMetadataMap = detailsParser.parse();
		LinkedHashMap<Integer,TrafficData> trafficDataMap = dataParser.parse();
		
		LinkedHashMap<String,String> dataMap = joinMaps(trafficMetadataMap, trafficDataMap);
		
		JSONArray customDataJson = getTrafficDataSummary(dataMap);
		return customDataJson;
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

	private LinkedHashMap<String,String> joinMaps(LinkedHashMap<Integer,String> metadata, LinkedHashMap<Integer,TrafficData> data) {
		LinkedHashMap<String,String> retVal = Maps.newLinkedHashMap();
		for (Map.Entry<Integer, String> e : metadata.entrySet()) {
			retVal.put(e.getValue(), data.get(e.getKey()).toString());
		}
		return retVal;
	}
	
	private static class TrafficData {
		private int speed;
		private String incident = null;
		
		public void setSpeed(int speed) {
			this.speed = speed;
		}
		
		public void setIncident(String incident) {
			this.incident = incident;
		}
		
		@Override
		public String toString() {
			if (incident == null) {
				return String.valueOf(speed);
			} else {
				return String.valueOf(speed) + " - " + incident;
			}
		}
		
	}

	public enum JSONType {
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
	
	public Getter createGetter(SourceType type) {
		if (TrafficParser.isDebugging()) {
			return FileGetter.create(type.getUrl(), type.getDebuggingFile());
		} else {
			return Getter.create(type.getUrl());
		}
	}
	
	static class Getter {
		private String url;
		private Iterator<String> lines;
		private static String userAgent = "Mozilla/5.0";
		List<String> data = Lists.newArrayListWithExpectedSize(1000);

		private Getter(String url) {
			this.url = url;
		}
		
		public static Getter create(String locator) {
			return new Getter(locator);
		}
		
		public void get() {
			HttpGet request = new HttpGet(url);
			try (
				CloseableHttpClient client = HttpClients.custom()
											.setUserAgent(userAgent)
											.build();
				CloseableHttpResponse res = client.execute(request);
				BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
			) { 
				String s = null;
				s = reader.readLine();
				while (s != null) {
					store(s);
					s = reader.readLine();
				}
				close();
			} catch (IllegalStateException | IOException e ) {
				e.printStackTrace();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		void store(String s) throws Exception {
			data.add(s);
		}
		
		void initReader() throws Exception {
			lines = data.iterator();
		}
		
		String nextLine() throws Exception {
			return lines.next();
		}
		
		boolean hasNext() throws Exception {
			return lines.hasNext();
		}
		
		void close() throws Exception {
			// no-op
		}
		
	}
	
	static class FileGetter extends Getter {
		private String filename;
		private BufferedWriter out;
		private BufferedReader in;
		private boolean initialized;
		private String currentLine;

		private FileGetter (String url, String filename) {
			super(url);
			this.filename = filename;
		}
		
		public static FileGetter create(String locator, String file) {
			return new FileGetter(locator, file).init();
		}
		
		public FileGetter init() {
			initialized = initWriter();
			if(initialized) {
				return this;
			} else {
				return null;
			}
		}
		
		private boolean initWriter() {
			try {
				out = new BufferedWriter(new FileWriter(filename));
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		
		@Override
		void store(String s) throws Exception {
			Preconditions.checkState(initialized);
			out.write(s);
		}
		
		@Override
		String nextLine() throws Exception {
			return currentLine;
		}
		
		@Override
		void initReader() throws Exception {
			in = new BufferedReader(new FileReader(filename));
		}
		
		@Override
		boolean hasNext() throws Exception {
			currentLine = in.readLine();
			return currentLine != null;
		}
		
		@Override
		void close() throws Exception {
			if (out != null) out.close();
			if (in != null) in.close();
		}
	}
	
	/**
	 * The traffic details is a JSONObject containing speed and incident information
	 */
	static class TrafficDetails {
		private Splitter dataSplitter = Splitter.on(";");
		private Joiner dashJoiner = Joiner.on("-");
		private Getter data;
		
		public TrafficDetails(Getter detailsGetter) {
			this.data = detailsGetter;
		}

		public static TrafficDetails create(Getter detailsGetter) {
			return new TrafficDetails(detailsGetter);
		}
		
		public LinkedHashMap<Integer,TrafficData> parse() {
			LinkedHashMap<Integer,TrafficData> map = initalizeTrafficDataMap();
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
									for (Integer i : roadSections) {
										if (!jsonArray.isNull(i)) {
											JSONArray values = jsonArray.getJSONArray(i);
											map.get(i).setSpeed(values.getInt(0));
										}
									}
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

		private LinkedHashMap<Integer, TrafficData> initalizeTrafficDataMap() {
			LinkedHashMap<Integer, TrafficData> map = Maps.newLinkedHashMap();
			for (Integer i : roadSections) {
				map.put(i, new TrafficData());
			}
			return map;
		}
	}
	
	/**
	 *	The metadata from sigalert.com contains several JSON structures (objects and arrays).
	 *	Split out the objects into a String,String map
	 */
	static class TrafficMetadata {
		private Getter data;
		private MapSplitter detailSplitter = Splitter.on(";")
											.omitEmptyStrings()
											.trimResults()
											.withKeyValueSeparator("=");
		private Splitter semicolonSplitter = Splitter.on(";");
		
		private TrafficMetadata(Getter metadataGetter) {
			this.data = metadataGetter;
		}
		
		public static TrafficMetadata create(Getter metadataGetter) {
			return new TrafficMetadata(metadataGetter);
		}

		/**
		 * Returns a LinkedHashMap to preserve the order, which is in turn based on 
		 * the order of roadSection ids 
		 */
		public LinkedHashMap<Integer,String> parse() {
			LinkedHashMap<Integer,String> map = Maps.newLinkedHashMap();
			try {
				data.initReader();
				while (data.hasNext()) {
					String line = data.nextLine();
					
					// break up the JSON objects, terminated by semicolons
					Iterable<String> lineParts = semicolonSplitter.split(line);
					
					for (String linePart: lineParts) {
						// unless it's in key=value format, don't care
						if (!linePart.contains("=")) continue;
						
						Map<String, String> split = detailSplitter.split(linePart);
						for (Map.Entry<String, String> e : split.entrySet()) {
							String prefix = e.getKey();
							JSONType type = JSONType.getTypeFromString(e.getValue());
							// other traffic data is SensorPositions, RoadSections, Roads
							// if (type == JSONType.OBJECT) {		
							//	 JSONObject jsonObj = parseJsonObject(e.getValue());
							// }
							// grab the sensor names for the 92-E exits we're concerned about from the JSONArray
							if (type == JSONType.ARRAY) {
								JSONArray jsonArray = new JSONArray(e.getValue());
								if (prefix.contains(SENSOR_NAMES)) {
									for (Integer i : roadSections) {
										String value = jsonArray.get(i).toString();
										map.put(i, value);
									}
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return map;
		}
		
	}

	
	public static void main (String... args) {
		// -Ddebug=true
		DEBUGGING = Boolean.valueOf(System.getProperty(DEBUG_PARAM).toLowerCase());
		
		TrafficParser tp = new TrafficParser();
		// hand off the parser to a thread that will run, cache the results with a 15 min TTL, retrieving the data remotely when not cached
		
		tp.process();
		
		// one more thread to listen for requests, returning the 
	}
}
