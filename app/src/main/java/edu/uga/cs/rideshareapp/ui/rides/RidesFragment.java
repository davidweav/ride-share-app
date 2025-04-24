package edu.uga.cs.rideshareapp.ui.rides;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseError;

import java.util.ArrayList;
import java.util.List;

import edu.uga.cs.rideshareapp.PostRideActivity;
import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.adapter.RideBrowseAdapter;
import edu.uga.cs.rideshareapp.firebase.RideService;
import edu.uga.cs.rideshareapp.model.Ride;

public class RidesFragment extends Fragment {

    FloatingActionButton postRideButton;
    RecyclerView rideOffers;
    RecyclerView rideRequests;

    List<Ride> rideOffersList = new ArrayList<>();
    List<Ride> rideRequestsList = new ArrayList<>();

    RideService rideService = new RideService();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        rideOffers = view.findViewById(R.id.rideOffers);
        rideRequests = view.findViewById(R.id.rideRequests);
        postRideButton = view.findViewById(R.id.postRideButton);

        rideOffers.setLayoutManager(new LinearLayoutManager(getContext()));
        rideRequests.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize adapters with click handlers
        RideBrowseAdapter offerAdapter = new RideBrowseAdapter(rideOffersList, ride -> {
            rideService.acceptRide(ride.getRideId(), new RideService.CompletionListener() {
                @Override
                public void onSuccess() {
                    Toast.makeText(requireContext(), "Ride offer accepted", Toast.LENGTH_SHORT).show();
                    BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);
                    navView.setSelectedItemId(R.id.navigation_my_rides);
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(requireContext(), "Error accepting ride", Toast.LENGTH_SHORT).show();
                }
            });
        });

        RideBrowseAdapter requestAdapter = new RideBrowseAdapter(rideRequestsList, ride -> {
            rideService.acceptRide(ride.getRideId(), new RideService.CompletionListener() {
                @Override
                public void onSuccess() {
                    Toast.makeText(requireContext(), "Ride request accepted", Toast.LENGTH_SHORT).show();
                    BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);
                    navView.setSelectedItemId(R.id.navigation_my_rides);

                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(requireContext(), "Error accepting ride", Toast.LENGTH_SHORT).show();
                }
            });
        });

        rideOffers.setAdapter(offerAdapter);
        rideRequests.setAdapter(requestAdapter);

        // Fetch data and update lists
        rideService.getAllRideOffers(true, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG, "Received " + rides.size() + " ride offers");
                rideOffersList.clear();
                rideOffersList.addAll(rides);
                offerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(DatabaseError e) {
                Log.e(TAG, "Failed to get ride offers: " + e.getMessage());
            }
        });

        rideService.getAllRideRequests(true, new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                Log.d(TAG, "Received " + rides.size() + " ride requests");
                rideRequestsList.clear();
                rideRequestsList.addAll(rides);
                requestAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(DatabaseError e) {
                Log.e(TAG, "Failed to get ride requests: " + e.getMessage());
            }
        });

        // Post button click
        postRideButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PostRideActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
