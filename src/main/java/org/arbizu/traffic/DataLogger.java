package org.arbizu.traffic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import com.google.common.collect.Maps;
import com.google.common.io.Files;


/**
 * Persists collected time-series traffic data 
 * @author alan
 */
class DataLogger {
	static final String LOG_FILE_EXT = ".csv";
	static final String LOG_PATH = "logs";
	private final Map<Integer, FileWriter> datafileWriters;
	private final int dataFileCount;
	
	private final SimpleDateFormat fileNameFormat = new SimpleDateFormat("MM-yyyy");
	
	private DataLogger(int fileCount) {
		this.dataFileCount = fileCount;
		this.datafileWriters = Maps.newHashMapWithExpectedSize(fileCount);
	}
	
	static DataLogger getLogger(int fileCount) {
		return new DataLogger(fileCount);
	}
	
	boolean initialize() {
		boolean allInitialized = true;
		String filePrefix = getFilePrefix();
		File file = null;
		for (int i = 0; i < dataFileCount; ++i) {
			try {
				file = getFile(filePrefix, i);
				FileWriter fw = new FileWriter(file, true);
				datafileWriters.put(i, fw);
			} catch (IOException e) {
				allInitialized = false;
				String filename = (file != null) ? file.getAbsolutePath() : "unknown path";
				System.err.println("file creation error: " + filename + " " + e.getMessage());
			}
		}
		return allInitialized;
	}
	
	void logData(LinkedList<String> dataSet) throws IOException {
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
        final Path logFilePath = FileSystems.getDefault().getPath(LOG_PATH, prefix + "-" + uniqueId + LOG_FILE_EXT);
		File f = logFilePath.toFile();
		Files.createParentDirs(f);
		if (f.createNewFile()) {
			FileWriter headerWriter = new FileWriter(f);
			headerWriter.write(String.format("%s,%s", "time", "speed"));
			headerWriter.write(System.lineSeparator());
			headerWriter.close();
		}
		return f;
	}
}
