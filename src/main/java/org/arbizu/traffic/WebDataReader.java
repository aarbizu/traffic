package org.arbizu.traffic;

import com.google.common.base.Preconditions;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;

import java.io.*;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Retrieve traffic data via URL
 * Created by alan on 3/13/17.
 */
class WebDataReader implements TrafficDataReader {
    
    private boolean initialized;
    private final String url;
    private BufferedReader in;
    private OutputStreamWriter out;
    private FileWriter fw;
    private ByteArrayOutputStream data;
    private String currentLine;
    private final AutoflushingLogger logger;
    private String fallbackFile;
    private boolean persist = false;
    
    private WebDataReader(String url, AutoflushingLogger logger, String fallbackFile) {
        this.url = url;
        this.logger = logger;
        this.fallbackFile = fallbackFile;
    }
    
    static WebDataReader create(DataSource.SourceType type, AutoflushingLogger logger) {
        return new WebDataReader(type.getUrl(), logger, type.getFallback());
    }
    
    @Override
    public WebDataReader init() {
        initialized = initWriter();
        return this;
    }
    
    @Override
    public WebDataReader setPersist(boolean persist) {
        this.persist = persist;
        return this;
    }
    
    @Override
    public WebDataReader read() {
        HttpGet request = new HttpGet(url);
        String userAgent = "Mozilla/5.0";
        try (
            CloseableHttpClient client = HttpClients.custom()
                    .setUserAgent(userAgent)
                    .build();
            CloseableHttpResponse res = client.execute(request);
            BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()))
        ) {
            int statusCode = res.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                read(reader);
                logger.log(Level.INFO, "req={0},res_code={1},res_msg={2}", new Object[]{ request.toString(), res.getStatusLine().getStatusCode(), res.toString() });
            } else if (fallbackFile != null) {
                BufferedReader file = new BufferedReader(new InputStreamReader(WebDataReader.class.getResourceAsStream(fallbackFile)));
                read(file);
                logger.log(Level.INFO, "req={0},res_code={1},res_msg={2}", new Object[]{ "local-file-fallback", res.getStatusLine().getStatusCode(), res.toString() });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return this;
    }
    
    private void read(BufferedReader reader) throws Exception {
        String s;
        s = reader.readLine();
        while (s != null) {
            store(s);
            s = reader.readLine();
        }
        close();
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
    public String nextLine() {
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
