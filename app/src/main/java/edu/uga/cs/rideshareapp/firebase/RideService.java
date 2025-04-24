package edu.uga.cs.rideshareapp.firebase;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query; // Import Query
import com.google.firebase.database.Transaction;
// Corrected import statement below:
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList; // Import ArrayList
import java.util.List; // Import List

import edu.uga.cs.rideshareapp.model.Ride;
import android.util.Log;

public class RideService {

    private final DatabaseReference dbRootRef;
    private final DatabaseReference ridesRef;
    private final DatabaseReference counterRef;
    private static final String TAG = "RideService";

    // Define the callback interface (can be outside the class too)
    public interface RideListListener {
        void onRidesFetched(List<Ride> rideOffers);
        void onError(DatabaseError databaseError);
    }

    public RideService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        dbRootRef = database.getReference();
        ridesRef = database.getReference("rides");
        counterRef = database.getReference("counters/lastRideId");
    }

    /**
     * Creates a new ride entry using an auto-incrementing integer ID.
     * Uses a Firebase Transaction to safely get the next ID.
     *
     * @param ride The Ride object containing the data (rideId will be set here).
     */
    public void createNewRide(final Ride ride) {
        if (ride == null) {
            Log.e(TAG, "Cannot create a null ride.");
            return;
        }

        // Run a transaction on the counter node
        counterRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    // Initialize counter if it doesn't exist (should be done manually first ideally)
                    currentValue = 0;
                }

                // Increment the counter
                int nextId = currentValue + 1;
                mutableData.setValue(nextId); // Set the new counter value

                // *** Store the nextId to be used outside the transaction ***
                // We pass it back via Transaction.success(mutableData) and retrieve it in onComplete
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                if (databaseError != null) {
                    // Transaction failed (e.g., network error)
                    Log.e(TAG, "Counter transaction failed.", databaseError.toException());
                    // Optionally: Add a callback or listener to notify caller of failure
                } else if (committed) {
                    // Transaction completed successfully
                    Integer newId = dataSnapshot.getValue(Integer.class);
                    if (newId != null) {
                        Log.d(TAG, "Successfully obtained new ride ID: " + newId);

                        // Set the obtained ID on the ride object
                        ride.setRideId(newId);

                        // Now save the actual ride data under /rides/{newId}
                        // Use the integer ID as the key for the ride node
                        ridesRef.child(String.valueOf(newId)).setValue(ride)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "New ride created successfully with ID: " + newId);
                                        // Optionally: Add callback/listener for success
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e(TAG, "Error creating new ride data for ID: " + newId, e);
                                        // Optionally: Add callback/listener for failure
                                        // Consider if you need to roll back the counter (complex)
                                    }
                                });
                    } else {
                        Log.e(TAG, "Failed to retrieve new ID after transaction commit, dataSnapshot is null or not an Integer.");
                    }

                } else {
                    // Transaction not committed (e.g., data changed unexpectedly during transaction)
                    // Firebase automatically retries, so this usually indicates persistent issues
                    Log.w(TAG, "Counter transaction not committed.");
                    // Optionally: Add a callback or listener to notify caller of failure
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
     */
    public void createNewRideWithStrings(String dateTime, String user, boolean isDriver, String from, String to) {
        // Basic input validation
        if (dateTime == null || user == null || from == null || to == null ||
                dateTime.isEmpty() || user.isEmpty() || from.isEmpty() || to.isEmpty()) {
            Log.e(TAG, "Invalid input provided for creating a ride.");
            // Optionally: Throw an exception or use a callback for error
            return;
        }

        Ride ride;
        try {
            // Construct the Ride object based on whether the user is a driver or rider
            // Initialize rideId with a placeholder (e.g., 0 or -1) as it will be set during save
            if (isDriver) {
                // User is the driver, rider is initially unknown/null
                ride = new Ride(dateTime, user, null, to, from, false, 0); // Corrected constructor call order & default ID
            } else {
                // User is the rider, driver is initially unknown/null
                ride = new Ride(dateTime, null, user, to, from, false, 0); // Corrected constructor call order & default ID
            }

            // Call the main createNewRide method which handles ID generation and saving
            createNewRide(ride);

        } catch (Exception e) {
            // Catch potential exceptions during Ride object creation, though unlikely here
            Log.e(TAG, "Error preparing ride object", e);
            // Optionally: Use a callback for error
        }
    }


    /**
     * Fetches all ride offers asynchronously.
     * A ride offer is defined as a ride that:
     * 1. Has a non-null/non-empty driver.
     * 2. Has a null or empty rider.
     * 3. Is not marked as complete (isComplete is false).
     * Results are returned via the provided listener.
     *
     * @param listener The callback listener to handle results or errors.
     */
    public void getAllRideOffers(final RideListListener listener) {
        if (listener == null) {
            Log.e(TAG, "Listener cannot be null for getAllRideOffers.");
            return;
        }

        // Query rides where the 'rider' field is null.
        // Firebase queries are best suited for exact matches or ranges on indexed fields.
        // Checking for non-empty driver and isComplete=false will be done client-side after fetching.
        Query query = ridesRef.orderByChild("rider").equalTo(null);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Ride> rideOffers = new ArrayList<>();
                Log.d(TAG, "getAllRideOffers: onDataChange triggered. Processing " + dataSnapshot.getChildrenCount() + " potential rides.");

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Ride ride = snapshot.getValue(Ride.class);

                        if (ride != null) {
                            // Log the raw ride data fetched by the query
                            // Log.v(TAG, "Processing ride: " + ride.toString());

                            // Apply the specific criteria for a "ride offer"
                            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
                            // Rider check should be redundant due to query, but good for safety
                            boolean noRider = ride.getRider() == null || ride.getRider().trim().isEmpty();
                            boolean notComplete = !ride.isComplete();

                            if (hasDriver && noRider && notComplete) {
                                // Ensure the rideId (which is the Firebase key) is set in the object
                                // The key might not be automatically populated inside the object by getValue()
                                // if it wasn't explicitly saved there. Let's set it from the snapshot key.
                                try {
                                    // Get the key from the snapshot (e.g., "1", "2")
                                    String rideKey = snapshot.getKey();
                                    if (rideKey != null) {
                                        int rideId = Integer.parseInt(rideKey);
                                        ride.setRideId(rideId); // Set the ID from the key
                                    } else {
                                        Log.w(TAG, "Snapshot key was null when trying to set rideId.");
                                    }
                                } catch (NumberFormatException nfe) {
                                    Log.w(TAG, "Could not parse ride key to int: " + snapshot.getKey());
                                    // Decide how to handle - skip? log? Keep the potentially incorrect ID from DB?
                                    // Let's keep the ID from the DB if parsing fails, but log it.
                                    if (ride.getRideId() == 0 && snapshot.getKey() != null) {
                                        Log.w(TAG,"Ride object has ID 0, key is: " + snapshot.getKey());
                                    }
                                }


                                rideOffers.add(ride);
                                // Log.d(TAG, "Added ride offer: ID " + ride.getRideId());
                            } else {
                                // Log why a ride fetched by the query was filtered out
                                // Log.v(TAG, "Filtered out ride ID " + snapshot.getKey() + ": hasDriver=" + hasDriver + ", noRider=" + noRider + ", notComplete=" + notComplete);
                            }
                        } else {
                            Log.w(TAG, "Fetched null Ride object for key: " + snapshot.getKey());
                        }
                    } catch (Exception e) {
                        // Catch potential errors during getValue or processing
                        Log.e(TAG, "Error processing ride snapshot: " + snapshot.getKey(), e);
                    }
                }
                // Call the listener's success method with the filtered list
                Log.d(TAG, "Finished processing. Found " + rideOffers.size() + " valid ride offers.");
                listener.onRidesFetched(rideOffers);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Call the listener's error method
                Log.e(TAG, "Firebase query cancelled or failed: ", databaseError.toException());
                listener.onError(databaseError);
            }
        });
    }

    // ... [rest of your RideService class might go here] ...

}