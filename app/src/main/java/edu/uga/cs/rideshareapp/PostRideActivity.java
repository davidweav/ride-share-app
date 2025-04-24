package edu.uga.cs.rideshareapp;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import edu.uga.cs.rideshareapp.firebase.RideService;
import edu.uga.cs.rideshareapp.model.Ride;

public class PostRideActivity extends AppCompatActivity {

    RideService rideService = new RideService();
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

        Button postRideButton = findViewById(R.id.postRideButton);
        EditText dateTimeField = findViewById(R.id.dateTimeField);
        EditText fromField = findViewById(R.id.fromField);
        EditText toField = findViewById(R.id.toField);
        RadioButton offerRadio = findViewById(R.id.offerRadio);
        RadioButton requestRadio = findViewById(R.id.requestRadio);


        dateTimeField.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();

            // Step 1: Show DatePicker
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        // Store selected date
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
                                    @SuppressLint("SimpleDateFormat")
                                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
                                    dateTimeField.setText(sdf.format(calendar.getTime()));
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                        );
                        timePickerDialog.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );

            datePickerDialog.show();
        });

        postRideButton.setOnClickListener(v -> {
            String dateTime = dateTimeField.getText().toString().trim();
            String from = fromField.getText().toString().trim();
            String to = toField.getText().toString().trim();

            // Get current user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String userEmail = user != null ? user.getEmail() : "unknown@uga.edu";

            rideService.createNewRideWithStrings(dateTime, userEmail, offerRadio.isChecked(), from, to);
            finish();
        });
    }
}