package com.racks.parentalcontrol.parent.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.models.CallModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class CallAdapter extends RecyclerView.Adapter<CallAdapter.MyCallViewHolder>{
    private final ArrayList<CallModel> callModelArrayList;
    private Context context;
    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }
    private SelectionListener selectionListener;

    public CallAdapter(Context context, ArrayList<CallModel> callModelArrayList) {
        this.callModelArrayList = callModelArrayList;
        this.context = context;
    }

    @NonNull
    @Override
    public MyCallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_call, parent, false);
        return new MyCallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyCallViewHolder holder, int position) {
        CallModel callModel = callModelArrayList.get(position);

        if (callModel.isHeader()){
            holder.card_view_call_item.setVisibility(View.GONE);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            holder.tv_date_header.setText(sdf.format(new Date(callModel.getDate())));
            holder.tv_date_header.setVisibility(View.VISIBLE);

        }else{
            holder.tv_date_header.setVisibility(View.GONE);
            holder.card_view_call_item.setVisibility(View.VISIBLE);
            holder.tv_contact_name.setText(callModel.getName());
            holder.tv_phone_no.setText("mobile "+callModel.getNumber());
            holder.tv_date_time_call.setText(getFormatedTime(callModel.getDate()));
            holder.tv_call_type.setText(callModel.getType());
            holder.tv_call_duration.setText(getConvertedDuration(callModel.getDuration()));
        }

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSelectionMode()){
                    toggleSelection(holder.getAdapterPosition());
                }
            }
        });

        int color = callModel.isSelected()
                ? ContextCompat.getColor(context, R.color.selection_color)
                : ContextCompat.getColor(context, android.R.color.transparent);

        ViewCompat.setBackgroundTintList(holder.card_view_call_item, ColorStateList.valueOf(color));
    }

    @Override
    public int getItemCount() {
        return callModelArrayList.size();
    }

    public static class MyCallViewHolder extends RecyclerView.ViewHolder {

        TextView tv_contact_name, tv_phone_no, tv_date_time_call,tv_call_duration, tv_call_type, tv_date_header;
        MaterialCardView card_view_call_item;
        public MyCallViewHolder(@NonNull View itemView) {
            super(itemView);

            tv_contact_name = itemView.findViewById(R.id.tv_contact_name);
            tv_phone_no = itemView.findViewById(R.id.tv_phone_no);
            tv_date_time_call = itemView.findViewById(R.id.tv_date_time_call);
            tv_call_duration = itemView.findViewById(R.id.tv_call_duration);
            tv_call_type = itemView.findViewById(R.id.tv_call_type);
            tv_date_header = itemView.findViewById(R.id.tv_date_header);
            card_view_call_item = itemView.findViewById(R.id.card_view_call_item);
        }
    }
    public String getFormatedTime(long dateMillis){
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(dateMillis));
    }
    private String getConvertedDuration(int durationSeconds){
        return String.format(Locale.getDefault(),"%02d:%02d:%02d",
                durationSeconds / 3600,
                (durationSeconds % 3600) / 60,
                durationSeconds % 60);
    }

    private boolean isSelectionMode() {
        for (CallModel model : callModelArrayList) {
            if (model.isSelected()) return true;
        }
        return false;
    }

    private void toggleSelection(int position) {
        CallModel model = callModelArrayList.get(position);
        model.setSelected(!model.isSelected());
        notifyItemChanged(position);

        if (selectionListener != null) {
            int count = getSelectedCallList().size();
            selectionListener.onSelectionChanged(count);
        }
    }

    public ArrayList<CallModel> getSelectedCallList() {
        ArrayList<CallModel> selectedItems = new ArrayList<>();
        for (CallModel model : callModelArrayList) {
            if (model.isSelected()) selectedItems.add(model);
        }
        return selectedItems;
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }
    public void clearSelection() {
        for (CallModel model : callModelArrayList) {
            if (model.isSelected()) {
                model.setSelected(false);
            }
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }
    public void selectAll() {
        for (CallModel call : callModelArrayList) {
            if (!call.isHeader()) {
                call.setSelected(true);
            }
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedCallList().size());
        }
    }


}
