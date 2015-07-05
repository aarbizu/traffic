/**
 * 
 */
package traffic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import com.sun.net.httpserver.HttpServer;

/**
 * 
 * Read traffic data for 92-E from sigalert.com data structures.
 * @author alan
 * 
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
	private static boolean DEBUGGING;
	private static final int MAX_QUEUE_SIZE = 32;
	private final static String SENSOR_NAMES = "SensorNames";
	private final static String SPEEDS = "speeds";
	private final static String INCIDENTS = "incidents";
	private static String detailsUrl = "http://cdn-static.sigalert.com/129/Zip/RegionInfo/NoCalStatic.js";
	private static String dataUrl = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
	private static ArrayList<Integer> roadSections = Lists.newArrayList( 2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654 );
	
	private enum SourceType {
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
	
	public JSONArray process() {
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
	
	private static boolean isDebugging() {
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
		dispatchLogging(trafficDataMap);
		LinkedHashMap<String,String> dataMap = joinMaps(trafficMetadataMap, trafficDataMap);
		
		JSONArray customDataJson = getTrafficDataSummary(dataMap);
		return customDataJson;
	}
	
	private void dispatchLogging(LinkedHashMap<Integer, TrafficData> trafficDataMap) {
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

	private LinkedHashMap<String,String> joinMaps(LinkedHashMap<Integer,String> metadata, LinkedHashMap<Integer,TrafficData> data) {
		LinkedHashMap<String,String> retVal = Maps.newLinkedHashMap();
		for (Map.Entry<Integer, String> e : metadata.entrySet()) {
			retVal.put(e.getValue(), data.get(e.getKey()).toString());
		}
		return retVal;
	}
	
	protected static class TrafficData {
		private int speed;
		private String incident = null;
		
		public void setSpeed(int speed) {
			this.speed = speed;
		}
		
		public void setIncident(String incident) {
			this.incident = incident;
		}
		
		public int getSpeed() {
			return this.speed;
		}
		
		public String getIncident() {
			return this.incident;
		}
		
		public boolean hasIncident() {
			return (incident != null);
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
	
	private Getter createGetter(SourceType type) {
		if (TrafficParser.isDebugging()) {
			return FileGetter.create(type.getUrl(), type.getDebuggingFile());
		} else {
			return Getter.create(type.getUrl());
		}
	}
	
	private static class Getter {
		private final static int INIT_BUFFER_SIZE_BYTES = 512;
		private static String userAgent = "Mozilla/5.0";
		private boolean initialized;
		private String url;
		private BufferedReader in;
		private OutputStreamWriter out;
		private ByteArrayOutputStream data;
		private String currentLine;

		private Getter(String url) {
			this.url = url;
		}
		
		public static Getter create(String locator) {
			return new Getter(locator).init();
		}
		
		public Getter init() {
			initialized = initWriter();
			return this;
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
		
		private boolean initWriter() {
			data = new ByteArrayOutputStream(INIT_BUFFER_SIZE_BYTES);
			out = new OutputStreamWriter(data);
			return true;
		}
		
		void store(String s) throws Exception {
			Preconditions.checkState(initialized);
			out.write(s);
		}
		
		void initReader() throws Exception {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(data.toByteArray());
			in = new BufferedReader(new InputStreamReader(inputStream));
		}
		
		String nextLine() throws Exception {
			return currentLine;
		}
		
		boolean hasNext() throws Exception {
			currentLine = in.readLine();
			return currentLine != null;
		}
		
		void close() throws Exception {
			out.close();
		}
	}
	
	private static class FileGetter extends Getter {
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
	private static class TrafficDetails {
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
	private static class TrafficMetadata {
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
		String isDebugSet = System.getProperty(DEBUG_PARAM);
		if (isDebugSet != null) {
			DEBUGGING = Boolean.valueOf(isDebugSet.toLowerCase());
		}
		
		Checker trafficChecker = Checker.create(new TrafficParser());
		TrafficLoggerTask.createAndSchedule(15, TimeUnit.MINUTES, trafficChecker);
		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress(HTTP_PORT), MAX_QUEUE_SIZE);
			server.createContext("/t", TrafficRequestHandler.create(trafficChecker));
			server.setExecutor(null); // creates a default executor
			server.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
