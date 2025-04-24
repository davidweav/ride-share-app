package edu.uga.cs.rideshareapp.ui.myrides;

import android.os.Bundle;
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
import edu.uga.cs.rideshareapp.adapter.RideManageAdapter;
import edu.uga.cs.rideshareapp.firebase.RideService;
import edu.uga.cs.rideshareapp.model.Ride;

public class MyRidesFragment extends Fragment {

    private RecyclerView pendingOffersRecycler, pendingRequestsRecycler, acceptedRidesRecycler;

    private final List<Ride> pendingOffersList = new ArrayList<>();
    private final List<Ride> pendingRequestsList = new ArrayList<>();
    private final List<Ride> acceptedRidesList = new ArrayList<>();

    private final RideService rideService = new RideService();

    private RideManageAdapter offerAdapter;
    private RideManageAdapter requestAdapter;
    private RideManageAdapter acceptedAdapter;

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

        // Set up offer adapter with delete support
        offerAdapter = new RideManageAdapter(pendingOffersList, new RideManageAdapter.OnRideActionListener() {
            @Override
            public void onEdit(Ride ride) {
                // TODO: Launch edit screen here
                Toast.makeText(requireContext(), "Edit not implemented yet", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(Ride ride) {
                rideService.deleteRide(ride.getRideId(), new RideService.CompletionListener() {
                    @Override
                    public void onSuccess() {
                        rideService.deleteRide(ride.getRideId(), new RideService.CompletionListener() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(requireContext(), "Ride deleted", Toast.LENGTH_SHORT).show();
                                pendingOffersList.remove(ride);
                                offerAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                Toast.makeText(requireContext(), "Ride deleted", Toast.LENGTH_SHORT).show();
                            }
                        });

                    }
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(requireContext(), "Failed to delete ride", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Set up request adapter with delete support
        requestAdapter = new RideManageAdapter(pendingRequestsList, new RideManageAdapter.OnRideActionListener() {
            @Override
            public void onEdit(Ride ride) {
                Toast.makeText(requireContext(), "Edit not implemented yet", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(Ride ride) {
                rideService.deleteRide(ride.getRideId(), new RideService.CompletionListener() {
                    @Override
                    public void onSuccess() {
                        rideService.deleteRide(ride.getRideId(), new RideService.CompletionListener() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(requireContext(), "Ride deleted", Toast.LENGTH_SHORT).show();
                                pendingOffersList.remove(ride);
                                offerAdapter.notifyDataSetChanged();
                            }
                            @Override
                            public void onFailure(Exception e) {
                                Toast.makeText(requireContext(), "Ride deleted", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(requireContext(), "Failed to delete ride", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Accepted rides adapter — read-only
        acceptedAdapter = new RideManageAdapter(acceptedRidesList, null);

        // Attach adapters to views
        pendingOffersRecycler.setAdapter(offerAdapter);
        pendingRequestsRecycler.setAdapter(requestAdapter);
        acceptedRidesRecycler.setAdapter(acceptedAdapter);

        // Load rides
        fetchMyRides();

        return view;
    }

    private void fetchMyRides() {
        // Load pending offers
        rideService.getAllRideOffers(false, new RideService.RideListListener() {
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

        // Load pending requests
        rideService.getAllRideRequests(false, new RideService.RideListListener() {
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

        // Load accepted rides
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
