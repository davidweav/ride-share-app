package edu.uga.cs.rideshareapp.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Firebase Imports
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

// Java Util Imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
// Removed unused concurrency imports related to blocking await


// Local Model Imports
import edu.uga.cs.rideshareapp.model.Points;
import edu.uga.cs.rideshareapp.model.Ride;

// Android Util Imports
import android.util.Log;

public class RideService {

    private final DatabaseReference dbRootRef;
    private final DatabaseReference ridesRef;
    private final DatabaseReference userPointsRef; // Reference for user points
    private final DatabaseReference counterRef;
    private final FirebaseAuth firebaseAuth;
    private static final String TAG = "RideService";

    // Point constants
    private static final int POINTS_FOR_COMPLETED_OFFER = 50; // Points awarded to driver on completion
    private static final int POINTS_FOR_REQUEST = -50;     // Cost for requesting
    private static final int STARTING_POINTS = 100;

    // --- Callback Interfaces (with method definitions) ---
    public interface RideListListener {
        void onRidesFetched(List<Ride> rides);
        void onError(DatabaseError databaseError);
    }
    public interface RideSingleListener {
        void onRideFetched(@Nullable Ride ride);
        void onError(DatabaseError databaseError);
    }
    public interface CompletionListener {
        void onSuccess(); // Method definition added
        void onFailure(Exception e); // Method definition added
    }
    public interface PointsFetchListener {
        void onPointsFetched(int points); // Method definition added
        void onError(Exception e); // Method definition added
    }


