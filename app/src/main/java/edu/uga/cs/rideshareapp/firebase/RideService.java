package edu.uga.cs.rideshareapp.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Import Nullable

import com.google.android.gms.tasks.OnCompleteListener; // Import OnCompleteListener
import com.google.android.gms.tasks.Task; // Import Task
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap; // Import HashMap
import java.util.List;
import java.util.Map; // Import Map

import edu.uga.cs.rideshareapp.model.Ride;
import android.util.Log;

public class RideService {

    private final DatabaseReference dbRootRef;
    private final DatabaseReference ridesRef;
    private final DatabaseReference counterRef;
    private static final String TAG = "RideService";

    // --- Callback Interfaces ---

    /** Listener for asynchronous fetch operations returning a list of rides. */
    public interface RideListListener {
        void onRidesFetched(List<Ride> rides);
        void onError(DatabaseError databaseError);
    }

    /** Listener for asynchronous fetch operations returning a single ride. */
    public interface RideSingleListener {
        void onRideFetched(@Nullable Ride ride); // Ride can be null if not found
        void onError(DatabaseError databaseError);
    }

    /** Listener for asynchronous write/update/delete operations. */
    public interface CompletionListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    // --- Constructor ---

    public RideService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        dbRootRef = database.getReference(); // Not strictly needed unless transactions span multiple nodes
        ridesRef = database.getReference("rides");
        counterRef = database.getReference("counters/lastRideId");
    }

    // --- Helper Methods ---

    /**
     * Helper method to safely parse the snapshot key and set the rideId on the Ride object.
     * @param ride The Ride object to update.
     * @param rideKey The key (String) obtained from the DataSnapshot.
     */
    private void setRideIdFromKey(Ride ride, String rideKey) {
        if (ride == null || rideKey == null) return;
        try {
            int id = Integer.parseInt(rideKey);
            ride.setRideId(id);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Could not parse ride key to int: " + rideKey + ". Ride object ID remains: " + ride.getRideId());
        }
    }

    /**
     * Creates a standard OnCompleteListener for write operations to call our CompletionListener.
     * @param listener The CompletionListener to notify.
     * @param operationTag A tag for logging (e.g., "deleteRide", "updateRide").
     * @return An OnCompleteListener instance.
     */
    private OnCompleteListener<Void> createWriteCompleteListener(final CompletionListener listener, final String operationTag) {
        return task -> {
            if (listener != null) {
                if (task.isSuccessful()) {
                    Log.d(TAG, operationTag + " successful.");
                    listener.onSuccess();
                } else {
                    Log.e(TAG, operationTag + " failed.", task.getException());
                    listener.onFailure(task.getException() != null ? task.getException() : new Exception(operationTag + " failed with unknown error"));
                }
            } else {
                // Log even if no listener provided
                if (task.isSuccessful()) {
                    Log.d(TAG, operationTag + " successful (no listener).");
                } else {
                    Log.e(TAG, operationTag + " failed (no listener).", task.getException());
                }
            }
        };
    }

    // --- Create Operations ---

    /**
     * Creates a new ride entry using an auto-incrementing integer ID.
     * Uses a Firebase Transaction to safely get the next ID.
     * Notifies listener upon completion of saving the ride data.
     *
     * @param ride The Ride object containing the data (rideId will be set here).
     * @param listener Optional listener to be notified of success/failure of the final save.
     */
    public void createNewRide(final Ride ride, @Nullable final CompletionListener listener) {
        if (ride == null) {
            Log.e(TAG, "Cannot create a null ride.");
            if (listener != null) listener.onFailure(new IllegalArgumentException("Ride cannot be null"));
            return;
        }

        counterRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                mutableData.setValue((currentValue == null ? 0 : currentValue) + 1);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "Counter transaction failed.", error.toException());
                    if (listener != null) listener.onFailure(error.toException());
                } else if (committed && currentData != null) {
                    Integer newId = currentData.getValue(Integer.class);
                    if (newId != null) {
                        Log.d(TAG, "Successfully obtained new ride ID: " + newId);
                        ride.setRideId(newId); // Set ID on the object
                        // Save the ride data, passing the listener to the completion handler
                        ridesRef.child(String.valueOf(newId)).setValue(ride)
                                .addOnCompleteListener(createWriteCompleteListener(listener, "createNewRide (setValue)"));
                    } else {
                        Log.e(TAG, "Failed to retrieve new ID after transaction commit.");
                        if (listener != null) listener.onFailure(new Exception("Failed to retrieve new ID after transaction commit"));
                    }
                } else {
                    Log.w(TAG, "Counter transaction not committed.");
                    if (listener != null) listener.onFailure(new Exception("Counter transaction not committed"));
                }
            }
        });
    }

    /**
     * Creates a new Ride object from string inputs and saves it using an auto-incrementing ID.
     *
     * @param dateTime   Date and time string.
     * @param user       Email or identifier of the user posting the ride.
     * @param isDriver   True if the user is the driver, false if they are the rider.
     * @param from       Starting location.
     * @param to         Destination location.
     * @param listener   Optional listener for completion status of the save operation.
     */
    public void createNewRideWithStrings(String dateTime, String user, boolean isDriver, String from, String to, @Nullable CompletionListener listener) {
        if (dateTime == null || user == null || from == null || to == null ||
                dateTime.isEmpty() || user.isEmpty() || from.isEmpty() || to.isEmpty()) {
            Log.e(TAG, "Invalid input provided for creating a ride.");
            if (listener != null) listener.onFailure(new IllegalArgumentException("Invalid input for createNewRideWithStrings"));
            return;
        }

        Ride ride;
        try {
            // Initialize rideId with 0; it will be set by createNewRide
            if (isDriver) {
                ride = new Ride(dateTime, user, null, to, from, false, 0);
            } else {
                ride = new Ride(dateTime, null, user, to, from, false, 0);
            }
            createNewRide(ride, listener); // Pass the listener along
        } catch (Exception e) {
            Log.e(TAG, "Error preparing ride object", e);
            if (listener != null) listener.onFailure(e);
        }
    }

    // --- Read Operations ---

    /**
     * Fetches a single ride by its ID.
     * @param rideId The ID of the ride to fetch.
     * @param listener Listener to receive the ride or error.
     */
    public void getRideById(int rideId, @NonNull final RideSingleListener listener) {
        ridesRef.child(String.valueOf(rideId)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);
                    if (ride != null) {
                        setRideIdFromKey(ride, snapshot.getKey()); // Ensure ID is set
                    }
                    listener.onRideFetched(ride);
                } else {
                    Log.d(TAG, "Ride with ID " + rideId + " not found.");
                    listener.onRideFetched(null); // Not found
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching ride by ID " + rideId, error.toException());
                listener.onError(error);
            }
        });
    }


    /**
     * Fetches all ride offers (driver set, rider null, not complete).
     * @param listener Listener to receive the list of offers or error.
     */
    public void getAllRideOffers(@NonNull final RideListListener listener) {
        Query query = ridesRef.orderByChild("rider").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            // Additional client-side filtering for offers
            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
            boolean noRider = ride.getRider() == null || ride.getRider().trim().isEmpty(); // Redundant check
            boolean notComplete = !ride.isComplete();
            return hasDriver && noRider && notComplete;
        }, "getAllRideOffers"));
    }

    /**
     * Fetches all ride requests (rider set, driver null, not complete).
     * @param listener Listener to receive the list of requests or error.
     */
    public void getAllRideRequests(@NonNull final RideListListener listener) {
        Query query = ridesRef.orderByChild("driver").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            // Additional client-side filtering for requests
            boolean hasRider = ride.getRider() != null && !ride.getRider().trim().isEmpty();
            boolean noDriver = ride.getDriver() == null || ride.getDriver().trim().isEmpty(); // Redundant check
            boolean notComplete = !ride.isComplete();
            return hasRider && noDriver && notComplete;
        }, "getAllRideRequests"));
    }

    /**
     * Fetches all accepted rides (driver set, rider set, not complete).
     * @param listener Listener to receive the list of accepted rides or error.
     */
    public void getAllAcceptedRides(@NonNull final RideListListener listener) {
        // Query by isComplete=false, filter the rest client-side
        Query query = ridesRef.orderByChild("complete").equalTo(false); // Note: Firebase stores boolean as 'complete'
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            // Client-side filtering for accepted rides
            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
            boolean hasRider = ride.getRider() != null && !ride.getRider().trim().isEmpty();
            boolean notComplete = !ride.isComplete(); // Should be true from query
            return hasDriver && hasRider && notComplete;
        }, "getAllAcceptedRides"));
    }

    // --- Helper for creating ValueEventListeners for lists ---
    private interface RideFilter {
        boolean shouldInclude(Ride ride);
    }

    private ValueEventListener createListValueEventListener(@NonNull final RideListListener listener, @NonNull final RideFilter filter, final String operationTag) {
        return new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Ride> rides = new ArrayList<>();
                Log.d(TAG, operationTag + ": onDataChange processing " + dataSnapshot.getChildrenCount() + " potential rides.");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Ride ride = snapshot.getValue(Ride.class);
                        if (ride != null) {
                            setRideIdFromKey(ride, snapshot.getKey()); // Ensure ID is set
                            if (filter.shouldInclude(ride)) { // Apply filter
                                rides.add(ride);
                            }
                        } else {
                            Log.w(TAG, operationTag + ": Fetched null Ride object for key: " + snapshot.getKey());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, operationTag + ": Error processing snapshot: " + snapshot.getKey(), e);
                    }
                }
                Log.d(TAG, operationTag + ": Finished processing. Found " + rides.size() + " matching rides.");
                listener.onRidesFetched(rides);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase query cancelled or failed (" + operationTag + "): ", databaseError.toException());
                listener.onError(databaseError);
            }
        };
    }

    // --- Update Operations ---

    /**
     * Updates an entire existing ride. Replaces all data at /rides/{rideId}.
     * Ensures the rideId within the object matches the path ID.
     * @param rideId The ID of the ride to update.
     * @param updatedRideData A Ride object containing the complete new data.
     * @param listener Optional listener for completion status.
     */
    public void updateRide(int rideId, @NonNull Ride updatedRideData, @Nullable CompletionListener listener) {
        if (updatedRideData.getRideId() != 0 && updatedRideData.getRideId() != rideId) {
            Log.w(TAG, "updateRide: Mismatch between path rideId (" + rideId + ") and object rideId (" + updatedRideData.getRideId() + "). Using path ID.");
        }
        // Ensure the ID within the object matches the path ID we are writing to
        updatedRideData.setRideId(rideId);

        ridesRef.child(String.valueOf(rideId)).setValue(updatedRideData)
                .addOnCompleteListener(createWriteCompleteListener(listener, "updateRide"));
    }

    /**
     * Accepts a ride offer or request by adding the missing driver or rider.
     * @param rideId The ID of the ride to accept.
     * @param acceptingUserEmail The email of the user accepting the ride.
     * @param listener Optional listener for completion status.
     */
    public void acceptRide(final int rideId, @NonNull final String acceptingUserEmail, @Nullable final CompletionListener listener) {
        if (acceptingUserEmail.trim().isEmpty()) {
            Log.e(TAG, "acceptRide: Accepting user email cannot be empty.");
            if (listener != null) listener.onFailure(new IllegalArgumentException("Accepting user email cannot be empty"));
            return;
        }

        DatabaseReference rideNodeRef = ridesRef.child(String.valueOf(rideId));

        rideNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "acceptRide: Ride with ID " + rideId + " not found.");
                    if (listener != null) listener.onFailure(new Exception("Ride not found"));
                    return;
                }

                Ride ride = snapshot.getValue(Ride.class);
                if (ride == null) {
                    Log.e(TAG, "acceptRide: Could not deserialize ride data for ID " + rideId);
                    if (listener != null) listener.onFailure(new Exception("Could not read ride data"));
                    return;
                }

                // Determine if accepting as driver or rider
                Map<String, Object> updates = new HashMap<>();
                boolean needsUpdate = false;

                if (ride.getDriver() != null && !ride.getDriver().trim().isEmpty() &&
                        (ride.getRider() == null || ride.getRider().trim().isEmpty())) {
                    // Existing driver, accepting as RIDER
                    Log.d(TAG, "acceptRide: Accepting ride " + rideId + " as RIDER (" + acceptingUserEmail + ")");
                    updates.put("rider", acceptingUserEmail);
                    needsUpdate = true;
                } else if (ride.getRider() != null && !ride.getRider().trim().isEmpty() &&
                        (ride.getDriver() == null || ride.getDriver().trim().isEmpty())) {
                    // Existing rider, accepting as DRIVER
                    Log.d(TAG, "acceptRide: Accepting ride " + rideId + " as DRIVER (" + acceptingUserEmail + ")");
                    updates.put("driver", acceptingUserEmail);
                    needsUpdate = true;
                } else {
                    // Ride might already be accepted or in an invalid state
                    Log.w(TAG, "acceptRide: Ride " + rideId + " is not in a state to be accepted (already full or invalid).");
                    if (listener != null) listener.onFailure(new IllegalStateException("Ride cannot be accepted in its current state"));
                    return;
                }

                if (needsUpdate) {
                    // Perform the update using updateChildren
                    rideNodeRef.updateChildren(updates)
                            .addOnCompleteListener(createWriteCompleteListener(listener, "acceptRide (updateChildren)"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "acceptRide: Failed to read ride data for ID " + rideId, error.toException());
                if (listener != null) listener.onFailure(error.toException());
            }
        });
    }

    /**
     * Marks a ride as complete by setting the 'isComplete' flag to true.
     * @param rideId The ID of the ride to complete.
     * @param listener Optional listener for completion status.
     */
    public void completeRide(int rideId, @Nullable CompletionListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("complete", true); // Use "complete" as likely stored by Firebase for boolean 'isComplete'

        ridesRef.child(String.valueOf(rideId)).updateChildren(updates)
                .addOnCompleteListener(createWriteCompleteListener(listener, "completeRide"));
    }

    // --- Delete Operation ---

    /**
     * Deletes a ride by its ID.
     * @param rideId The ID of the ride to delete.
     * @param listener Optional listener for completion status.
     */
    public void deleteRide(int rideId, @Nullable CompletionListener listener) {
        ridesRef.child(String.valueOf(rideId)).removeValue()
                .addOnCompleteListener(createWriteCompleteListener(listener, "deleteRide"));
    }

}
