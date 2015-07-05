/**
 * 
 */
package traffic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import com.google.common.collect.Maps;


/**
 * Persists collected time-series traffic data 
 * @author alan
 */
public class DataLogger {
	private Map<Integer, FileWriter> datafileWriters;
	private int dataFileCount;
	
	private SimpleDateFormat fileNameFormat = new SimpleDateFormat("MM-yyyy");
	
	private DataLogger(int fileCount) {
		this.dataFileCount = fileCount;
		this.datafileWriters = Maps.newHashMapWithExpectedSize(fileCount);
	}
	
	static DataLogger getLogger(int fileCount) {
		return new DataLogger(fileCount);
	}
	
	public boolean initialize() {
		boolean allInitialized = true;
		String filePrefix = getFilePrefix();
		for (int i = 0; i < dataFileCount; ++i) {
			try {
				File file = getFile(filePrefix, i);
				FileWriter fw = new FileWriter(file, true);
				datafileWriters.put(i, fw);
			} catch (IOException e) {
				allInitialized = false;
			}
		}
		return allInitialized;
	}
	
	public void logData(LinkedList<String> dataSet) throws IOException {
		int id = 0;
		for (String s : dataSet) {
			FileWriter fw = datafileWriters.get(id);
			fw.write(s);
			fw.write(System.lineSeparator());
			++id;
		}
		closeAll(datafileWriters);
	}
	
	private void closeAll(Map<Integer, FileWriter> writers) {
		for (FileWriter fw : writers.values()) {
			try {
				fw.close();
			} catch (IOException ioe) {
				// keep trying to close the other writers
			}
		}
	}
	
	private String getFilePrefix() {
		Date now = new Date();
		return fileNameFormat.format(now);
	}
	
	private File getFile(String prefix, int uniqueId) throws IOException {
		StringBuilder fileNameBuilder = new StringBuilder(prefix)
		.append("-").append(uniqueId).append(".csv");
		File f = new File(fileNameBuilder.toString());
		if (f.createNewFile()) {
			FileWriter headerWriter = new FileWriter(f);
			headerWriter.write(String.format("%s,%s", "time", "speed"));
			headerWriter.write(System.lineSeparator());
			headerWriter.close();
		}
		return f;
	}
}