    // --- Constructor ---
    public RideService() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.dbRootRef = database.getReference();
        this.ridesRef = database.getReference("rides");
        this.userPointsRef = database.getReference("userPoints"); // Initialize points ref
        this.counterRef = database.getReference("counters/lastRideId");
    }

    // --- Helper Methods ---

    /** Sets the rideId field on a Ride object based on its Firebase key. */
    private void setRideIdFromKey(Ride ride, String rideKey) {
        if (ride == null || rideKey == null) return;
        try {
            int id = Integer.parseInt(rideKey);
            ride.setRideId(id);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Could not parse ride key to int: " + rideKey);
        }
    }

    /** Creates a standard OnCompleteListener for write operations. */
    private OnCompleteListener<Void> createWriteCompleteListener(@Nullable final CompletionListener listener, final String operationTag) {
        return task -> {
            if (listener != null) {
                if (task.isSuccessful()) {
                    Log.d(TAG, operationTag + " successful.");
                    listener.onSuccess(); // Call the defined interface method
                } else {
                    Exception e = task.getException() != null ? task.getException() : new Exception(operationTag + " failed with unknown error");
                    Log.e(TAG, operationTag + " failed.", e);
                    listener.onFailure(e); // Call the defined interface method
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

    /** Gets current user's email or calls listener.onError if not logged in (for read list operations). */
    @Nullable
    private String getCurrentUserEmailForRead(@NonNull RideListListener listener) {
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

    /** Gets current user's email or calls listener.onError if not logged in (for read single operations). */
    @Nullable
    private String getCurrentUserEmailForRead(@NonNull RideSingleListener listener) {
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

    /** Gets current user's email or calls listener.onError if not logged in (for points fetch operations). */
    @Nullable
    private String getCurrentUserEmailForRead(@NonNull PointsFetchListener listener) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Operation failed: User not logged in.");
            listener.onError(new SecurityException("User not logged in"));
            return null;
        }
        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            Log.e(TAG, "Operation failed: User email is missing.");
            listener.onError(new IllegalStateException("User email is missing"));
            return null;
        }
        return email;
    }

    /** Sanitizes email to be used as a Firebase key (replace '.' with ','). Use UID if possible! */
    private String sanitizeEmailForKey(String email) {
        if (email == null) return null;
        // Using UID (user.getUid()) is strongly preferred as it's guaranteed safe for keys.
        return email.replace('.', ',');
    }


    // --- Points Handling ---

    /** Fetches the current points for the logged-in user. */
    public void getUserPoints(@NonNull final PointsFetchListener listener) {
        String currentUserEmail = getCurrentUserEmailForRead(listener);
        if (currentUserEmail == null) return; // Error handled in helper

        String sanitizedEmail = sanitizeEmailForKey(currentUserEmail);
        if (sanitizedEmail == null) {
            listener.onError(new IllegalArgumentException("Invalid user email for key"));
            return;
        }

        userPointsRef.child(sanitizedEmail).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Points pointsData = snapshot.getValue(Points.class);
                    listener.onPointsFetched(pointsData != null ? pointsData.getPoints() : STARTING_POINTS);
                } else {
                    listener.onPointsFetched(STARTING_POINTS); // Default if no entry
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch user points for " + currentUserEmail, error.toException());
                listener.onError(error.toException());
            }
        });
    }


    /** Handles adding or subtracting points for a user using a Transaction. */
    private void handlePoints(String userEmail, final int pointChange, @Nullable final CompletionListener listener) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Log.w(TAG, "handlePoints: Cannot modify points for null or empty user email.");
            // Decide if this is a failure or just a no-op
            if (listener != null) listener.onFailure(new IllegalArgumentException("User email required for points handling"));
            return;
        }
        final String sanitizedEmail = sanitizeEmailForKey(userEmail);
        if (sanitizedEmail == null) {
            Log.e(TAG, "handlePoints: Invalid user email for key: " + userEmail);
            if (listener != null) listener.onFailure(new IllegalArgumentException("Invalid user email for key"));
            return;
        }

        DatabaseReference userPointsNode = userPointsRef.child(sanitizedEmail);

        userPointsNode.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Points currentPointsData = mutableData.getValue(Points.class);
                if (currentPointsData == null) {
                    currentPointsData = new Points(STARTING_POINTS); // Initialize
                }
                int currentPoints = currentPointsData.getPoints();
                int newPoints = currentPoints + pointChange;

                // Check for sufficient points BEFORE deducting
                if (pointChange < 0 && currentPoints < Math.abs(pointChange)) {
                    Log.w(TAG, "Transaction Aborted: User " + userEmail + " has insufficient points (" + currentPoints + ") to apply change " + pointChange);
                    return Transaction.abort(); // Abort if not enough points
                }
                currentPointsData.setPoints(newPoints);
                mutableData.setValue(currentPointsData);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                String opTag = "handlePoints (" + (pointChange >= 0 ? "+" : "") + pointChange + " for " + userEmail + ")";
                if (error != null) {
                    Log.e(TAG, opTag + " transaction failed.", error.toException());
                    if (listener != null) listener.onFailure(error.toException());
                } else if (committed) {
                    Log.d(TAG, opTag + " successful. New points: " + (currentData != null && currentData.child("points").exists() ? currentData.child("points").getValue() : "N/A"));
                    if (listener != null) listener.onSuccess(); // Call defined method
                } else {
                    // Transaction aborted (likely insufficient points)
                    Log.w(TAG, opTag + " transaction aborted (likely insufficient points).");
                    // Use a more specific error message
                    if (listener != null) listener.onFailure(new Exception(pointChange < 0 ? "Insufficient points" : "Points transaction aborted")); // Call defined method
                }
            }
        });
    }


    // --- Create Operations ---

    /** Internal method to save ride data and then handle points (only for requests). */
    private void saveRideData(final Ride ride, final int newId, final boolean isOffer, @Nullable final CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(null); // Get email again for safety
        if (currentUserEmail == null) {
            if (listener != null) listener.onFailure(new SecurityException("User not logged in for point handling"));
            return;
        }

        ride.setRideId(newId);
        ridesRef.child(String.valueOf(newId)).setValue(ride.toMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Ride save successful for ID: " + newId);
                        // *** Only handle points if it was a REQUEST ***
                        if (!isOffer) {
                            Log.d(TAG, ". Handling points deduction for request...");
                            handlePoints(currentUserEmail, POINTS_FOR_REQUEST, listener); // Pass original listener
                        } else {
                            // It was an offer, points awarded on completion, just call onSuccess
                            Log.d(TAG, ". Offer created, points awarded on completion.");
                            if (listener != null) listener.onSuccess(); // Call defined method
                        }
                    } else {
                        Log.e(TAG, "Ride save failed for ID: " + newId, task.getException());
                        if (listener != null) listener.onFailure(task.getException() != null ? task.getException() : new Exception("Failed to save ride data")); // Call defined method
                    }
                });
    }

    /** Creates a new ride entry, performing preliminary points check for requests asynchronously. */
    public void createNewRide(final Ride ride, @Nullable final CompletionListener listener) {
        if (ride == null) { if (listener != null) listener.onFailure(new IllegalArgumentException("Ride cannot be null")); return; }
        final String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return; // Not logged in

        final boolean isOffer = ride.getDriver() != null && !ride.getDriver().isEmpty();

        // Authorization checks...
        if (isOffer && !ride.getDriver().equals(currentUserEmail)) {
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride offer for another user")); return;
        }
        if (!isOffer && (ride.getRider() == null || !ride.getRider().equals(currentUserEmail))) {
            if (listener != null) listener.onFailure(new SecurityException("Cannot create ride request for another user")); return;
        }

        // --- Asynchronous Points Check (for requests only) ---
        if (!isOffer) {
            Log.d(TAG, "Performing preliminary points check for request...");
            getUserPoints(new PointsFetchListener() {
                @Override
                public void onPointsFetched(int points) {
                    if (points >= Math.abs(POINTS_FOR_REQUEST)) {
                        Log.d(TAG, "Preliminary points check passed (" + points + " points). Proceeding with ride creation.");
                        // Points are sufficient, proceed to get ride ID and save
                        proceedWithRideCreation(ride, isOffer, listener);
                    } else {
                        Log.w(TAG, "Preliminary check failed: User " + currentUserEmail + " has insufficient points (" + points + ").");
                        if (listener != null) listener.onFailure(new Exception("Insufficient points to create request")); // Call defined method
                    }
                }
                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error during preliminary points check.", e);
                    if (listener != null) listener.onFailure(e); // Call defined method
                }
            });
        } else {
            // It's an offer, no points check needed, proceed directly
            Log.d(TAG, "Skipping points check for offer. Proceeding with ride creation.");
            proceedWithRideCreation(ride, isOffer, listener);
        }
    }

    /** Helper method to run the counter transaction and save data after points check (if applicable). */
    private void proceedWithRideCreation(final Ride ride, final boolean isOffer, @Nullable final CompletionListener listener) {
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
                    if (listener != null) listener.onFailure(error.toException()); // Call defined method
                } else if (committed && currentData != null) {
                    Integer newId = currentData.getValue(Integer.class);
                    if (newId != null) {
                        Log.d(TAG, "Successfully obtained new ride ID: " + newId);
                        // Call saveRideData which handles saving the ride AND points update (if request)
                        saveRideData(ride, newId, isOffer, listener);
                    } else {
                        Log.e(TAG, "Failed to retrieve new ID after transaction commit.");
                        if (listener != null) listener.onFailure(new Exception("Failed to retrieve new ID")); // Call defined method
                    }
                } else {
                    Log.w(TAG, "Counter transaction not committed.");
                    if (listener != null) listener.onFailure(new Exception("Counter transaction not committed")); // Call defined method
                }
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
            createNewRide(ride, listener); // createNewRide now handles the points check internally
        } catch (Exception e) { if (listener != null) listener.onFailure(e); }
    }

    // --- Read Operations ---

    public void getRideById(int rideId, @NonNull final RideSingleListener listener) {
        ridesRef.child(String.valueOf(rideId)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);
                    if (ride != null) setRideIdFromKey(ride, snapshot.getKey());
                    listener.onRideFetched(ride); // Call defined method
                } else { listener.onRideFetched(null); } // Call defined method
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { listener.onError(error); } // Call defined method
        });
    }

    /** Fetches ride offers, optionally filtering based on the current user. */
    public void getAllRideOffers(boolean excludeCurrentUser, @NonNull final RideListListener listener) {
        final String currentUserEmail = getCurrentUserEmailForRead(listener);
        if (currentUserEmail == null) return; // Needed for either filter mode

        Query query = ridesRef.orderByChild("rider").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
            boolean noRider = ride.getRider() == null || ride.getRider().trim().isEmpty();
            boolean notComplete = !ride.isComplete();
            boolean userMatches = Objects.equals(ride.getDriver(), currentUserEmail);
            boolean shouldInclude = excludeCurrentUser ? !userMatches : userMatches;
            return hasDriver && noRider && notComplete && shouldInclude;
        }, "getAllRideOffers"));
    }

    /** Fetches ride requests, optionally filtering based on the current user. */
    public void getAllRideRequests(boolean excludeCurrentUser, @NonNull final RideListListener listener) {
        final String currentUserEmail = getCurrentUserEmailForRead(listener);
        if (currentUserEmail == null) return; // Needed for either filter mode

        Query query = ridesRef.orderByChild("driver").equalTo(null);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            boolean hasRider = ride.getRider() != null && !ride.getRider().trim().isEmpty();
            boolean noDriver = ride.getDriver() == null || ride.getDriver().trim().isEmpty();
            boolean notComplete = !ride.isComplete();
            boolean userMatches = Objects.equals(ride.getRider(), currentUserEmail);
            boolean shouldInclude = excludeCurrentUser ? !userMatches : userMatches;
            return hasRider && noDriver && notComplete && shouldInclude;
        }, "getAllRideRequests"));
    }

    /** Fetches accepted rides involving the current user. */
    public void getAllAcceptedRides(@NonNull final RideListListener listener) {
        final String currentUserEmail = getCurrentUserEmailForRead(listener);
        if (currentUserEmail == null) return;

        Query query = ridesRef.orderByChild("complete").equalTo(false);
        query.addListenerForSingleValueEvent(createListValueEventListener(listener, ride -> {
            boolean hasDriver = ride.getDriver() != null && !ride.getDriver().trim().isEmpty();
            boolean hasRider = ride.getRider() != null && !ride.getRider().trim().isEmpty();
            boolean notComplete = !ride.isComplete();
            boolean userIsParticipant = Objects.equals(currentUserEmail, ride.getDriver()) || Objects.equals(currentUserEmail, ride.getRider());
            return hasDriver && hasRider && notComplete && userIsParticipant;
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
                listener.onRidesFetched(rides); // Call defined method
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, opTag + ": Firebase query cancelled or failed.", error.toException());
                listener.onError(error); // Call defined method
            }
        };
    }

    // --- Update Operations ---

    /** Updates an entire existing ride. */
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

    /** Accepts a ride offer/request. */
    public void acceptRide(final int rideId, @Nullable final CompletionListener listener) {
        // Points are not changed on accept, only on create/delete/complete
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
                // Directly call listener on success, as no points are handled here
                rideNodeRef.updateChildren(updates).addOnCompleteListener(createWriteCompleteListener(listener, "acceptRide"));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { if (listener != null) listener.onFailure(error.toException()); }
        });
    }

    /** Marks a ride as complete and awards points to the driver if it was an offer. */
    public void completeRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) { if (listener != null) listener.onFailure(new Exception("Ride not found")); return; }

                // Store driver email before checking authorization/completion
                final String driverEmail = ride.getDriver();
                final boolean wasOriginallyOffer = driverEmail != null && !driverEmail.isEmpty(); // Check if driver existed initially

                // Authorization check
                if (!Objects.equals(currentUserEmail, ride.getDriver()) && !Objects.equals(currentUserEmail, ride.getRider())) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized")); return;
                }
                // Check if already complete
                if (ride.isComplete()) {
                    Log.w(TAG, "Ride " + rideId + " is already complete.");
                    if (listener != null) listener.onSuccess(); // Already done
                    return;
                }

                // Proceed with completion
                Map<String, Object> updates = new HashMap<>();
                updates.put("complete", true);
                ridesRef.child(String.valueOf(rideId)).updateChildren(updates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "completeRide successful for ID " + rideId + ". Handling points award...");
                                // Award points to driver only if it was originally an offer
                                if (wasOriginallyOffer && driverEmail != null && !driverEmail.isEmpty()) {
                                    handlePoints(driverEmail, POINTS_FOR_COMPLETED_OFFER, listener); // Pass original listener
                                } else {
                                    // Ride was a request, or driver info missing, no points awarded on completion
                                    Log.d(TAG, "Ride was a request or driver info missing, no points awarded.");
                                    if (listener != null) listener.onSuccess(); // Call defined method
                                }
                            } else {
                                Log.e(TAG, "completeRide failed for ID " + rideId, task.getException());
                                if (listener != null) listener.onFailure(task.getException() != null ? task.getException() : new Exception("Failed to complete ride")); // Call defined method
                            }
                        });
            }
            @Override public void onError(DatabaseError databaseError) { if (listener != null) listener.onFailure(databaseError.toException()); }
        });
    }

    // --- Delete Operation ---

    /** Deletes a ride and refunds points. */
    public void deleteRide(int rideId, @Nullable CompletionListener listener) {
        String currentUserEmail = getCurrentUserEmail(listener);
        if (currentUserEmail == null) return;

        getRideById(rideId, new RideSingleListener() {
            @Override public void onRideFetched(@Nullable Ride ride) {
                if (ride == null) { if (listener != null) listener.onSuccess(); return; } // Not found is success for delete

                final String driverEmail = ride.getDriver();
                final String riderEmail = ride.getRider();
                // Determine if it was an offer based on whether driver existed
                final boolean wasOffer = driverEmail != null && !driverEmail.isEmpty();
                // Determine who needs refunding based on who posted
                final String userToRefund = wasOffer ? driverEmail : riderEmail;
                // Calculate points to refund (reverse the initial change)
                // Offers initially gave 0 points, Requests cost points.
                // So only refund if it was a request.
                final int pointsToRefund = wasOffer ? 0 : -POINTS_FOR_REQUEST; // Refund request cost

                // Authorization check
                if (!Objects.equals(currentUserEmail, driverEmail) && !Objects.equals(currentUserEmail, riderEmail)) {
                    if (listener != null) listener.onFailure(new SecurityException("Not authorized")); return;
                }

                Log.d(TAG, "User " + currentUserEmail + " deleting ride " + rideId);
                ridesRef.child(String.valueOf(rideId)).removeValue()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "deleteRide successful for ID " + rideId + ". Refunding points if applicable...");
                                // Only refund points if it was a request (wasOffer is false)
                                if (!wasOffer && userToRefund != null && !userToRefund.isEmpty()) {
                                    handlePoints(userToRefund, pointsToRefund, listener); // Pass original listener
                                } else {
                                    // Offer deleted (no initial point change) or user info missing
                                    Log.d(TAG, "Offer deleted or no user to refund points for deleted ride " + rideId);
                                    if (listener != null) listener.onSuccess(); // Report success if delete worked
                                }
                            } else {
                                Log.e(TAG, "deleteRide failed for ID " + rideId, task.getException());
                                if (listener != null) listener.onFailure(task.getException() != null ? task.getException() : new Exception("Failed to delete ride")); // Call defined method
                            }
                        });
            }
            @Override public void onError(DatabaseError databaseError) {
                Log.e(TAG, "deleteRide: Error fetching ride " + rideId, databaseError.toException());
                if (listener != null) listener.onFailure(databaseError.toException()); // Call defined method
            }
        });
    }
}
