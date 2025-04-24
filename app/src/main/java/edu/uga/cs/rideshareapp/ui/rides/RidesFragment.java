package edu.uga.cs.rideshareapp.ui.rides;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import edu.uga.cs.rideshareapp.HomeActivity;
import edu.uga.cs.rideshareapp.PostRideActivity;
import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.adapter.RideAdapter;
import edu.uga.cs.rideshareapp.databinding.FragmentDashboardBinding;
import edu.uga.cs.rideshareapp.model.Ride;
import edu.uga.cs.rideshareapp.ui.home.HomeViewModel;

public class RidesFragment extends Fragment {

    FloatingActionButton postRideButton;
    RecyclerView rideOffers;
    RecyclerView rideRequests;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        rideOffers = view.findViewById(R.id.rideOffers);
        rideRequests = view.findViewById(R.id.rideRequests);
        rideRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        rideOffers.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Ride> rideList = new ArrayList<>();
        Ride ride = new Ride();
        ride.setFrom("Test");
        ride.setTo("Test");
        ride.setDateTime("MM/DD/YYYY");
        rideList.add(ride);

        Ride ride2 = new Ride();
        ride2.setFrom("Test2");
        ride2.setTo("Test2");
        ride2.setDateTime("MM/DD/YYYY");
        rideList.add(ride);
        RideAdapter adapter = new RideAdapter(rideList);
        rideOffers.setAdapter(adapter);
        rideRequests.setAdapter(adapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        postRideButton = view.findViewById(R.id.postRideButton);

        postRideButton.setOnClickListener((v) -> {
            Intent intent = new Intent(requireContext(), PostRideActivity.class);
            startActivity(intent);
        });

    }

}