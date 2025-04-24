package edu.uga.cs.rideshareapp.adapter;

import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import edu.uga.cs.rideshareapp.R;
import edu.uga.cs.rideshareapp.model.Ride;

public class RideManageAdapter extends RecyclerView.Adapter<RideManageAdapter.ViewHolder> {

    public interface OnRideActionListener {
        void onEdit(Ride ride);
        void onDelete(Ride ride);
        void onConfirm(Ride ride);
    }

    private final List<Ride> rideList;
    private final OnRideActionListener actionListener;
    private final boolean showConfirm;

    public RideManageAdapter(List<Ride> rideList, OnRideActionListener actionListener, boolean showConfirm) {
        this.rideList = rideList;
        this.actionListener = actionListener;
        this.showConfirm = showConfirm;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ride_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ride ride = rideList.get(position);
        holder.bind(ride);
    }

    @Override
    public int getItemCount() {
        return rideList != null ? rideList.size() : 0;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView dateText, fromText, toText;
        ImageButton optionsButton;
        Button confirmButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            fromText = itemView.findViewById(R.id.fromText);
            toText = itemView.findViewById(R.id.toText);
            optionsButton = itemView.findViewById(R.id.optionsButton);
            confirmButton = itemView.findViewById(R.id.confirmButton);
        }

        public void bind(Ride ride) {
            dateText.setText(ride.getDateTime());
            fromText.setText("From: " + ride.getFrom());
            toText.setText("To: " + ride.getTo());

            // Confirm button logic
            if (confirmButton != null) {
                confirmButton.setVisibility(showConfirm ? View.VISIBLE : View.GONE);
                confirmButton.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onConfirm(ride);
                    }
                });
            }

            // Popup menu for Edit/Delete
            if (optionsButton != null) {
                optionsButton.setVisibility(View.VISIBLE);
                optionsButton.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(v.getContext(), optionsButton);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.ride_item_menu, popup.getMenu());
                    popup.setOnMenuItemClickListener(item -> {
                        if (actionListener == null) return false;
                        if (item.getItemId() == R.id.menu_edit) {
                            actionListener.onEdit(ride);
                            return true;
                        } else if (item.getItemId() == R.id.menu_delete) {
                            actionListener.onDelete(ride);
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            }
        }
    }
}
