package traffic;

import com.google.common.base.Preconditions;

import java.io.*;
import java.util.logging.Level;

/**
 * Retrieve traffic data from a local file
 * Created by alan on 3/13/17.
 */
class FileDataReader implements TrafficDataReader {
    private String filename;
    private BufferedReader in;
    private ByteArrayOutputStream data;
    private boolean initialized;
    private String currentLine;
    private OutputStreamWriter out;
    private AutoflushingLogger logger;
    
    private FileDataReader(String filename, AutoflushingLogger logger) {
        this.filename = filename;
        this.logger = logger;
    }
    
    static FileDataReader create(String locator, AutoflushingLogger logger) {
        return new FileDataReader(locator, logger);
    }
    
    @Override
    public TrafficDataReader init() {
        initialized = initWriter();
        if (initialized) {
            return this;
        } else {
            return null;
        }
    }
    
    @Override
    public void get() {
        try (
            BufferedReader reader = new BufferedReader(new FileReader(this.filename));
        )
        {
            String s;
            int lineCount = 0;
            s = reader.readLine();
            while (s != null) {
                store(s);
                ++lineCount;
                s = reader.readLine();
            }
            close();
            logger.log(Level.INFO, "read file {0}, {1} lines", new Object[] {this.filename, lineCount});
        } catch (Exception x) {
            x.printStackTrace();
        }
    }
    
    @Override
    public TrafficDataReader setPersist(boolean doPersistence) {
        return this;
    }
    
    private boolean initWriter() {
        data = new ByteArrayOutputStream(INIT_BUFFER_SIZE_BYTES);
        out = new OutputStreamWriter(data);
        return true;
    }
    
    @Override
    public void store(String s) throws Exception {
        Preconditions.checkState(initialized);
        out.write(s);
    }
    
    @Override
    public String nextLine() throws Exception {
        return currentLine;
    }
    
    @Override
    public void initReader() throws Exception {
        in = new BufferedReader(new FileReader(filename));
    }
    
    @Override
    public boolean hasNext() throws Exception {
        currentLine = in.readLine();
        return currentLine != null;
    }
    
    @Override
    public void close() throws Exception {
        if (out != null) out.close();
        if (in != null) in.close();
    }
}
