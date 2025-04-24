package edu.uga.cs.rideshareapp.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth; // Import FirebaseAuth
import com.google.firebase.auth.FirebaseUser; // Import FirebaseUser
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects; // Import Objects for equals check

import edu.uga.cs.rideshareapp.model.Ride;
import android.util.Log;

public class RideService {

    private final DatabaseReference dbRootRef;
    private final DatabaseReference ridesRef;
    private final DatabaseReference counterRef;
    private final FirebaseAuth firebaseAuth; // Added FirebaseAuth instance
    private static final String TAG = "RideService";

    // --- Callback Interfaces ---

    public interface RideListListener {
        void onRidesFetched(List<Ride> rides);
        void onError(DatabaseError databaseError);
    }

    public interface RideSingleListener {
        void onRideFetched(@Nullable Ride ride);
        void onError(DatabaseError databaseError);
    }

    public interface CompletionListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    // --- Constructor ---

    public RideService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance(); // Get instance here
        this.dbRootRef = database.getReference();
        this.ridesRef = database.getReference("rides");
        this.counterRef = database.getReference("counters/lastRideId");
    }

    // --- Helper Methods ---

    private void setRideIdFromKey(Ride ride, String rideKey) {
        if (ride == null || rideKey == null) return;
        try {
            int id = Integer.parseInt(rideKey);
            ride.setRideId(id);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Could not parse ride key to int: " + rideKey);
        }
    }

    private OnCompleteListener<Void> createWriteCompleteListener(@Nullable final CompletionListener listener, final String operationTag) {
        return task -> {
            if (listener != null) {
                if (task.isSuccessful()) {
                    Log.d(TAG, operationTag + " successful.");
                    listener.onSuccess();
                } else {
                    Exception e = task.getException() != null ? task.getException() : new Exception(operationTag + " failed with unknown error");
                    Log.e(TAG, operationTag + " failed.", e);
                    listener.onFailure(e);
                }
            } else { // Log even without listener
                if (!task.isSuccessful()) {
                    Log.e(TAG, operationTag + " failed (no listener).", task.getException());
                }
            }
        };
    }

    /** Gets current user's email or calls listener.onFailure if not logged in. */
    @Nullable
    private String getCurrentUserEmail(@Nullable CompletionListener listener) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Operation failed: User not logged in.");
            if (listener != null) listener.onFailure(new SecurityException("User not logged in"));
            return null;
        }
        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            Log.e(TAG, "Operation failed: User email is missing.");
            if (listener != null) listener.onFailure(new IllegalStateException("User email is missing"));
            return null;
        }
        return email;
    }

    /** Gets current user's email or calls listener.onFailure if not logged in (for read operations). */
    @Nullable
    private String getCurrentUserEmailForRead(@Nullable RideSingleListener listener) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Operation failed: User not logged in.");
            if (listener != null) listener.onError(DatabaseError.fromException(new SecurityException("User not logged in")));
            return null;
        }
        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            Log.e(TAG, "Operation failed: User email is missing.");
            if (listener != null) listener.onError(DatabaseError.fromException(new IllegalStateException("User email is missing")));
            return null;
        }
        return email;
    }


    // --- Create Operations ---

    /** Internal method to create ride once ID is obtained. */
    private void saveRideData(final Ride ride, final int newId, @Nullable final CompletionListener listener) {
        ride.setRideId(newId); // Set ID on the object
        ridesRef.child(String.valueOf(newId)).setValue(ride)
                .addOnCompleteListener(createWriteCompleteListener(listener, "createNewRide (setValue)"));
    }

    /** Creates a new ride entry using an auto-incrementing ID. */
    public void createNewRide(final Ride ride, @Nullable final CompletionListener listener) {
        if (ride == null) {
            Log.e(TAG, "Cannot create a null ride.");
            if (listener != null) listener.onFailure(new IllegalArgumentException("Ride cannot be null"));
            return;
        }
        // Ensure creator is set correctly based on ride data
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return; // Failure handled in helper

        if (ride.getDriver() != null && !ride.getDriver().isEmpty() && !ride.getDriver().equals(currentUserEmail)) {
            Log.e(TAG, "Attempting to create ride offer for different user: " + ride.getDriver());
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride offer for another user"));
            return;
        }
        if (ride.getRider() != null && !ride.getRider().isEmpty() && !ride.getRider().equals(currentUserEmail)) {
            Log.e(TAG, "Attempting to create ride request for different user: " + ride.getRider());
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride request for another user"));
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
                        saveRideData(ride, newId, listener); // Call helper to save
                    } else {
                        Log.e(TAG, "Failed to retrieve new ID after transaction commit.");
                        if (listener != null) listener.onFailure(new Exception("Failed to retrieve new ID"));
                    }
                } else {
                    Log.w(TAG, "Counter transaction not committed.");
                    if (listener != null) listener.onFailure(new Exception("Counter transaction not committed"));
                }
            }
        });
    }

    /** Creates a new Ride from strings, automatically using the current user. */
    public void createNewRideWithStrings(String dateTime, boolean isDriver, String from, String to, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) {
            return; // Failure already handled by helper
        }

        if (dateTime == null || from == null || to == null ||
                dateTime.isEmpty() || from.isEmpty() || to.isEmpty()) {
            Log.e(TAG, "Invalid input provided for creating a ride.");
            if (listener != null) listener.onFailure(new IllegalArgumentException("Invalid input for createNewRideWithStrings"));
            return;
        }

        Ride ride;
        try {
            if (isDriver) { // User is creating an OFFER
                ride = new Ride(dateTime, currentUserEmail, null, to, from, false, 0);
            } else { // User is creating a REQUEST
                ride = new Ride(dateTime, null, currentUserEmail, to, from, false, 0);
            }
            createNewRide(ride, listener); // Call the main create method
        } catch (Exception e) {
            Log.e(TAG, "Error preparing ride object", e);
            if (listener != null) listener.onFailure(e);
        }
    }

    // --- Read Operations ---

    public void getRideById(int rideId, @NonNull final RideSingleListener listener) {
        ridesRef.child(String.valueOf(rideId)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);
                    if (ride != null) setRideIdFromKey(ride, snapshot.getKey());
                    listener.onRideFetched(ride);
                } else {
                    listener.onRideFetched(null); // Not found
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { listener.onError(error); }
        });
    }

    public void getAllRideOffers(@NonNull final RideListListener listener) {
        Query query = ridesRef.orderByChild("rider").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride ->
                        (ride.getDriver() != null && !ride.getDriver().trim().isEmpty()) && !ride.isComplete()
                , "getAllRideOffers"));
    }

    public void getAllRideRequests(@NonNull final RideListListener listener) {
        Query query = ridesRef.orderByChild("driver").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride ->
                        (ride.getRider() != null && !ride.getRider().trim().isEmpty()) && !ride.isComplete()
                , "getAllRideRequests"));
    }

    public void getAllAcceptedRides(@NonNull final RideListListener listener) {
        Query query = ridesRef.orderByChild("complete").equalTo(false);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride ->
                        (ride.getDriver() != null && !ride.getDriver().trim().isEmpty()) &&
                                (ride.getRider() != null && !ride.getRider().trim().isEmpty()) &&
                                !ride.isComplete() // Redundant check due to query, but safe
                , "getAllAcceptedRides"));
    }

    // Helper for creating ValueEventListeners for lists
    private interface RideFilter { boolean shouldInclude(Ride ride); }

    private ValueEventListener createListValueEventListener(@NonNull final RideListListener listener, @NonNull final RideFilter filter, final String opTag) {
        return new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Ride> rides = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Ride ride = snapshot.getValue(Ride.class);
                        if (ride != null) {
                            setRideIdFromKey(ride, snapshot.getKey());
                            if (filter.shouldInclude(ride)) rides.add(ride);
                        }
                    } catch (Exception e) { Log.e(TAG, opTag + ": Error processing snapshot: " + snapshot.getKey(), e); }
                }
                listener.onRidesFetched(rides);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { listener.onError(error); }
        };
    }

    // --- Update Operations ---

    /** Updates an entire existing ride. Requires current user to be driver or rider. */
    public void updateRide(int rideId, @NonNull Ride updatedRideData, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride existingRide) {
                if (existingRide == null) {
                    if (listener != null) listener.onFailure(new Exception("Ride not found"));
                    return;
                }
                // Authorization check
                if (!Objects.equals(currentUserEmail, existingRide.getDriver()) && !Objects.equals(currentUserEmail, existingRide.getRider())) {
                    Log.w(TAG, "User " + currentUserEmail + " not authorized to update ride " + rideId);
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized to update this ride"));
                    return;
                }

                // Proceed with update
                if (updatedRideData.getRideId() != 0 && updatedRideData.getRideId() != rideId) {
                    Log.w(TAG, "updateRide: Correcting object rideId (" + updatedRideData.getRideId() + ") to match path ID (" + rideId + ").");
                }
                updatedRideData.setRideId(rideId); // Ensure ID consistency

                ridesRef.child(String.valueOf(rideId)).setValue(updatedRideData)
                        .addOnCompleteListener(createWriteCompleteListener(listener, "updateRide"));
            }
            @Override public void onError(DatabaseError databaseError) {
                if (listener != null) listener.onFailure(databaseError.toException());
            }
        });
    }

    /** Accepts a ride offer/request. Current user becomes the missing driver/rider. */
    public void acceptRide(final int rideId, @Nullable final CompletionListener listener) {
        final String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return; // Not logged in

        DatabaseReference rideNodeRef = ridesRef.child(String.valueOf(rideId));
        rideNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (listener != null) listener.onFailure(new Exception("Ride not found")); return;
                }
                Ride ride = snapshot.getValue(Ride.class);
                if (ride == null) {
                    if (listener != null) listener.onFailure(new Exception("Could not read ride data")); return;
                }

                Map<String, Object> updates = new HashMap<>();
                boolean isAcceptingOffer = false; // Accepting an offer (becoming rider)
                boolean isAcceptingRequest = false; // Accepting a request (becoming driver)

                // Check if ride is an OFFER (has driver, needs rider)
                if (ride.getDriver() != null && !ride.getDriver().trim().isEmpty() &&
                        (ride.getRider() == null || ride.getRider().trim().isEmpty())) {
                    // Check user is not the driver already
                    if (Objects.equals(currentUserEmail, ride.getDriver())) {
                        if (listener != null) listener.onFailure(new IllegalStateException("Cannot accept your own ride offer")); return;
                    }
                    updates.put("rider", currentUserEmail);
                    isAcceptingOffer = true;
                }
                // Check if ride is a REQUEST (has rider, needs driver)
                else if (ride.getRider() != null && !ride.getRider().trim().isEmpty() &&
                        (ride.getDriver() == null || ride.getDriver().trim().isEmpty())) {
                    // Check user is not the rider already
                    if (Objects.equals(currentUserEmail, ride.getRider())) {
                        if (listener != null) listener.onFailure(new IllegalStateException("Cannot accept your own ride request")); return;
                    }
                    updates.put("driver", currentUserEmail);
                    isAcceptingRequest = true;
                }
                // Else: Ride is not available for acceptance
                else {
                    if (listener != null) listener.onFailure(new IllegalStateException("Ride cannot be accepted")); return;
                }

                // Perform update
                Log.d(TAG, "User " + currentUserEmail + " accepting ride " + rideId +
                        (isAcceptingOffer ? " as RIDER" : "") + (isAcceptingRequest ? " as DRIVER" : ""));
                rideNodeRef.updateChildren(updates)
                        .addOnCompleteListener(createWriteCompleteListener(listener, "acceptRide"));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (listener != null) listener.onFailure(error.toException());
            }
        });
    }

    /** Marks a ride as complete. Requires current user to be driver or rider. */
    public void completeRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) {
                    if (listener != null) listener.onFailure(new Exception("Ride not found")); return;
                }
                // Authorization check
                if (!Objects.equals(currentUserEmail, ride.getDriver()) && !Objects.equals(currentUserEmail, ride.getRider())) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized to complete this ride")); return;
                }
                // Check if already complete
                if (ride.isComplete()) {
                    Log.w(TAG, "Ride " + rideId + " is already complete.");
                    if (listener != null) listener.onSuccess(); // Treat as success if already done
                    return;
                }

                // Proceed with completion
                Map<String, Object> updates = new HashMap<>();
                updates.put("complete", true);
                ridesRef.child(String.valueOf(rideId)).updateChildren(updates)
                        .addOnCompleteListener(createWriteCompleteListener(listener, "completeRide"));
            }
            @Override public void onError(DatabaseError databaseError) {
                if (listener != null) listener.onFailure(databaseError.toException());
            }
        });
    }

    // --- Delete Operation ---

    /** Deletes a ride. Requires current user to be driver or rider. */
    public void deleteRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return; // Not logged in

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) {
                    // Ride doesn't exist, consider it a success for deletion? Or failure?
                    // Let's treat "not found" as success for deletion idempotency.
                    Log.w(TAG, "deleteRide: Ride " + rideId + " not found, treating as success.");
                    if (listener != null) listener.onSuccess();
                    return;
                }

                // Authorization check: Must be the driver or the rider
                if (!Objects.equals(currentUserEmail, ride.getDriver()) && !Objects.equals(currentUserEmail, ride.getRider())) {
                    Log.w(TAG, "User " + currentUserEmail + " not authorized to delete ride " + rideId);
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized to delete this ride"));
                    return;
                }

                // Proceed with deletion
                Log.d(TAG, "User " + currentUserEmail + " deleting ride " + rideId);
                ridesRef.child(String.valueOf(rideId)).removeValue()
                        .addOnCompleteListener(createWriteCompleteListener(listener, "deleteRide"));
            }

            @Override public void onError(DatabaseError databaseError) {
                Log.e(TAG, "deleteRide: Error fetching ride " + rideId + " for authorization check.", databaseError.toException());
                if (listener != null) listener.onFailure(databaseError.toException());
            }
        });
    }
}
