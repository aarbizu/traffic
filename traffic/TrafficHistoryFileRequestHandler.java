package traffic;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * HTTP requests for traffic file data, with caching.
 * @author alan
 *
 */
public class TrafficHistoryFileRequestHandler implements HttpHandler {
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	private static final LoadingCache<String, byte[]> FILE_CACHE = CacheBuilder.newBuilder()
			.maximumSize(20)
			.expireAfterWrite(1, TimeUnit.DAYS)
			.build(new CacheLoader<String, byte[]>() {
				@Override
				public byte[] load(String key) throws Exception {
					return TrafficHistoryFileRequestHandler.loadFromFilesystem(key);
				}
			});

	public static HttpHandler create() {
		return new TrafficHistoryFileRequestHandler();
	}

	@Override
	public void handle(HttpExchange t) throws IOException {
		byte[] fileData = getFileStream(t.getRequestURI().getQuery());
		OutputStream os = t.getResponseBody();
		if (null != fileData) {
			t.sendResponseHeaders(200, fileData.length);
			copy(new ByteArrayInputStream(fileData), os);
		} else {
			String err = "Error loading file";
			t.sendResponseHeaders(503, err.length());
			os.write(err.getBytes());
		}
		os.close();
	}

	private byte[] getFileStream(String query) throws FileNotFoundException {
//		System.out.println("got " + query);
		try {
			byte[] data = FILE_CACHE.get(query);
			if (null == data) {
				data = loadFromFilesystem(query);
			}
			return data;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			return null;
		}
	}

	private static byte[] loadFromFilesystem(String query) throws IOException {
		byte[] data;
		final Path path = FileSystems.getDefault().getPath(DataLogger.LOG_PATH, query + DataLogger.LOG_FILE_EXT);
		data = Files.readAllBytes(path);
		FILE_CACHE.put(query, data);
		return data;
	}

	private int copy(InputStream input, OutputStream output) throws IOException {
	  long count = copyLarge(input, output);
	  if (count > Integer.MAX_VALUE) {
	    return -1;
	  }
	  return (int) count;
	}

	private long copyLarge(InputStream input, OutputStream output) throws IOException {
	   byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
	   long count = 0;
	   int n = 0;
	   while (-1 != (n = input.read(buffer))) {
	     output.write(buffer, 0, n);
	     count += n;
	   }
	   return count;
	}

}
