package edu.uga.cs.rideshareapp;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.icu.text.SimpleDateFormat; // Use android.icu for API 24+
import android.icu.util.Calendar; // Use android.icu for API 24+
import android.os.Bundle;
import android.util.Log; // Import Log
import android.view.View; // Import View for disabling button
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast; // Import Toast

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import edu.uga.cs.rideshareapp.firebase.RideService;
// Removed unused Ride import: import edu.uga.cs.rideshareapp.model.Ride;

import java.util.Locale; // Import Locale for SimpleDateFormat

public class PostRideActivity extends AppCompatActivity {

    private static final String TAG = "PostRideActivity"; // Tag for logging
    private RideService rideService; // Initialize later if needed, or keep as member
    private Button postRideButton; // Make button a member variable to disable/enable it

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_post_ride);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.postRideRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize RideService
        rideService = new RideService();

        // Get references to UI elements
        postRideButton = findViewById(R.id.postRideButton); // Assign to member variable
        EditText dateTimeField = findViewById(R.id.dateTimeField);
        EditText fromField = findViewById(R.id.fromField);
        EditText toField = findViewById(R.id.toField);
        RadioButton offerRadio = findViewById(R.id.offerRadio);
        // Removed unused requestRadio: RadioButton requestRadio = findViewById(R.id.requestRadio);


        // --- Date & Time Picker Logic ---
        dateTimeField.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();

            // Step 1: Show DatePicker
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        // Step 2: Show TimePicker
                        TimePickerDialog timePickerDialog = new TimePickerDialog(
                                this,
                                (timeView, hourOfDay, minute) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    calendar.set(Calendar.MINUTE, minute);

                                    // Format and set to EditText
                                    // Use java.text.SimpleDateFormat if targeting lower API levels or for consistency
                                    // android.icu.text.SimpleDateFormat requires API 24+
                                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.getDefault());
                                    dateTimeField.setText(sdf.format(calendar.getTime()));
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false // Use 12-hour format (false) or 24-hour format (true)
                        );
                        timePickerDialog.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            // Optional: Prevent selecting past dates
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        // --- Post Ride Button Logic ---
        postRideButton.setOnClickListener(v -> {
            // Get input values
            String dateTime = dateTimeField.getText().toString().trim();
            String from = fromField.getText().toString().trim();
            String to = toField.getText().toString().trim();
            boolean isOffer = offerRadio.isChecked(); // True if offering, false if requesting

            // Basic Input Validation
            if (dateTime.isEmpty() || from.isEmpty() || to.isEmpty()) {
                Toast.makeText(PostRideActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return; // Stop processing if fields are empty
            }

            // Get current user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(PostRideActivity.this, "Error: Not logged in", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "User is not logged in, cannot post ride.");
                // Optionally: Redirect to login activity
                // Intent intent = new Intent(this, LoginActivity.class);
                // startActivity(intent);
                // finish();
                return;
            }
            String userEmail = user.getEmail();
            if (userEmail == null || userEmail.isEmpty()) {
                Toast.makeText(PostRideActivity.this, "Error: User email not found", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "User email is null or empty.");
                return;
            }

            // Disable button to prevent multiple clicks while processing
            postRideButton.setEnabled(false);
            postRideButton.setText("Posting..."); // Provide visual feedback

            // --- Define the CompletionListener ---
            RideService.CompletionListener listener = new RideService.CompletionListener() {
                @Override
                public void onSuccess() {
                    // Re-enable button (important on success or failure)
                    postRideButton.setEnabled(true);
                    postRideButton.setText(R.string.post_ride); // Reset button text (assuming you have this string resource)

                    // Show success message
                    Log.d(TAG, "Ride posted successfully.");
                    Toast.makeText(PostRideActivity.this, "Ride posted successfully!", Toast.LENGTH_SHORT).show();

                    // Close the activity after successful post
                    finish();
                }

                @Override
                public void onFailure(Exception e) {
                    // Re-enable button
                    postRideButton.setEnabled(true);
                    postRideButton.setText(R.string.post_ride); // Reset button text

                    // Show error message
                    Log.e(TAG, "Failed to post ride: " + e.getMessage(), e);
                    Toast.makeText(PostRideActivity.this, "Failed to post ride: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Do NOT finish() on failure, let the user try again or fix input
                }
            };

            // Call the RideService method, passing the listener
            Log.d(TAG, "Calling createNewRideWithStrings...");
            rideService.createNewRideWithStrings(dateTime, userEmail, isOffer, from, to, listener);
        });
    }
}
