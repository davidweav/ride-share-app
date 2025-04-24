package edu.uga.cs.rideshareapp.model;

public class Ride {
    private String dateTime;
    private String driver;
    private String rider;
    private String to;
    private String from;
    private boolean isComplete;
    private int rideId;

    public Ride() {
    }

    public Ride(String dateTime, String driver, String rider, String to, String from,  boolean isComplete, int rideId) {
        this.dateTime = dateTime;
        this.driver = driver;
        this.rider = rider;
        this.to = to;
        this.from = from;
        this.isComplete = isComplete;
        this.rideId = rideId;
    }

    // --- Getters ---
    public String getDateTime() { return dateTime; }
    public String getDriver() { return driver; }
    public String getFrom() { return from; }
    public String getRider() { return rider; }
    public String getTo() { return to; }
    public boolean isComplete() { return isComplete; }
    public int getRideId() { return rideId; }

    // --- Setters ---
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
    public void setDriver(String driver) { this.driver = driver; }
    public void setFrom(String from) { this.from = from; }
    public void setRider(String rider) { this.rider = rider; }
    public void setTo(String to) { this.to = to; }
    public void setComplete(boolean complete) { isComplete = complete; }
    public void setRideId(int rideId) { this.rideId = rideId; }

    @Override
    public String toString() {
        return "Ride{" +
                "dateTime='" + dateTime + '\'' +
                ", driver='" + driver + '\'' +
                ", from='" + from + '\'' +
                ", rider='" + rider + '\'' +
                ", to='" + to + '\'' +
                ", isComplete=" + isComplete +
                ", rideId='" + rideId + '\'' +
                '}';
    }
}
