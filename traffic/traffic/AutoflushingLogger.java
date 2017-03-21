package traffic;


import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.*;

/**
 * Wrap a Logger with a designated source and auto-flush logging
 * Created by alan on 3/11/17.
 */
class AutoflushingLogger  {
    private final Logger logger;
    private Handler handler;
    private final String sourceName;
    
    
    AutoflushingLogger(Logger logger, String source, String logFileName) {
        this.logger = logger;
        this.sourceName = source;
        try {
            this.handler = new StreamHandler(new FileOutputStream(logFileName, true), new SimpleFormatter());
            this.logger.addHandler(handler);
        } catch (IOException iox) {
            System.err.println("couldn't customize handler, using default" + iox);
        }
    }
    
    private Handler getHandler() {
        return this.handler;
    }
    
    private String getSource() {
        return this.sourceName;
    }
    
    private void log(String source, Level level, String msg, Object[] params) {
        LogRecord lr = new LogRecord(level, msg);
        lr.setParameters(params);
        lr.setSourceClassName(source);
        logger.log(lr);
        getHandler().flush();
    }
    
    void log(Level level, String msg, Object[] params) {
        log(getSource(), level, msg, params);
    }
}
