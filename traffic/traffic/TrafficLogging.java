/**
 * 
 */
package traffic;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import traffic.TrafficParser.TrafficData;

import com.google.common.collect.Lists;

/**
 * Service to take enqueued logging requests and dispatch them to a writer
 * @author alan
 *
 */
class TrafficLogging {
	private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");
	static boolean process(LinkedHashMap<Integer, TrafficData> trafficDataMap) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		LogTask t = new LogTask(trafficDataMap);
		Future<Boolean> logInitFuture = executor.submit(t);
		executor.shutdown();
		boolean success = false;
		try {
			success = logInitFuture.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException ee) {
			success = false;
		} catch (InterruptedException e) {
			// could still be waiting for the future, assume it worked
			success = true;
		}
		
		return success;
	}
	
	private static class LogTask implements Callable<Boolean> {
		private DataLogger logger;
		private LinkedList<String> data;
		
		LogTask(LinkedHashMap<Integer,TrafficData> dataMap) {
			this.logger = DataLogger.getLogger(dataMap.size());
			this.data = prepareLoggingData(dataMap);
		}
		
		@Override
		public Boolean call() {
			boolean initialized = logger.initialize();
			if(initialized) {
				try {
					logger.logData(data);
				} catch (IOException e) {
					
				}
			}
			return initialized;
		}
	}
	
	private static LinkedList<String> prepareLoggingData(LinkedHashMap<Integer, TrafficData> dataMap) {
		LinkedList<String> dataSet = Lists.newLinkedList();
		Date now = new Date();
		for (Map.Entry<Integer, TrafficData> entry : dataMap.entrySet()) {
			String logEntry = String.format("%s,%d", LOG_DATE_FORMAT.format(now), entry.getValue().getSpeed());
			dataSet.add(logEntry);
		}
		return dataSet;
		
	}
}
