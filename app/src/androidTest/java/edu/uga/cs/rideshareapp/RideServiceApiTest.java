package edu.uga.cs.rideshareapp; // Match your app's package

// Imports for Instrumented Tests
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry; // If context needed
import android.content.Context; // If context needed

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
    private static final String TAG = "RideServiceApiTest";
    private static final int ASYNC_TIMEOUT_SECONDS = 25; // Slightly longer timeout for device tests
    private static final String TEST_USER_EMAIL = "asr05918@uga.edu";
    private static final String TEST_USER_PASSWORD = "YOUR_PASSWORD_HERE"; // <<< --- IMPORTANT: SET PASSWORD HERE

    // Shared state for tests (use cautiously) - consider creating fresh data per test
    private static final AtomicInteger lastCreatedRideId = new AtomicInteger(-1);


    @Before
    public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("--- Setting up test ---");
        // Context context = InstrumentationRegistry.getInstrumentation().getTargetContext(); // Get context if needed
        mAuth = FirebaseAuth.getInstance();
        rideService = new RideService(); // RideService now uses FirebaseAuth internally

        // Sign in the test user before each test
        signInTestUser();
    }

    private void signInTestUser() throws ExecutionException, InterruptedException, TimeoutException {
        // Check if already signed in
        if (mAuth.getCurrentUser() != null && TEST_USER_EMAIL.equals(mAuth.getCurrentUser().getEmail())) {
            System.out.println("User " + TEST_USER_EMAIL + " already signed in.");
            return;
        }
        // Sign out any previous user first
        if (mAuth.getCurrentUser() != null) {
            System.out.println("Signing out previous user: " + mAuth.getCurrentUser().getEmail());
            mAuth.signOut();
        }

        System.out.println("Attempting to sign in user: " + TEST_USER_EMAIL);
        if (TEST_USER_PASSWORD.equals("YOUR_PASSWORD_HERE")) {
            fail("!!! Test user password not set in RideServiceApiTest.java !!!");
        }

        Task<AuthResult> signInTask = mAuth.signInWithEmailAndPassword(TEST_USER_EMAIL, TEST_USER_PASSWORD);

        try {
            // Block and wait for the sign-in task to complete
            Tasks.await(signInTask, ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (signInTask.isSuccessful()) {
                System.out.println("Sign in SUCCESSFUL for user: " + TEST_USER_EMAIL);
                assertNotNull("FirebaseUser should not be null after successful sign in", mAuth.getCurrentUser());
                assertEquals("Signed in user email mismatch", TEST_USER_EMAIL, mAuth.getCurrentUser().getEmail());
            } else {
                System.err.println("Sign in FAILED: " + signInTask.getException());
                fail("Failed to sign in test user " + TEST_USER_EMAIL + ". Exception: " + signInTask.getException());
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            System.err.println("Exception during sign in await: " + e);
            fail("Exception while waiting for user sign in: " + e.getMessage());
            throw e; // Re-throw to ensure test setup fails clearly
        }
    }


    @After
    public void tearDown() {
        System.out.println("--- Tearing down test ---");
        // Sign out the user after each test for isolation
        if (mAuth.getCurrentUser() != null) {
            System.out.println("Signing out user: " + mAuth.getCurrentUser().getEmail());
            mAuth.signOut();
        }
    }

    // --- Helper Methods for Testing Async Calls ---
    private boolean waitForLatch(CountDownLatch latch, String operation) throws InterruptedException {
        System.out.println("Waiting for " + operation + " (up to " + ASYNC_TIMEOUT_SECONDS + "s)...");
        boolean completed = latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) { System.err.println(operation + " timed out!"); }
        return completed;
    }

    // --- Test Cases ---

    @Test
    public void test01_CreateNewRideOffer() throws InterruptedException {
        System.out.println("\n>>> Testing: Create New Ride Offer <<<");
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
                System.out.println("testCreateNewRideOffer: onSuccess (Expected in instrumented test)");
                // Ideally, capture the created ride ID here if RideService provided it back
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                System.err.println("testCreateNewRideOffer: onFailure (Unexpected in instrumented test): " + e.getMessage());
                e.printStackTrace(); // Print stack trace for debugging
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
        System.out.println("\n>>> Testing: Create New Ride Request <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        String dateTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).format(new Date());
        String from = "Test Request From Location Instrumented";
        String to = "Test Request To Location Instrumented";
        boolean isDriver = false; // This is a request

        rideService.createNewRideWithStrings(dateTime, isDriver, from, to, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                System.out.println("testCreateNewRideRequest: onSuccess (Expected in instrumented test)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                System.err.println("testCreateNewRideRequest: onFailure (Unexpected in instrumented test): " + e.getMessage());
                e.printStackTrace();
                success.set(false);
                latch.countDown();
                fail("Create Ride Request failed: " + e.getMessage());
            }
        });

        assertTrue("Create Request callback did not complete in time.", waitForLatch(latch, "Create Request"));
        assertTrue("Create Request operation failed.", success.get());
    }

    // --- Read tests remain largely the same ---

    @Test
    public void test03_GetAllRideOffers() throws InterruptedException {
        System.out.println("\n>>> Testing: Get All Ride Offers <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        rideService.getAllRideOffers(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                System.out.println("GetAllRideOffers: Success - Fetched " + rides.size() + " offers.");
                assertNotNull("Offers list should not be null", rides);
                for (Ride ride : rides) {
                    assertNotNull(ride.getDriver());
                    assertNull(ride.getRider());
                    assertFalse(ride.isComplete());
                }
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                System.err.println("GetAllRideOffers: Error - " + databaseError.getMessage());
                success.set(false);
                latch.countDown();
                fail("GetAllRideOffers failed: " + databaseError.getMessage());
            }
        });

        assertTrue("Get Offers callback did not complete in time.", waitForLatch(latch, "Get Offers"));
        assertTrue("Get Offers operation failed (check logs).", success.get());
    }

    // ... (test04_GetAllRideRequests, test05_GetAllAcceptedRides, test06_GetRideById_NotFound remain similar) ...
    @Test
    public void test04_GetAllRideRequests() throws InterruptedException {
        // ... (Similar structure to test03_GetAllRideOffers) ...
        System.out.println("\n>>> Testing: Get All Ride Requests <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        rideService.getAllRideRequests(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                System.out.println("GetAllRideRequests: Success - Fetched " + rides.size() + " requests.");
                assertNotNull(rides);
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                System.err.println("GetAllRideRequests: Error - " + databaseError.getMessage());
                success.set(false);
                latch.countDown();
                fail("GetAllRideRequests failed: " + databaseError.getMessage());
            }
        });
        assertTrue("Get Requests callback timed out", waitForLatch(latch, "Get Requests"));
        assertTrue("Get Requests failed", success.get());
    }

    @Test
    public void test05_GetAllAcceptedRides() throws InterruptedException {
        // ... (Similar structure to test03_GetAllRideOffers) ...
        System.out.println("\n>>> Testing: Get All Accepted Rides <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        rideService.getAllAcceptedRides(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                System.out.println("GetAllAcceptedRides: Success - Fetched " + rides.size() + " accepted rides.");
                assertNotNull(rides);
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onError(DatabaseError databaseError) {
                System.err.println("GetAllAcceptedRides: Error - " + databaseError.getMessage());
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
        // ... (Similar structure to test03_GetAllRideOffers) ...
        System.out.println("\n>>> Testing: Get Ride By ID (Not Found) <<<");
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
                System.err.println("GetRideById_NotFound: Error - " + databaseError.getMessage());
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
        System.out.println("\n>>> Testing: Accept Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of a ride OFFER/REQUEST created by a DIFFERENT user !!!
        // !!! Or create one specifically for this test in @Before or a preceding test !!!
        int rideIdToAccept = 1; // <<< --- FIND/CREATE A VALID ID FOR THIS

        System.out.println("Attempting to accept ride ID: " + rideIdToAccept);

        rideService.acceptRide(rideIdToAccept, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                System.out.println("AcceptRide: onSuccess (Expected in instrumented test)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                System.err.println("AcceptRide: onFailure (Unexpected if ride " + rideIdToAccept + " exists and is acceptable): " + e.getMessage());
                e.printStackTrace();
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
        System.out.println("\n>>> Testing: Complete Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of an ACCEPTED ride involving TEST_USER_EMAIL !!!
        int rideIdToComplete = 1; // <<< --- FIND/CREATE A VALID ID FOR THIS

        System.out.println("Attempting to complete ride ID: " + rideIdToComplete);

        rideService.completeRide(rideIdToComplete, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                System.out.println("CompleteRide: onSuccess (Expected)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                System.err.println("CompleteRide: onFailure (Unexpected if ride " + rideIdToComplete + " exists and user is involved): " + e.getMessage());
                e.printStackTrace();
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
        System.out.println("\n>>> Testing: Delete Ride <<<");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        // !!! IMPORTANT: Replace with an ID of a ride involving TEST_USER_EMAIL !!!
        // !!! Be careful, this permanently deletes data !!!
        int rideIdToDelete = 3; // <<< --- FIND/CREATE A VALID ID FOR THIS (Maybe create in test01/02 and store ID?)

        System.out.println("Attempting to delete ride ID: " + rideIdToDelete);

        rideService.deleteRide(rideIdToDelete, new RideService.CompletionListener() {
            @Override
            public void onSuccess() {
                System.out.println("DeleteRide: onSuccess (Expected)");
                success.set(true);
                latch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                System.err.println("DeleteRide: onFailure (Unexpected if ride " + rideIdToDelete + " exists and user is involved): " + e.getMessage());
                e.printStackTrace();
                success.set(false);
                latch.countDown();
                fail("Delete Ride failed: " + e.getMessage());
            }
        });

        assertTrue("Delete Ride callback did not complete in time.", waitForLatch(latch, "Delete Ride"));
        assertTrue("Delete Ride operation failed.", success.get());
        // Add verification step: Try to fetch ride `rideIdToDelete` and assert it's null
    }

    // Add testUpdateRide similarly if needed, expecting onSuccess if authorized.

}
