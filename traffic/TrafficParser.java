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
	
	static ArrayList<Integer> roadSections = Lists.newArrayList( 2645, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654 );
	/* 
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
	final static String SENSOR_NAMES = "SensorNames";
	final static String SPEEDS = "speeds";
	final static String INCIDENTS = "incidents";
	private static String detailsUrl = "http://cdn-static.sigalert.com/129/Zip/RegionInfo/NoCalStatic.js";
	private static String dataUrl = "http://www.sigalert.com/Data/NoCal/1~j/NoCalData.json?cb=25615489";
	
	private void process() {
		
		JSONArray trafficSummary = getTrafficSummary();
		
		debugOutput(trafficSummary); 
	}

	private void debugOutput(JSONArray data) {
		try (
				FileWriter fw = new FileWriter("analyzed.data");
				BufferedWriter writer = new BufferedWriter(fw);
			) 
		{
			writer.write(data.toString(2));
			writer.newLine();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private JSONArray getTrafficSummary() {
		FileGetter details = FileGetter.create(detailsUrl, "details.dat");
		FileGetter data = FileGetter.create(dataUrl, "data.dat");
		details.get();
		data.get();
		
		TrafficMetadata detailsParser = TrafficMetadata.create("details.dat");
		TrafficDetails dataParser = TrafficDetails.create("data.dat");
		
		LinkedHashMap<Integer,String> trafficMetadataMap = detailsParser.parse();
		LinkedHashMap<Integer,TrafficData> trafficDataMap = dataParser.parse();
		
		LinkedHashMap<String,String> dataMap = joinMaps(trafficMetadataMap, trafficDataMap);
		
		JSONArray customDataJson = generateCustomJson(dataMap);
		return customDataJson;
	}
	
	
	
	private JSONArray generateCustomJson(LinkedHashMap<String, String> dataMap) {
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
	
	static class FileGetter {
		private String url;
		private String filename;
		private static String userAgent = "Mozilla/5.0";

		private FileGetter(String url, String filename) {
			this.url = url;
			this.filename = filename;
		}
		
		public static FileGetter create(String locator, String file) {
			return new FileGetter(locator, file);
		}
		
		public void get() {
			HttpGet request = new HttpGet(url);
			try (
				CloseableHttpClient client = HttpClients.custom()
											.setUserAgent(userAgent)
											.build();
				CloseableHttpResponse res = client.execute(request);
				BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
				FileWriter fw = new FileWriter(filename);
				BufferedWriter writer = new BufferedWriter(fw);	
			) { 
				String s = null;
				s = reader.readLine();
				while (s != null) {
					writer.write(s);
					s = reader.readLine();
				}
			} catch (IllegalStateException | IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * The traffic details is a JSONObject containing speed and incident information
	 */
	static class TrafficDetails {
		private String dataFile;
		private Splitter dataSplitter = Splitter.on(";");
		private Joiner dashJoiner = Joiner.on("-");
		public TrafficDetails(String file) {
			this.dataFile = file;
		}

		public static TrafficDetails create(String file) {
			return new TrafficDetails(file);
		}
		
		public LinkedHashMap<Integer,TrafficData> parse() {
			LinkedHashMap<Integer,TrafficData> map = initalizeTrafficDataMap();
			Preconditions.checkState(map.size() == roadSections.size());
			try (
				FileReader fr = new FileReader(dataFile);
				BufferedReader in = new BufferedReader(fr);
			) {
				String line = in.readLine();
				while (line != null) {
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
					line = in.readLine();
				}
			} catch (IOException e) {
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
		private String detailsFile;
		private MapSplitter detailSplitter = Splitter.on(";")
											.omitEmptyStrings()
											.trimResults()
											.withKeyValueSeparator("=");
		private Splitter semicolonSplitter = Splitter.on(";");
		
		private TrafficMetadata(String file) {
			this.detailsFile = file;
		}
		
		public static TrafficMetadata create(String file) {
			return new TrafficMetadata(file);
		}

		public LinkedHashMap<Integer,String> parse() {
			LinkedHashMap<Integer,String> map = Maps.newLinkedHashMap();
			try ( 
				FileReader fr = new FileReader(detailsFile);
				BufferedReader in = new BufferedReader(fr);
			) {
				String line = in.readLine();
				while (line != null) {
					Iterable<String> lineParts = semicolonSplitter.split(line);
					for (String linePart: lineParts) {
						if (!linePart.contains("=")) continue;
						Map<String, String> split = detailSplitter.split(linePart);
						for (Map.Entry<String, String> e : split.entrySet()) {
							String prefix = e.getKey();
							JSONType type = JSONType.getTypeFromString(e.getValue());
							// other traffic data is SensorPositions, RoadSections, Roads
//							if (type == JSONType.OBJECT) {		
//								JSONObject jsonObj = parseJsonObject(e.getValue());
//							}
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
					line = in.readLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return map;
		}
		
	}

	
	public static void main (String... args) {
		TrafficParser tp = new TrafficParser();
		tp.process();
	}
}
