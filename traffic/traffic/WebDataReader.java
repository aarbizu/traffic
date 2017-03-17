package traffic;

import com.google.common.base.Preconditions;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.logging.Level;

/**
 * Retrieve traffic data via URL
 * Created by alan on 3/13/17.
 */
class WebDataReader implements TrafficDataReader {
    
    private static String userAgent = "Mozilla/5.0";
    private boolean initialized;
    private String url;
    private BufferedReader in;
    private OutputStreamWriter out;
    private FileWriter fw;
    private ByteArrayOutputStream data;
    private String currentLine;
    private AutoflushingLogger logger;
    private boolean persist = false;
    
    private WebDataReader(String url, AutoflushingLogger logger) {
        this.url = url;
        this.logger = logger;
    }
    
    static WebDataReader create(String locator, AutoflushingLogger logger) {
        return new WebDataReader(locator, logger);
    }
    
    @Override
    public WebDataReader init() {
        initialized = initWriter();
        return this;
    }
    
    @Override
    public TrafficDataReader setPersist(boolean persist) {
        this.persist = persist;
        return this;
    }
    
    @Override
    public void get() {
        HttpGet request = new HttpGet(url);
        try (
            CloseableHttpClient client = HttpClients.custom()
                    .setUserAgent(userAgent)
                    .build();
            CloseableHttpResponse res = client.execute(request);
            BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()))
        ) {
            String s;
            s = reader.readLine();
            while (s != null) {
                store(s);
                s = reader.readLine();
            }
            close();
            logger.log(Level.INFO, "req={0},res_code={1},res_msg={2}", new Object[]{request.toString(), res.getStatusLine().getStatusCode(), res.toString()});
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private boolean initWriter() {
        data = new ByteArrayOutputStream(INIT_BUFFER_SIZE_BYTES);
        out = new OutputStreamWriter(data);
        if (persist) {
            String[] fields = this.url.split("/");
            try {
                fw = new FileWriter(fields[fields.length - 1]);
            } catch (IOException ioe) {
                persist = false;
            }
        }
        return true;
    }
    
    @Override
    public void store(String s) throws Exception {
        Preconditions.checkState(initialized);
        if (persist) {
            fw.write(s);
        }
        out.write(s);
    }
    
    @Override
    public void initReader() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.toByteArray());
        in = new BufferedReader(new InputStreamReader(inputStream));
    }
    
    @Override
    public String nextLine() throws Exception {
        return currentLine;
    }
    
    @Override
    public boolean hasNext() throws Exception {
        currentLine = in.readLine();
        return currentLine != null;
    }
    
    @Override
    public void close() throws Exception {
        out.close();
    }
}
