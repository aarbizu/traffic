/**
 * 
 */
package traffic;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Handles access to TrafficParser results.  Use a LoadingCache with a TTL of 15 minutes
 * to preserve results.
 * 
 * @author alan
 */
class Checker {
	private static final String KEY = "92EastTrafficDataKey";
	private final traffic.TrafficParser trafficData;
	private LoadingCache<String,JSONArray> data;
	private RateLimiter uncachedReadLimiter = RateLimiter.create((double) 1/60); // 1 permit every 60 seconds (0.167/sec)
	
	private Checker(traffic.TrafficParser dataProvider) {
		this.trafficData = dataProvider;
	}
	
	static Checker create(traffic.TrafficParser p) {
		Checker c = new Checker(p);
		c.initCache();
		return c;
	}
	
	private void initCache() {
		data = CacheBuilder.newBuilder()
				.maximumSize(1)
				.expireAfterWrite(15, TimeUnit.MINUTES)
				.build(new CacheLoader<String,JSONArray>() {
					@Override
					public JSONArray load(String key) throws Exception {
						return trafficData.process();
					}
					
				});
	}
	
	JSONArray retrieve() {
		try {
			return data.get(KEY);
		} catch (ExecutionException e) {
			e.printStackTrace();
			return new JSONArray().put("ERROR: Unable to load value " + e.getMessage());
		}
	}
	
	JSONObject retrieveObject() {
		try {
			String jsonArrayStr = data.get(KEY).toString();
			return new JSONObject("{ DATA: " + jsonArrayStr + "}");
		} catch (JSONException | ExecutionException e) {
			e.printStackTrace();
			return new JSONObject().putOnce("ERROR", e.getMessage());
		}
	}

	JSONArray force() {
		if (uncachedReadLimiter.tryAcquire()) {
			data.invalidate(KEY);
			return retrieve();
		} else {
			return retrieve();
		}
	}
}
