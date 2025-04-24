package edu.uga.cs.rideshareapp; // Match your app's package

// Imports for Instrumented Tests
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry; // If context needed
import android.content.Context; // If context needed
import android.util.Log; // Use Android Log for instrumented tests

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith; // Import RunWith
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task; // Import Task for sign-in/out
import com.google.android.gms.tasks.Tasks; // Import Tasks for await
import com.google.firebase.auth.AuthResult; // Import AuthResult
import com.google.firebase.auth.FirebaseAuth; // Import FirebaseAuth
import com.google.firebase.database.DatabaseError;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects; // Import Objects
import java.util.UUID; // Import UUID for random locations
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException; // Import ExecutionException
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException; // Import TimeoutException
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import edu.uga.cs.rideshareapp.firebase.RideService;
import edu.uga.cs.rideshareapp.model.Ride;

/**
 * Instrumented tests for RideService, running on an Android device/emulator.
 * These tests will sign in a specific user before running to test authorization logic.
 */
@RunWith(AndroidJUnit4.class) // Use AndroidJUnit4 runner
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // Optional: Run tests alphabetically
public class RideServiceApiTest {

    private RideService rideService;
    private FirebaseAuth mAuth;
    private static final String TAG = "RideServiceApiTest"; // Use Android Log tag
    private static final int ASYNC_TIMEOUT_SECONDS = 25; // Generous timeout for device tests
    private static final String TEST_USER_EMAIL = "asr05918@uga.edu";
    private static final String TEST_USER_PASSWORD = "spaugh11AA@@"; // <<< --- IMPORTANT: SET PASSWORD HERE

    // Shared state for tests (use cautiously) - consider creating fresh data per test
    private static final AtomicInteger lastCreatedRideId = new AtomicInteger(-1);


    @Before
    public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        Log.i(TAG, "--- Setting up test ---");
        // Context context = InstrumentationRegistry.getInstrumentation().getTargetContext(); // Get context if needed
        mAuth = FirebaseAuth.getInstance();
        rideService = new RideService(); // RideService now uses FirebaseAuth internally

