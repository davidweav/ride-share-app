package edu.uga.cs.rideshareapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.model.Ride;

public class RideAdapter extends RecyclerView.Adapter<RideAdapter.RideViewHolder> {

    private List<Ride> rideList;

    public RideAdapter(List<Ride> rideList) {
        this.rideList = rideList;
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ride, parent, false);
        return new RideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
        Ride ride = rideList.get(position);
        holder.date.setText(ride.getDateTime());
        holder.from.setText("From: " + ride.getFrom());
        holder.to.setText("To: " + ride.getTo());
    }

    @Override
    public int getItemCount() {
        return rideList.size();
    }

    public static class RideViewHolder extends RecyclerView.ViewHolder {
        TextView date, from, to;

        public RideViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.dateText);
            from = itemView.findViewById(R.id.fromText);
            to = itemView.findViewById(R.id.toText);
        }
    }
}
