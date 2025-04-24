package edu.uga.cs.rideshareapp.model;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Represents the points data for a user stored in Firebase.
 */
@IgnoreExtraProperties // Good practice for Firebase POJOs
public class Points {

    private int points; // The user's current point total

    /**
     * Default constructor required for calls to DataSnapshot.getValue(Points.class).
     * Initializes points to the default starting value (e.g., 100).
     */
    public Points() {
        this.points = 100; // Default starting points for a new user entry
    }

    /**
     * Constructor to create a Points object with a specific value.
     * @param points The initial points value.
     */
    public Points(int points) {
        this.points = points;
    }

    // --- Getter ---

    /**
     * Gets the current points value.
     * @return The integer points value.
     */
    public int getPoints() {
        return points;
    }

    // --- Setter ---

    /**
     * Sets the points value.
     * @param points The new integer points value.
     */
    public void setPoints(int points) {
        this.points = points;
    }

    // --- Optional: toString for debugging ---

    @Override
    public String toString() {
        return "Points{" +
                "points=" + points +
                '}';
    }
}