        // Sign in the test user before each test
        signInTestUser();
    }

    private void signInTestUser() throws ExecutionException, InterruptedException, TimeoutException {
        // Check if already signed in
        if (mAuth.getCurrentUser() != null && TEST_USER_EMAIL.equals(mAuth.getCurrentUser().getEmail())) {
            Log.d(TAG,"User " + TEST_USER_EMAIL + " already signed in.");
            return;
        }
        // Sign out any previous user first
        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG,"Signing out previous user: " + mAuth.getCurrentUser().getEmail());
            mAuth.signOut();
        }

        Log.d(TAG,"Attempting to sign in user: " + TEST_USER_EMAIL);
        if (TEST_USER_PASSWORD.equals("YOUR_PASSWORD_HERE")) {
            fail("!!! Test user password not set in RideServiceApiTest.java !!!");
        }

        Task<AuthResult> signInTask = mAuth.signInWithEmailAndPassword(TEST_USER_EMAIL, TEST_USER_PASSWORD);

        try {
            // Block and wait for the sign-in task to complete
            Tasks.await(signInTask, ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (signInTask.isSuccessful()) {
                Log.i(TAG,"Sign in SUCCESSFUL for user: " + TEST_USER_EMAIL);
                assertNotNull("FirebaseUser should not be null after successful sign in", mAuth.getCurrentUser());
                assertEquals("Signed in user email mismatch", TEST_USER_EMAIL, mAuth.getCurrentUser().getEmail());
            } else {
                Log.e(TAG,"Sign in FAILED: " + signInTask.getException());
                fail("Failed to sign in test user " + TEST_USER_EMAIL + ". Exception: " + signInTask.getException());
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG,"Exception during sign in await: " + e);
            fail("Exception while waiting for user sign in: " + e.getMessage());
            throw e; // Re-throw to ensure test setup fails clearly
        }
    }


    @After
    public void tearDown() {
        Log.i(TAG,"--- Tearing down test ---");
        // Sign out the user after each test for isolation
        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG,"Signing out user: " + mAuth.getCurrentUser().getEmail());
            mAuth.signOut();
        }
    }

    // --- Helper Methods for Testing Async Calls ---
    private boolean waitForLatch(CountDownLatch latch, String operation) throws InterruptedException {
        Log.d(TAG,"Waiting for " + operation + " (up to " + ASYNC_TIMEOUT_SECONDS + "s)...");
        boolean completed = latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) { Log.e(TAG,operation + " timed out!"); }
        return completed;
    }

    // --- Test Cases ---

    @Test
    public void test01_CreateNewRideOffer() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Create New Ride Offer <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        String dateTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).format(new Date());
        String from = "Test Offer From Location Instrumented";
        String to = "Test Offer To Location Instrumented";
        boolean isDriver = true; // This is an offer

        // Now we expect onSuccess because the user should be signed in
        rideService.createNewRideWithStrings(dateTime, isDriver, from, to, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"testCreateNewRideOffer: onSuccess (Expected in instrumented test)");
                // Ideally, capture the created ride ID here if RideService provided it back
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"testCreateNewRideOffer: onFailure (Unexpected in instrumented test)", e);
                success.set(false);
                latch.countDown();
                fail("Create Ride Offer failed: " + e.getMessage()); // Fail the test explicitly
            }
        });

        assertTrue("Create Offer callback did not complete in time.", waitForLatch(latch, "Create Offer"));
        assertTrue("Create Offer operation failed (check logs).", success.get());
        // We need a way to get the created ID back from RideService or query for it to use in later tests
    }


    @Test
    public void test02_CreateNewRideRequest() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Create New Ride Request <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        String dateTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).format(new Date());
        String from = "Test Request From Location Instrumented";
        String to = "Test Request To Location Instrumented";
        boolean isDriver = false; // This is a request

        rideService.createNewRideWithStrings(dateTime, isDriver, from, to, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"testCreateNewRideRequest: onSuccess (Expected in instrumented test)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"testCreateNewRideRequest: onFailure (Unexpected in instrumented test)", e);
                success.set(false);
                latch.countDown();
                fail("Create Ride Request failed: " + e.getMessage());
            }
        });

        assertTrue("Create Request callback did not complete in time.", waitForLatch(latch, "Create Request"));
        assertTrue("Create Request operation failed.", success.get());
    }

    // --- Read tests updated for excludeCurrentUser flag ---

    @Test
    public void test03_GetAllRideOffers_ExcludeUser() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get All Ride Offers (Excluding Current User) <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        // Call with excludeCurrentUser = true
        rideService.getAllRideOffers(true, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG,"GetAllRideOffers_ExcludeUser: Success - Fetched " + rides.size() + " offers.");
                assertNotNull("Offers list should not be null", rides);
                for (Ride ride : rides) {
                    Log.d(TAG,"  Offer (Exclude): ID=" + ride.getRideId() + ", Driver=" + ride.getDriver());
                    assertNotNull(ride.getDriver());
                    assertNull(ride.getRider());
                    assertFalse(ride.isComplete());
                    // Assert that the driver is NOT the current test user
                    assertNotEquals("Ride offer should not be from the current user", TEST_USER_EMAIL, ride.getDriver());
                }
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetAllRideOffers_ExcludeUser: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetAllRideOffers(exclude=true) failed: " + databaseError.getMessage());
            }
        });

        assertTrue("Get Offers (Exclude) callback did not complete in time.", waitForLatch(latch, "Get Offers (Exclude)"));
        assertTrue("Get Offers (Exclude) operation failed (check logs).", success.get());
    }

    @Test
    public void test03b_GetAllRideOffers_IncludeUser() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get All Ride Offers (Including Current User) <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicBoolean foundOwnOffer = new AtomicBoolean(false); // Flag to check if we find an offer by the test user

        // Call with excludeCurrentUser = false
        rideService.getAllRideOffers(false, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG,"GetAllRideOffers_IncludeUser: Success - Fetched " + rides.size() + " offers.");
                assertNotNull("Offers list should not be null", rides);
                for (Ride ride : rides) {
                    Log.d(TAG,"  Offer (Include): ID=" + ride.getRideId() + ", Driver=" + ride.getDriver());
                    assertNotNull(ride.getDriver());
                    assertNull(ride.getRider());
                    assertFalse(ride.isComplete());
                    if (Objects.equals(TEST_USER_EMAIL, ride.getDriver())) {
                        foundOwnOffer.set(true); // Mark if we found one by the current user
                    }
                }
                // This assertion depends on whether test01 actually created an offer successfully
                // assertTrue("Expected to find at least one offer by the current user", foundOwnOffer.get());
                Log.d(TAG,"Found own offer: " + foundOwnOffer.get()); // Log whether own offer was found
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetAllRideOffers_IncludeUser: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetAllRideOffers(exclude=false) failed: " + databaseError.getMessage());
            }
        });

        assertTrue("Get Offers (Include) callback did not complete in time.", waitForLatch(latch, "Get Offers (Include)"));
        assertTrue("Get Offers (Include) operation failed (check logs).", success.get());
    }

    @Test
    public void test04_GetAllRideRequests_ExcludeUser() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get All Ride Requests (Excluding Current User) <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        // Call with excludeCurrentUser = true
        rideService.getAllRideRequests(true, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG,"GetAllRideRequests_ExcludeUser: Success - Fetched " + rides.size() + " requests.");
                assertNotNull("Requests list should not be null", rides);
                for (Ride ride : rides) {
                    Log.d(TAG,"  Request (Exclude): ID=" + ride.getRideId() + ", Rider=" + ride.getRider());
                    assertNotNull(ride.getRider());
                    assertNull(ride.getDriver());
                    assertFalse(ride.isComplete());
                    // Assert that the rider is NOT the current test user
                    assertNotEquals("Ride request should not be from the current user", TEST_USER_EMAIL, ride.getRider());
                }
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetAllRideRequests_ExcludeUser: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetAllRideRequests(exclude=true) failed: " + databaseError.getMessage());
            }
        });

        assertTrue("Get Requests (Exclude) callback did not complete in time.", waitForLatch(latch, "Get Requests (Exclude)"));
        assertTrue("Get Requests (Exclude) operation failed.", success.get());
    }

    @Test
    public void test04b_GetAllRideRequests_IncludeUser() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get All Ride Requests (Including Current User) <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicBoolean foundOwnRequest = new AtomicBoolean(false);

        // Call with excludeCurrentUser = false
        rideService.getAllRideRequests(false, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG,"GetAllRideRequests_IncludeUser: Success - Fetched " + rides.size() + " requests.");
                assertNotNull("Requests list should not be null", rides);
                for (Ride ride : rides) {
                    Log.d(TAG,"  Request (Include): ID=" + ride.getRideId() + ", Rider=" + ride.getRider());
                    assertNotNull(ride.getRider());
                    assertNull(ride.getDriver());
                    assertFalse(ride.isComplete());
                    if (Objects.equals(TEST_USER_EMAIL, ride.getRider())) {
                        foundOwnRequest.set(true);
                    }
                }
                // assertTrue("Expected to find at least one request by the current user", foundOwnRequest.get());
                Log.d(TAG,"Found own request: " + foundOwnRequest.get());
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetAllRideRequests_IncludeUser: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetAllRideRequests(exclude=false) failed: " + databaseError.getMessage());
            }
        });

        assertTrue("Get Requests (Include) callback did not complete in time.", waitForLatch(latch, "Get Requests (Include)"));
        assertTrue("Get Requests (Include) operation failed.", success.get());
    }

    @Test
    public void test05_GetAllAcceptedRides() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get All Accepted Rides <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        rideService.getAllAcceptedRides(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG,"GetAllAcceptedRides: Success - Fetched " + rides.size() + " accepted rides for user " + TEST_USER_EMAIL);
                assertNotNull(rides);
                for (Ride ride : rides) {
                    Log.d(TAG,"  Accepted: ID=" + ride.getRideId() + ", Driver=" + ride.getDriver() + ", Rider=" + ride.getRider());
                    assertNotNull(ride.getDriver());
                    assertNotNull(ride.getRider());
                    assertFalse(ride.isComplete());
                    // Verify current user is involved
                    assertTrue("Current user should be driver or rider",
                            Objects.equals(TEST_USER_EMAIL, ride.getDriver()) || Objects.equals(TEST_USER_EMAIL, ride.getRider()));
                }
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetAllAcceptedRides: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetAllAcceptedRides failed: " + databaseError.getMessage());
            }
        });
        assertTrue("Get Accepted Rides callback timed out", waitForLatch(latch, "Get Accepted Rides"));
        assertTrue("Get Accepted Rides failed", success.get());
    }

    @Test
    public void test06_GetRideById_NotFound() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Get Ride By ID (Not Found) <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        rideService.getRideById(-9999, new RideService.RideSingleListener() {
            @Override
            public void onRideFetched(@Nullable Ride ride) {
                assertNull(ride); // Expect null
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"GetRideById_NotFound: Error - " + databaseError.getMessage(), databaseError.toException());
                success.set(false);
                latch.countDown();
                fail("GetRideById_NotFound failed: " + databaseError.getMessage());
            }
        });
        assertTrue("Get Ride Not Found callback timed out", waitForLatch(latch, "Get Ride Not Found"));
        assertTrue("Get Ride Not Found failed", success.get());
    }


    // --- Auth-dependent tests now expect SUCCESS if conditions met ---

    @Test
    public void test10_AcceptRide() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Accept Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of a ride OFFER/REQUEST created by a DIFFERENT user !!!
        // !!! Or create one specifically for this test in @Before or a preceding test !!!
        int rideIdToAccept = 1; // <<< --- FIND/CREATE A VALID ID FOR THIS

        Log.d(TAG,"Attempting to accept ride ID: " + rideIdToAccept);

        rideService.acceptRide(rideIdToAccept, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"AcceptRide: onSuccess (Expected in instrumented test)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"AcceptRide: onFailure (Unexpected if ride " + rideIdToAccept + " exists and is acceptable)", e);
                success.set(false);
                latch.countDown();
                fail("Accept Ride failed: " + e.getMessage());
            }
        });

        assertTrue("Accept Ride callback did not complete in time.", waitForLatch(latch, "Accept Ride"));
        assertTrue("Accept Ride operation failed.", success.get());
        // Add verification step: Fetch ride `rideIdToAccept` and assert driver/rider is now TEST_USER_EMAIL
    }

    @Test
    public void test11_CompleteRide() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Complete Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of an ACCEPTED ride involving TEST_USER_EMAIL !!!
        int rideIdToComplete = 1; // <<< --- FIND/CREATE A VALID ID FOR THIS

        Log.d(TAG,"Attempting to complete ride ID: " + rideIdToComplete);

        rideService.completeRide(rideIdToComplete, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"CompleteRide: onSuccess (Expected)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"CompleteRide: onFailure (Unexpected if ride " + rideIdToComplete + " exists and user is involved)", e);
                success.set(false);
                latch.countDown();
                fail("Complete Ride failed: " + e.getMessage());
            }
        });

        assertTrue("Complete Ride callback did not complete in time.", waitForLatch(latch, "Complete Ride"));
        assertTrue("Complete Ride operation failed.", success.get());
        // Add verification step: Fetch ride `rideIdToComplete` and assert isComplete is true
    }

    @Test
    public void test12_DeleteRide() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Delete Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of a ride involving TEST_USER_EMAIL !!!
        // !!! Be careful, this permanently deletes data !!!
        int rideIdToDelete = 10; // <<< --- FIND/CREATE A VALID ID FOR THIS (Maybe create in test01/02 and store ID?)

        Log.d(TAG,"Attempting to delete ride ID: " + rideIdToDelete);

        rideService.deleteRide(rideIdToDelete, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"DeleteRide: onSuccess (Expected)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"DeleteRide: onFailure (Unexpected if ride " + rideIdToDelete + " exists and user is involved)", e);
                success.set(false);
                latch.countDown();
                fail("Delete Ride failed: " + e.getMessage());
            }
        });

        assertTrue("Delete Ride callback did not complete in time.", waitForLatch(latch, "Delete Ride"));
        assertTrue("Delete Ride operation failed.", success.get());
        // Add verification step: Try to fetch ride `rideIdToDelete` and assert it's null
    }

    // --- New Test for Update ---
    @Test
    public void test13_UpdateRide() throws InterruptedException {
        Log.i(TAG,"\n>>> Testing: Update Ride <<<");
        final CountDownLatch fetchLatch = new CountDownLatch(1);
        final CountDownLatch updateLatch = new CountDownLatch(1);
        final AtomicBoolean fetchSuccess = new AtomicBoolean(false);
        final AtomicBoolean updateSuccess = new AtomicBoolean(false);
        final AtomicReference<Ride> originalRide = new AtomicReference<>();

        // !!! IMPORTANT: Replace with an ID of a ride involving TEST_USER_EMAIL !!!
        final int rideIdToUpdate = 10; // <<< --- FIND/CREATE A VALID ID FOR THIS
        final String newFromLocation = "Updated From Location " + UUID.randomUUID().toString().substring(0, 6);
        final String newToLocation = "Updated To Location " + UUID.randomUUID().toString().substring(0, 6);

        Log.d(TAG, "Attempting to fetch ride ID: " + rideIdToUpdate + " for update");

        // 1. Fetch the ride first
        rideService.getRideById(rideIdToUpdate, new RideService.RideSingleListener() {
            @Override
            public void onRideFetched(@Nullable Ride ride) {
                if (ride != null) {
                    Log.d(TAG,"UpdateRide: Fetched original ride ID " + rideIdToUpdate);
                    originalRide.set(ride);
                    fetchSuccess.set(true);
                } else {
                    Log.e(TAG,"UpdateRide: Ride " + rideIdToUpdate + " not found for update.");
                    fetchSuccess.set(false);
                    fail("Cannot update ride: Ride " + rideIdToUpdate + " not found.");
                }
                fetchLatch.countDown();
            }

            @Override
            public void onError(DatabaseError databaseError) {
                Log.e(TAG,"UpdateRide: Error fetching ride " + rideIdToUpdate, databaseError.toException());
                fetchSuccess.set(false);
                fetchLatch.countDown();
                fail("Failed to fetch ride for update: " + databaseError.getMessage());
            }
        });

        // Wait for the fetch operation to complete
        assertTrue("Fetch ride for update callback timed out", waitForLatch(fetchLatch, "Fetch Ride for Update"));
        assertTrue("Fetch ride for update failed", fetchSuccess.get());
        assertNotNull("Original ride object is null after fetch", originalRide.get());

        // 2. If fetch was successful, proceed with update
        Ride rideToUpdate = originalRide.get();
        Log.d(TAG, "Original From: " + rideToUpdate.getFrom() + ", Original To: " + rideToUpdate.getTo());
        rideToUpdate.setFrom(newFromLocation);
        rideToUpdate.setTo(newToLocation);
        Log.d(TAG, "Attempting to update ride ID: " + rideIdToUpdate + " with From: " + newFromLocation + ", To: " + newToLocation);


        rideService.updateRide(rideIdToUpdate, rideToUpdate, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"UpdateRide: onSuccess (Expected)");
                updateSuccess.set(true);
                updateLatch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG,"UpdateRide: onFailure (Unexpected if ride " + rideIdToUpdate + " exists and user is involved)", e);
                updateSuccess.set(false);
                updateLatch.countDown();
                fail("Update Ride failed: " + e.getMessage());
            }
        });

        // Wait for the update operation
        assertTrue("Update Ride callback timed out", waitForLatch(updateLatch, "Update Ride"));
        assertTrue("Update Ride operation failed", updateSuccess.get());

        // Add verification step: Fetch ride `rideIdToUpdate` again and assert 'from' and 'to' match new locations
        // (This would require another async call and latch)
        Log.i(TAG, "Update test completed. Manual verification or further async fetch needed to confirm DB changes.");
    }

}
