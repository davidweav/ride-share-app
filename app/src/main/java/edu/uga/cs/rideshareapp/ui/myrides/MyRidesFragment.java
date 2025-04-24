package edu.uga.cs.rideshareapp.ui.myrides;

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

import com.google.firebase.database.DatabaseError;

import java.util.ArrayList;
import java.util.List;

import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.adapter.RideAdapter;
import edu.uga.cs.rideshareapp.firebase.RideService;
import edu.uga.cs.rideshareapp.model.Ride;

public class MyRidesFragment extends Fragment {

    private RecyclerView pendingOffersRecycler, pendingRequestsRecycler, acceptedRidesRecycler;

    private final List<Ride> pendingOffersList = new ArrayList<>();
    private final List<Ride> pendingRequestsList = new ArrayList<>();
    private final List<Ride> acceptedRidesList = new ArrayList<>();

    private final RideService rideService = new RideService();

    private RideAdapter offerAdapter;
    private RideAdapter requestAdapter;
    private RideAdapter acceptedAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        // Find views
        pendingOffersRecycler = view.findViewById(R.id.pendingOffersRecycler);
        pendingRequestsRecycler = view.findViewById(R.id.pendingRequestsRecycler);
        acceptedRidesRecycler = view.findViewById(R.id.acceptedRidesRecycler);

        // Set layout managers
        pendingOffersRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        pendingRequestsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        acceptedRidesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize adapters
        offerAdapter = new RideAdapter(pendingOffersList, null);
        requestAdapter = new RideAdapter(pendingRequestsList, null);
        acceptedAdapter = new RideAdapter(acceptedRidesList, null);

        // Attach adapters
        pendingOffersRecycler.setAdapter(offerAdapter);
        pendingRequestsRecycler.setAdapter(requestAdapter);
        acceptedRidesRecycler.setAdapter(acceptedAdapter);

        fetchMyRides();

        return view;
    }

    private void fetchMyRides() {
        // Get pending offers (driver set, no rider, not complete)
        rideService.getAllRideOffers(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                pendingOffersList.clear();
                pendingOffersList.addAll(rides);
                offerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(DatabaseError e) {
                Toast.makeText(requireContext(), "Error loading offers", Toast.LENGTH_SHORT).show();
            }
        });

        // Get pending requests (rider set, no driver, not complete)
        rideService.getAllRideRequests(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                pendingRequestsList.clear();
                pendingRequestsList.addAll(rides);
                requestAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(DatabaseError e) {
                Toast.makeText(requireContext(), "Error loading requests", Toast.LENGTH_SHORT).show();
            }
        });

        // Get accepted rides (both rider and driver set, not complete)
        rideService.getAllAcceptedRides(new RideService.RideListListener() {
            @Override
            public void onRidesFetched(List<Ride> rides) {
                acceptedRidesList.clear();
                acceptedRidesList.addAll(rides);
                acceptedAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(DatabaseError e) {
                Toast.makeText(requireContext(), "Error loading accepted rides", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
