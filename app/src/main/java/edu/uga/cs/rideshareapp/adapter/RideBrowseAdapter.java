package edu.uga.cs.rideshareapp.adapter;

import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.model.Ride;

public class RideBrowseAdapter extends RecyclerView.Adapter<RideBrowseAdapter.ViewHolder> {

    public interface OnRideClickListener {
        void onRideClick(Ride ride);
    }

    private final List<Ride> rides;
    private final OnRideClickListener clickListener;

    public RideBrowseAdapter(List<Ride> rides, OnRideClickListener clickListener) {
        this.rides = rides;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ride, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ride ride = rides.get(position);
        holder.bind(ride);
    }

    @Override
    public int getItemCount() {
        return rides.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView date, from, to;

        ViewHolder(View view) {
            super(view);
            date = view.findViewById(R.id.dateText);
            from = view.findViewById(R.id.fromText);
            to = view.findViewById(R.id.toText);

            view.setOnClickListener(v -> {
                if (clickListener != null)
                    clickListener.onRideClick(rides.get(getAdapterPosition()));
            });
        }

        void bind(Ride ride) {
            date.setText(ride.getDateTime());
            from.setText("From: " + ride.getFrom());
            to.setText("To: " + ride.getTo());
        }
    }
}
