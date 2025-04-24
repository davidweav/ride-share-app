package edu.uga.cs.rideshareapp.model;

import com.google.firebase.database.Exclude; // Import Exclude
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Ride {
    private String dateTime;
    private String driver; // Will be included in map even if null
    private String rider;  // Will be included in map even if null
    private String to;
    private String from;
    private boolean isComplete; // Firebase maps this to 'complete' by default
    private int rideId;

    // Default constructor required for Firebase
    public Ride() {
    }

    // Constructor used when creating a new ride instance in code
    public Ride(String dateTime, String driver, String rider, String to, String from, boolean isComplete, int rideId) {
        this.dateTime = dateTime;
        this.driver = driver;
        this.rider = rider;
        this.to = to;
        this.from = from;
        this.isComplete = isComplete;
        this.rideId = rideId; // rideId is often set later or is the key itself
    }

    // --- Getters ---
    public String getDateTime() { return dateTime; }
    public String getDriver() { return driver; }
    public String getRider() { return rider; }
    public String getTo() { return to; }
    public String getFrom() { return from; }
    public boolean isComplete() { return isComplete; } // Getter for boolean
    // Note: Firebase uses the key as the ID, so this getter might not reflect the key
    // unless explicitly set after fetching or before saving as a field.
    public int getRideId() { return rideId; }

    // --- Setters ---
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
    public void setDriver(String driver) { this.driver = driver; }
    public void setRider(String rider) { this.rider = rider; }
    public void setTo(String to) { this.to = to; }
    public void setFrom(String from) { this.from = from; }
    public void setComplete(boolean complete) { isComplete = complete; }
    public void setRideId(int rideId) { this.rideId = rideId; }

    /**
     * Converts the Ride object to a Map suitable for Firebase Realtime Database updates.
     * This ensures that keys for null fields (like driver or rider) are explicitly included.
     * The rideId field is excluded as it's typically the key of the node, not a value within it.
     * @return A Map representing the Ride object's data.
     */
    @Exclude // Exclude this method from being treated as a Firebase property
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("dateTime", dateTime);
        result.put("driver", driver); // Include even if null
        result.put("rider", rider);   // Include even if null
        result.put("to", to);
        result.put("from", from);
        result.put("complete", isComplete); // Use 'complete' key matching Firebase default for boolean getter 'isComplete'
        result.put("rideId", rideId); // Include rideId as a field within the data

        return result;
    }


    @Override
    public String toString() {
        return "Ride{" +
                "rideId=" + rideId +
                ", dateTime='" + dateTime + '\'' +
                ", driver='" + driver + '\'' +
                ", rider='" + rider + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", isComplete=" + isComplete +
                '}';
    }
}
