/**
 * 
 */
package traffic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * @author alan
 *
 */
public class TrafficRequestHandler implements HttpHandler {
	private Checker trafficChecker;
	private Splitter andSplitter = Splitter.on("&");
	
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
		t.getRequestBody();
		Command command = parseGetParams(t);
		String response = dispatch(trafficChecker, command); 
		if (command.doSetJsContentType()) {
			Headers responseHeaders = t.getResponseHeaders();
			responseHeaders.set("Content-Type", "text/javascript");
		}
		t.sendResponseHeaders(200, response.length());
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}
	
	private Command parseGetParams(HttpExchange exchange) {
		String query = exchange.getRequestURI().getQuery();
		Iterable<String> keyValPairs = andSplitter.split(query);
		String first = keyValPairs.iterator().next().toUpperCase();
		return Command.fromString(first);
	}

	private String dispatch(Checker trafficChecker, Command cmd) {
		if (cmd != null) {
			return cmd.doCommand(trafficChecker);
		} else {
			return "NO COMMAND";
		}
	}

	private static enum Command {
		CHECK {
			@Override 
			public String doCommand(Checker c) {
				return c.retrieve().toString();
			}
		},
		CHECKJS {
			@Override
			public String doCommand(Checker c) {
				return c.retrieve().toString();
			}
			@Override
			public boolean doSetJsContentType() {
				return true;
			}
		},
		FORCE {
			@Override
			public String doCommand(Checker c) {
				return c.force().toString();
			}
		},
		UNKNOWN {
			@Override
			public String doCommand(Checker c) {
				return this.name();
			}
		};
		
		public abstract String doCommand(Checker c);
		public boolean doSetJsContentType() { return false; }
		
		private static final Set<String> commandSet = initializeCommandNameSet();
		
		private static Set<String> initializeCommandNameSet() {
			ImmutableSet.Builder<String> setBuilder = new ImmutableSet.Builder<String>();
			for (Command c : Command.values()) {
				setBuilder.add(c.name());
			}
			return setBuilder.build();
		}
		
		public static Command fromString(String name) {
			if (Command.commandSet.contains(name)) {
				return Command.valueOf(name);
			} else {
				return Command.UNKNOWN;
			}
		}
	}
}
