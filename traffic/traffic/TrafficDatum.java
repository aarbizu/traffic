package traffic;

/**
 * Traffic data point
 * Created by alan on 3/13/17.
 */
class TrafficDatum {
    private int speed;
    private String incident = null;
    private String locationName;
    
    void setSpeed(int speed) {
        this.speed = speed;
    }
    
    void setIncident(String incident) {
        this.incident = incident;
    }
    
    int getSpeed() {
        return this.speed;
    }
    
    public String getIncident() {
        return this.incident;
    }
    
    public boolean hasIncident() {
        return (incident != null);
    }
    
    public String getLocationName() { return this.locationName; }
    
    public void setLocationName(String location) { this.locationName = location; }
    
    @Override
    public String toString() {
        if (incident == null) {
            return String.valueOf(speed);
        } else {
            return String.valueOf(speed) + " - " + incident;
        }
    }
    
}
