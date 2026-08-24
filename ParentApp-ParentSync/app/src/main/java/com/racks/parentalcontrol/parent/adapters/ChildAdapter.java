package com.racks.parentalcontrol.parent.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.models.ChildDetailModel;

import java.util.ArrayList;

public class ChildAdapter extends RecyclerView.Adapter<ChildAdapter.ChildViewHolder> {

    private final ArrayList<ChildDetailModel> childList;
    private OnChildClickListener listener;

    public ChildAdapter(ArrayList<ChildDetailModel> childList, OnChildClickListener listener) {
        this.childList = childList;
        this.listener = listener;
    }
    public interface OnChildClickListener {
        void onChildClick(ChildDetailModel child);
    }

    @NonNull
    @Override
    public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_paired_child, parent, false);
        return new ChildViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
        ChildDetailModel childDetail = childList.get(position);
        holder.item_device_name.setText("Device: "+childDetail.getDevice_model());
        if (childDetail.getName() == null) {
            holder.item_child_name.setText("Child: Unknown");
        }else{
        holder.item_child_name.setText("Child: "+childDetail.getName());
        }
        if (childDetail.getLast_online().equals("online")){
            holder.view_green_dot.setVisibility(View.VISIBLE);
        }else{
            holder.view_green_dot.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(view -> {
            listener.onChildClick(childDetail);
        });
    }

    @Override
    public int getItemCount() {
        return childList.size();
    }

    public static class ChildViewHolder extends RecyclerView.ViewHolder {
        TextView item_device_name, item_child_name;
        View view_green_dot;

        public ChildViewHolder(@NonNull View itemView) {
            super(itemView);
            item_device_name = itemView.findViewById(R.id.item_device_name);
            item_child_name = itemView.findViewById(R.id.item_child_name);
            view_green_dot = itemView.findViewById(R.id.view_green_dot);
        }
    }
}

