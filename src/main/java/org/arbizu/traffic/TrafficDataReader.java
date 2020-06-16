package org.arbizu.traffic;

/**
 * Interface for collecting traffic data
 * @author alan
 * @since 3/13/17.
 */
interface TrafficDataReader {
    int INIT_BUFFER_SIZE_BYTES = 512;
    TrafficDataReader init();
    TrafficDataReader read();
    TrafficDataReader setPersist(boolean doPersistence);
    void initReader() throws Exception;
    boolean hasNext() throws Exception;
    String nextLine();
    void close() throws Exception;
    void store(String value) throws Exception;
}
