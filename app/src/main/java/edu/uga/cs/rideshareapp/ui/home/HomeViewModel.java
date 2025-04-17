package edu.uga.cs.rideshareapp.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> welcomeText = new MutableLiveData<>();

    public HomeViewModel() {
        // Pretend to load user data (you could hook this to Firebase)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            welcomeText.setValue("Welcome, " + user.getEmail());
        } else {
            welcomeText.setValue("Welcome!");
        }
    }

    public LiveData<String> getWelcomeText() {
        return welcomeText;
    }
}
