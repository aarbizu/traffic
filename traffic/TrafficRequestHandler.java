/**
 * 
 */
package traffic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * @author alan
 *
 */
public class TrafficRequestHandler implements HttpHandler {
	private Checker trafficChecker;
	
	private TrafficRequestHandler(Checker trafficChecker) {
		super();
		this.trafficChecker = trafficChecker;
	}
	
	public static HttpHandler create(Checker trafficChecker) {
		return new TrafficRequestHandler(trafficChecker);
	}
	/* (non-Javadoc)
	 * @see com.sun.net.httpserver.HttpHandler#handle(com.sun.net.httpserver.HttpExchange)
	 */
	@Override
	public void handle(HttpExchange t) throws IOException {
	   InputStream is = t.getRequestBody();
	   read(is); // .. read the request body
	   String response = trafficChecker.retrieve().toString();
	   t.sendResponseHeaders(200, response.length());
	   OutputStream os = t.getResponseBody();
	   os.write(response.getBytes());
	   os.close();
	}

	//TODO read in some REST-like commands and dispatch to the checker appropriately
	// think one command to get, allowing cache reads, another to force a cache invalidation
	private void read(InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		try {
			in.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
