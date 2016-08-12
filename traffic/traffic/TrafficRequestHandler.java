/**
 * 
 */
package traffic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * @author alan
 */
class TrafficRequestHandler implements HttpHandler {
    private Checker trafficChecker;
	private Splitter andSplitter = Splitter.on("&");
	private Splitter eqSplitter = Splitter.on("=");
	
	private TrafficRequestHandler(Checker trafficChecker) {
		super();
		this.trafficChecker = trafficChecker;
	}
	
	static HttpHandler create(Checker trafficChecker) {
		return new TrafficRequestHandler(trafficChecker);
	}
	/*
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
			String callback = command.getCallback();
			StringBuilder newResponse = new StringBuilder();
			newResponse.append(callback).append("(").append(response).append(");");
			response = newResponse.toString();
		}
		t.sendResponseHeaders(200, response.length());
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}
	
	private Command parseGetParams(HttpExchange exchange) {
		String query = exchange.getRequestURI().getQuery();
		Iterable<String> keyValPairs = andSplitter.split(query);
		Command c = null;
		String callbackFunctionName = null;
		for (String key : keyValPairs) {
			if (Command.isValidCommand(key.toUpperCase())) {
				c = Command.fromString(key);
			} else if (key.contains("=")) {
				Iterable<String> keyAndValue = eqSplitter.split(key);
				Iterator<String> iterator = keyAndValue.iterator();
				String nextKey = iterator.next().toUpperCase();
				if ("CALLBACK".equals(nextKey)) {
					callbackFunctionName = iterator.next();
				}
			}
		}
		c.setCallback(callbackFunctionName);
		return c;
	}

	private String dispatch(Checker trafficChecker, Command cmd) {
		if (cmd != null) {
			return cmd.doCommand(trafficChecker);
		} else {
			return "NO COMMAND";
		}
	}

	private enum Command {
		CHECK {
			@Override 
			public String doCommand(Checker c) {
				return c.retrieve().toString();
			}
		},
		CHECKJS {
			/** 
			 * for angularjs to use $http with JSONP request type,
			 * the callback= parameter needs to be supported, and we need
			 * to format the response like this:
			 *    'callback_param_value( <response> );'
			 */
			@Override
			public String doCommand(Checker c) {
				return c.retrieveObject().toString();
			}
			@Override
			public boolean doSetJsContentType() {
				return true;
			}
			@Override
			public void setCallback(String functionName) {
				this.callbackFunctionName = functionName;
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
		protected String callbackFunctionName = null;
		public boolean doSetJsContentType() { return false; }
		public void setCallback(String functionName) { /* default no-op */ }
		public String getCallback() { return callbackFunctionName; }
		
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
		
		public static boolean isValidCommand(String name) {
			return Command.commandSet.contains(name);
		}
	}
}
