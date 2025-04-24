package edu.uga.cs.rideshareapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    EditText email;
    EditText password;
    Button submitButton;
    private FirebaseAuth mAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        email = findViewById(R.id.loginEmail);
        password = findViewById(R.id.loginPassword);
        submitButton = findViewById(R.id.loginSubmit);

        submitButton.setOnClickListener((view) -> {
            String emailInput = email.getText().toString().trim();
            String passwordInput = password.getText().toString().trim();

            // Register the user with Firebase
            mAuth.signInWithEmailAndPassword(emailInput, passwordInput)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Login successful
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish(); // prevents back navigation to login
                        } else {
                            // Show error from Firebase
                            Log.e("Error", task.getException().getMessage());
                            Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}