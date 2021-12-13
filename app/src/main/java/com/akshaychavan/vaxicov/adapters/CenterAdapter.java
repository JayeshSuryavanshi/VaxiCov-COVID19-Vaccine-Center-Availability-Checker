package com.akshaychavan.vaxicov.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akshaychavan.vaxicov.R;
import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders one card per center with a compact line per bookable session.
 * Sessions are inflated into a plain LinearLayout: a center never has more
 * than seven, which is not worth a nested RecyclerView.
 */
public class CenterAdapter extends RecyclerView.Adapter<CenterAdapter.CenterViewHolder> {

    private final List<Center> centers = new ArrayList<>();

    public void setCenters(@NonNull List<Center> newCenters) {
        centers.clear();
        centers.addAll(newCenters);
        notifyDataSetChanged();
    }

    public void clear() {
        centers.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CenterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_center, parent, false);
        return new CenterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CenterViewHolder holder, int position) {
        holder.bind(centers.get(position));
    }

    @Override
    public int getItemCount() {
        return centers.size();
    }

    static class CenterViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView address;
        private final TextView fee;
        private final LinearLayout sessions;

        CenterViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_center_name);
            address = itemView.findViewById(R.id.tv_center_address);
            fee = itemView.findViewById(R.id.tv_fee_type);
            sessions = itemView.findViewById(R.id.ll_sessions);
        }

        void bind(@NonNull Center center) {
            name.setText(center.getName());
            address.setText(describeAddress(center));
            fee.setText(describeFee(center));

            sessions.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            if (center.getSessions() == null) {
                return;
            }
            for (Session session : center.getSessions()) {
                View row = inflater.inflate(R.layout.item_session, sessions, false);
                TextView date = row.findViewById(R.id.tv_date);
                TextView vaccine = row.findViewById(R.id.tv_vaccine);
                TextView doses = row.findViewById(R.id.tv_doses);
                date.setText(session.getDate());
                vaccine.setText(describeVaccine(session));
                Integer capacity = session.getAvailableCapacity();
                doses.setText(String.format(Locale.getDefault(), "%d", capacity == null ? 0 : capacity));
                sessions.addView(row);
            }
        }

        private static String describeAddress(Center center) {
            StringBuilder sb = new StringBuilder();
            if (center.getAddress() != null && !center.getAddress().trim().isEmpty()) {
                sb.append(center.getAddress().trim());
            }
            if (center.getPincode() != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(center.getPincode());
            }
            return sb.toString();
        }

        private static String describeFee(Center center) {
            String feeType = center.getFeeType() == null ? "" : center.getFeeType();
            if (!"Paid".equalsIgnoreCase(feeType) || center.getSessions() == null) {
                return feeType;
            }
            for (Session session : center.getSessions()) {
                String fee = session.getFee();
                if (fee != null && !fee.isEmpty() && !"0".equals(fee)) {
                    return feeType + " · ₹" + fee;
                }
            }
            return feeType;
        }

        private static String describeVaccine(Session session) {
            String vaccine = session.getVaccine() == null ? "" : session.getVaccine();
            if (session.getMinAgeLimit() == null) {
                return vaccine;
            }
            return vaccine + " · " + session.getMinAgeLimit() + "+";
        }
    }
}
