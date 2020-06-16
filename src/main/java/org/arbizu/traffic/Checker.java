package org.arbizu.traffic;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Handles access to Traffic results.  Use a LoadingCache with a TTL of 15 minutes
 * to preserve results.
 * 
 * @author alan
 */
class Checker {
	private static final String KEY = "92EastTrafficDataKey";
	private final Traffic trafficData;
	private LoadingCache<String,ByteArrayOutputStream> data;
	private final RateLimiter uncachedReadLimiter = RateLimiter.create((double) 1/60); // 1 permit every 60 seconds (0.167/sec)
	
	private Checker(Traffic dataProvider) {
		this.trafficData = dataProvider;
	}
	
	static Checker create(Traffic p) {
		Checker c = new Checker(p);
		c.initCache();
		return c;
	}
	
	private void initCache() {
		data = CacheBuilder.newBuilder()
				.maximumSize(1)
				.expireAfterWrite(15, TimeUnit.MINUTES)
				.build(new CacheLoader<String,ByteArrayOutputStream>() {
					@Override
					public ByteArrayOutputStream load(String key) throws Exception {
						return trafficData.process();
					}
					
				});
	}
	
	ByteArrayOutputStream retrieve() {
		try {
			return data.get(KEY);
		} catch (ExecutionException e) {
			e.printStackTrace();
			byte [] err = String.format("[ERROR: \"Unable to load value %s\"]" ,e.getMessage()).getBytes();
			ByteArrayOutputStream b = new ByteArrayOutputStream(512);
			b.write(err,0, err.length);
			return b;
		}
	}
	
	ByteArrayOutputStream retrieveObject() {
        String objectEnd = "}";
        byte [] objectEndBytes= objectEnd.getBytes();
		try {
			ByteArrayOutputStream jsonArrayStr = data.get(KEY);
			ByteArrayOutputStream jsonObjectBaos = new ByteArrayOutputStream(512);
			String objectBegin = "{ \"DATA\": ";
			byte [] objectBeginBytes = objectBegin.getBytes();
			jsonObjectBaos.write(objectBeginBytes,0,objectBeginBytes.length);
			byte [] jsonBytes = jsonArrayStr.toByteArray();
			jsonObjectBaos.write(jsonBytes,0,jsonBytes.length);
			jsonObjectBaos.write(objectEndBytes,0,objectEndBytes.length);
			return jsonObjectBaos;
		} catch (Exception e) {
			e.printStackTrace();
			String message = e.getMessage();
			byte[] errMsg = message.getBytes();
			ByteArrayOutputStream errStream = new ByteArrayOutputStream(errMsg.length + 10);
			String errObjBegin = "{ \"ERROR\":";
			byte[] errObjBeginBytes = errObjBegin.getBytes();
			errStream.write(errObjBeginBytes,0,errObjBeginBytes.length);
			errStream.write(errMsg,0,errMsg.length);
			errStream.write(objectEndBytes,0,objectEndBytes.length);
			return errStream;
		}
	}

	ByteArrayOutputStream force() {
		if (uncachedReadLimiter.tryAcquire()) {
			data.invalidate(KEY);
			return retrieve();
		} else {
			return retrieve();
		}
	}
}
