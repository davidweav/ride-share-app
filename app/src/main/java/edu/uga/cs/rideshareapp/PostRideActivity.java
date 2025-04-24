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
    private RideService rideService;
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
        postRideButton = findViewById(R.id.postRideButton);
        EditText dateTimeField = findViewById(R.id.dateTimeField);
        EditText fromField = findViewById(R.id.fromField);
        EditText toField = findViewById(R.id.toField);
        RadioButton offerRadio = findViewById(R.id.offerRadio);


        // --- Date & Time Picker Logic ---
        dateTimeField.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog( this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        TimePickerDialog timePickerDialog = new TimePickerDialog( this,
                                (timeView, hourOfDay, minute) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    calendar.set(Calendar.MINUTE, minute);
                                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.getDefault());
                                    dateTimeField.setText(sdf.format(calendar.getTime()));
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false );
                        timePickerDialog.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        // --- Post Ride Button Logic ---
        postRideButton.setOnClickListener(v -> {
            // Get input values
            String dateTime = dateTimeField.getText().toString().trim();
            String from = fromField.getText().toString().trim();
            String to = toField.getText().toString().trim();
            boolean isOffer = offerRadio.isChecked(); // True if offering (driver), false if requesting (rider)

            // Basic Input Validation
            if (dateTime.isEmpty() || from.isEmpty() || to.isEmpty()) {
                Toast.makeText(PostRideActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // *** No need to get userEmail here, RideService does it internally ***
            // FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            // if (user == null) { ... } // RideService handles this check now
            // String userEmail = user.getEmail();
            // if (userEmail == null || userEmail.isEmpty()) { ... }

            // Disable button to prevent multiple clicks
            postRideButton.setEnabled(false);
            postRideButton.setText("Posting...");

            // Define the CompletionListener
            RideService.CompletionListener listener = new RideService.CompletionListener() {
                @Override
                public void onSuccess() {
                    postRideButton.setEnabled(true);
                    // Assuming you have a string resource R.string.post_ride
                    postRideButton.setText(getString(R.string.post_ride));
                    Log.d(TAG, "Ride posted successfully.");
                    Toast.makeText(PostRideActivity.this, "Ride posted successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity on success
                }

                @Override
                public void onFailure(Exception e) {
                    postRideButton.setEnabled(true);
                    // Assuming you have a string resource R.string.post_ride
                    postRideButton.setText(getString(R.string.post_ride));
                    Log.e(TAG, "Failed to post ride: " + e.getMessage(), e);
                    Toast.makeText(PostRideActivity.this, "Failed to post ride: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            };

            // Call the RideService method with the CORRECT signature
            Log.d(TAG, "Calling createNewRideWithStrings...");
            // *** Corrected Call: Removed userEmail argument ***
            rideService.createNewRideWithStrings(dateTime, isOffer, from, to, listener);
        });
    }
}
