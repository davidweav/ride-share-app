package edu.uga.cs.rideshareapp.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.Objects;

// Import the updated Ride class
import edu.uga.cs.rideshareapp.model.Ride;
import android.util.Log;

public class RideService {

    private final DatabaseReference dbRootRef;
    private final DatabaseReference ridesRef;
    private final DatabaseReference counterRef;
    private final FirebaseAuth firebaseAuth;
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
        this.firebaseAuth = FirebaseAuth.getInstance();
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

    /** Gets current user's email or calls listener.onFailure if not logged in (for write operations). */
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

    /** Gets current user's email or calls listener.onError if not logged in (for read operations). */
    @Nullable
    private String getCurrentUserEmailForRead(@NonNull RideListListener listener) { // Changed listener type
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Operation failed: User not logged in.");
            // Use DatabaseError for consistency with listener type
            listener.onError(DatabaseError.fromException(new SecurityException("User not logged in")));
            return null;
        }
        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            Log.e(TAG, "Operation failed: User email is missing.");
            listener.onError(DatabaseError.fromException(new IllegalStateException("User email is missing")));
            return null;
        }
        return email;
    }

    @Nullable
    private String getCurrentUserEmailForRead(@NonNull RideSingleListener listener) { // Overload for single listener
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Operation failed: User not logged in.");
            listener.onError(DatabaseError.fromException(new SecurityException("User not logged in")));
            return null;
        }
        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            Log.e(TAG, "Operation failed: User email is missing.");
            listener.onError(DatabaseError.fromException(new IllegalStateException("User email is missing")));
            return null;
        }
        return email;
    }


    // --- Create Operations ---

    /** Internal method to create ride once ID is obtained. Uses toMap(). */
    private void saveRideData(final Ride ride, final int newId, @Nullable final CompletionListener listener) {
        ride.setRideId(newId); // Ensure ID is set on the object before mapping
        ridesRef.child(String.valueOf(newId)).setValue(ride.toMap())
                .addOnCompleteListener(createWriteCompleteListener(listener, "createNewRide (setValue)"));
    }

    /** Creates a new ride entry using an auto-incrementing ID. */
    public void createNewRide(final Ride ride, @Nullable final CompletionListener listener) {
        if (ride == null) { if (listener != null) listener.onFailure(new IllegalArgumentException("Ride cannot be null")); return; }
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;
        // Authorization checks...
        if (ride.getDriver() != null && !ride.getDriver().isEmpty() && !ride.getDriver().equals(currentUserEmail)) {
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride offer for another user")); return;
        }
        if (ride.getRider() != null && !ride.getRider().isEmpty() && !ride.getRider().equals(currentUserEmail)) {
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride request for another user")); return;
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
                if (error != null) { if (listener != null) listener.onFailure(error.toException()); }
                else if (committed && currentData != null) {
                    Integer newId = currentData.getValue(Integer.class);
                    if (newId != null) { saveRideData(ride, newId, listener); }
                    else { if (listener != null) listener.onFailure(new Exception("Failed to retrieve new ID")); }
                } else { if (listener != null) listener.onFailure(new Exception("Counter transaction not committed")); }
            }
        });
    }

    /** Creates a new Ride from strings, automatically using the current user. */
    public void createNewRideWithStrings(String dateTime, boolean isDriver, String from, String to, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;
        if (dateTime == null || from == null || to == null || dateTime.isEmpty() || from.isEmpty() || to.isEmpty()) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Invalid input")); return;
        }
        Ride ride;
        try {
            if (isDriver) { ride = new Ride(dateTime, currentUserEmail, null, to, from, false, 0); }
            else { ride = new Ride(dateTime, null, currentUserEmail, to, from, false, 0); }
            createNewRide(ride, listener);
        } catch (Exception e) { if (listener != null) listener.onFailure(e); }
    }

    // --- Read Operations ---

    public void getRideById(int rideId, @NonNull final RideSingleListener listener) {
        ridesRef.child(String.valueOf(rideId)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);
                    if (ride != null) setRideIdFromKey(ride, snapshot.getKey());
                    listener.onRideFetched(ride);
                } else { listener.onRideFetched(null); }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { listener.onError(error); }
        });
    }

    /**
     * Fetches ride offers (driver set, rider null, not complete).
     * @param excludeCurrentUser If true, excludes offers posted by the current user.
     * @param listener Listener to receive the list of offers or error.
     */
    public void getAllRideOffers(boolean excludeCurrentUser, @NonNull final RideListListener listener) {
        final String currentUserEmail = excludeCurrentUser ? getCurrentUserEmailForRead(listener) : null;
        // If excluding user and user isn't logged in/email missing, the helper calls onError and returns null
        if (excludeCurrentUser && currentUserEmail == null) return;

        Query query = ridesRef.orderByChild("rider").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            // Base criteria for an offer
            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
            boolean noRider = ride.getRider() == null || ride.getRider().trim().isEmpty();
            boolean notComplete = !ride.isComplete();

            // Apply exclusion filter if requested
            boolean shouldExclude = excludeCurrentUser && Objects.equals(ride.getDriver(), currentUserEmail);

            return hasDriver && noRider && notComplete && !shouldExclude;
        }, "getAllRideOffers"));
    }

    /**
     * Fetches ride requests (rider set, driver null, not complete).
     * @param excludeCurrentUser If true, excludes requests posted by the current user.
     * @param listener Listener to receive the list of requests or error.
     */
    public void getAllRideRequests(boolean excludeCurrentUser, @NonNull final RideListListener listener) {
        final String currentUserEmail = excludeCurrentUser ? getCurrentUserEmailForRead(listener) : null;
        // If excluding user and user isn't logged in/email missing, the helper calls onError and returns null
        if (excludeCurrentUser && currentUserEmail == null) return;

        Query query = ridesRef.orderByChild("driver").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            // Base criteria for a request
            boolean hasRider = ride.getRider() != null && !ride.getRider().trim().isEmpty();
            boolean noDriver = ride.getDriver() == null || ride.getDriver().trim().isEmpty();
            boolean notComplete = !ride.isComplete();

            // Apply exclusion filter if requested
            boolean shouldExclude = excludeCurrentUser && Objects.equals(ride.getRider(), currentUserEmail);

            return hasRider && noDriver && notComplete && !shouldExclude;
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
                Log.d(TAG, opTag + ": Found " + rides.size() + " matching rides after filtering.");
                listener.onRidesFetched(rides);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, opTag + ": Firebase query cancelled or failed.", error.toException());
                listener.onError(error);
            }
        };
    }

    // --- Update Operations ---

    /** Updates an entire existing ride using toMap(). Requires current user to be driver or rider. */
    public void updateRide(int rideId, @NonNull Ride updatedRideData, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride existingRide) {
                if (existingRide == null) { if (listener != null) listener.onFailure(new Exception("Ride not found")); return; }
                if (!Objects.equals(currentUserEmail, existingRide.getDriver()) && !Objects.equals(currentUserEmail, existingRide.getRider())) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized")); return;
                }
                updatedRideData.setRideId(rideId);
                ridesRef.child(String.valueOf(rideId)).setValue(updatedRideData.toMap())
                        .addOnCompleteListener(createWriteCompleteListener(listener, "updateRide"));
            }
            @Override public void onError(DatabaseError databaseError) { if (listener != null) listener.onFailure(databaseError.toException()); }
        });
    }

    /** Accepts a ride offer/request. Current user becomes the missing driver/rider. */
    public void acceptRide(final int rideId, @Nullable final CompletionListener listener) {
        final String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        DatabaseReference rideNodeRef = ridesRef.child(String.valueOf(rideId));
        rideNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) { if (listener != null) listener.onFailure(new Exception("Ride not found")); return; }
                Ride ride = snapshot.getValue(Ride.class);
                if (ride == null) { if (listener != null) listener.onFailure(new Exception("Could not read ride data")); return; }

                Map<String, Object> updates = new HashMap<>();
                boolean isAcceptingOffer = false, isAcceptingRequest = false;

                if (ride.getDriver() != null && !ride.getDriver().trim().isEmpty() && (ride.getRider() == null || ride.getRider().trim().isEmpty())) {
                    if (Objects.equals(currentUserEmail, ride.getDriver())) { if (listener != null) listener.onFailure(new IllegalStateException("Cannot accept own offer")); return; }
                    updates.put("rider", currentUserEmail); isAcceptingOffer = true;
                } else if (ride.getRider() != null && !ride.getRider().trim().isEmpty() && (ride.getDriver() == null || ride.getDriver().trim().isEmpty())) {
                    if (Objects.equals(currentUserEmail, ride.getRider())) { if (listener != null) listener.onFailure(new IllegalStateException("Cannot accept own request")); return; }
                    updates.put("driver", currentUserEmail); isAcceptingRequest = true;
                } else { if (listener != null) listener.onFailure(new IllegalStateException("Ride cannot be accepted")); return; }

                Log.d(TAG, "User " + currentUserEmail + " accepting ride " + rideId + (isAcceptingOffer ? " as RIDER" : "") + (isAcceptingRequest ? " as DRIVER" : ""));
                rideNodeRef.updateChildren(updates).addOnCompleteListener(createWriteCompleteListener(listener, "acceptRide"));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { if (listener != null) listener.onFailure(error.toException()); }
        });
    }

    /** Marks a ride as complete. Requires current user to be driver or rider. */
    public void completeRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) { if (listener != null) listener.onFailure(new Exception("Ride not found")); return; }
                if (!Objects.equals(currentUserEmail, ride.getDriver()) && !Objects.equals(currentUserEmail, ride.getRider())) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized")); return;
                }
                if (ride.isComplete()) { if (listener != null) listener.onSuccess(); return; } // Already done

                Map<String, Object> updates = new HashMap<>();
                updates.put("complete", true);
                ridesRef.child(String.valueOf(rideId)).updateChildren(updates)
                        .addOnCompleteListener(createWriteCompleteListener(listener, "completeRide"));
            }
            @Override public void onError(DatabaseError databaseError) { if (listener != null) listener.onFailure(databaseError.toException()); }
        });
    }

    // --- Delete Operation ---

    /** Deletes a ride. Requires current user to be driver or rider. */
    public void deleteRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) { if (listener != null) listener.onSuccess(); return; } // Treat not found as success for delete

                if (!Objects.equals(currentUserEmail, ride.getDriver()) && !Objects.equals(currentUserEmail, ride.getRider())) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized")); return;
                }
                Log.d(TAG, "User " + currentUserEmail + " deleting ride " + rideId);
                ridesRef.child(String.valueOf(rideId)).removeValue()
                        .addOnCompleteListener(createWriteCompleteListener(listener, "deleteRide"));
            }
            @Override public void onError(DatabaseError databaseError) { if (listener != null) listener.onFailure(databaseError.toException()); }
        });
    }
}
