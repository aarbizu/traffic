package org.arbizu.traffic;

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

import com.google.common.base.Throwables;

import com.google.common.collect.Lists;

/**
 * Service to take enqueued logging requests and dispatch them to a writer
 * @author alan
 *
 */
class TrafficLogging {
	private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");
	static void process(LinkedHashMap<Integer, TrafficDatum> trafficDataMap) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		LogTask t = new LogTask(trafficDataMap);
		Future<Boolean> logInitFuture = executor.submit(t);
		executor.shutdown();
		try {
			logInitFuture.get(10, TimeUnit.SECONDS);
		} catch (Exception ee) {
            ee.printStackTrace();
        }
	}
	
	private static class LogTask implements Callable<Boolean> {
		private final DataLogger logger;
		private final LinkedList<String> data;
		
		LogTask(LinkedHashMap<Integer, TrafficDatum> dataMap) {
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
					System.err.println("Error logging data: " + Throwables.getStackTraceAsString(e));
				}
			}
			return initialized;
		}
	}
	
	private static LinkedList<String> prepareLoggingData(LinkedHashMap<Integer, TrafficDatum> dataMap) {
		LinkedList<String> dataSet = Lists.newLinkedList();
		Date now = new Date();
		for (Map.Entry<Integer, TrafficDatum> entry : dataMap.entrySet()) {
			String logEntry = String.format("%s,%d", LOG_DATE_FORMAT.format(now), entry.getValue().getSpeed());
			dataSet.add(logEntry);
		}
		return dataSet;
		
	}
}
