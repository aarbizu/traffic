package traffic;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task to run every n-minutes to collect and log traffic data
 * @author alan
 */
class TrafficLoggerTask {
	private ScheduledExecutorService scheduler =  Executors.newScheduledThreadPool(1);
	
	private void logEvery(final long delay, final TimeUnit durationUnit, final Checker trafficChecker) {
		final Runnable logger = trafficChecker::force;
		
		scheduler.scheduleAtFixedRate(logger, 0, delay, durationUnit);
	}
	
	static void createAndSchedule(final long delay, final TimeUnit durationUnit, final Checker trafficChecker) {
		TrafficLoggerTask task = new TrafficLoggerTask();
		task.logEvery(delay, durationUnit, trafficChecker);
	}
}
