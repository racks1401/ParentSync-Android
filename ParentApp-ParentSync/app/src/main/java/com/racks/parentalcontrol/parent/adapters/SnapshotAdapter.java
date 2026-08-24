package com.racks.parentalcontrol.parent.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.fragments.FullScreenSnapFragment;
import com.racks.parentalcontrol.parent.fragments.HomeFragmentDirections;
import com.racks.parentalcontrol.parent.fragments.SnapshotFragment;
import com.racks.parentalcontrol.parent.fragments.SnapshotFragmentDirections;
import com.racks.parentalcontrol.parent.models.CallModel;
import com.racks.parentalcontrol.parent.models.NotificationModel;
import com.racks.parentalcontrol.parent.models.SnapshotModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SnapshotAdapter extends RecyclerView.Adapter<SnapshotAdapter.mySnapshotHolder>{
    private final ArrayList<SnapshotModel> snapshotModelArrayList;
    private final Context context;
    private OnSnapClickListener listener;
    private SelectionListener selectionListener;
    public interface OnSnapClickListener {
        void onSnapClick(String snapUrl);
    }
    public interface SelectionListener{
        void onSelectionChanged(int selectedCount);
    }


    public SnapshotAdapter(ArrayList<SnapshotModel> snapshotModelArrayList, Context context, OnSnapClickListener listener) {
        this.snapshotModelArrayList = snapshotModelArrayList;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public mySnapshotHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_snapshot, parent,false);
        return new mySnapshotHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull mySnapshotHolder holder, int position) {
        SnapshotModel snapshotModel = snapshotModelArrayList.get(position);
        holder.tv_snap_upload_time.setText(getUploadTimeString(snapshotModel.getUpload_time()));
        Glide.with(context)
                .load(snapshotModel.getSnap_url())
                .thumbnail(
                        Glide.with(context).load(snapshotModel.getSnap_url()).override(200, 200)
                )
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.img_snap_list);

        holder.itemView.setOnLongClickListener(view -> {
            selectItems(position);
            return true;
        });
        holder.itemView.setOnClickListener(view -> {
            if (isSelectionMode()){
                selectItems(position);
            }else{
                listener.onSnapClick(snapshotModel.getSnap_url());
            }

        });
        int color = snapshotModel.isSelected()
                ? ContextCompat.getColor(context, R.color.selection_color)
                : ContextCompat.getColor(context, android.R.color.transparent);

        holder.img_snap_list.setForeground(new ColorDrawable(color));
    }

    private void selectItems(int position) {
        SnapshotModel snapshotModel = snapshotModelArrayList.get(position);
        snapshotModel.setSelected(!snapshotModel.isSelected());
        notifyItemChanged(position);
        if (selectionListener != null) {
            int count = getSelectedSnapshots().size();
            selectionListener.onSelectionChanged(count);
        }
    }
    private boolean isSelectionMode() {
        for (SnapshotModel model : snapshotModelArrayList) {
            if (model.isSelected()) return true;
        }
        return false;
    }

    public ArrayList<SnapshotModel> getSelectedSnapshots() {
        ArrayList<SnapshotModel> selected = new ArrayList<>();
        for (SnapshotModel model : snapshotModelArrayList) {
            if (model.isSelected()) selected.add(model);
        }
        return selected;
    }

    @Override
    public int getItemCount() {
        return snapshotModelArrayList.size();
    }

    public static class mySnapshotHolder extends RecyclerView.ViewHolder {
        ImageView img_snap_list;
        TextView tv_snap_upload_time;
        public mySnapshotHolder(@NonNull View itemView) {
            super(itemView);
            img_snap_list = itemView.findViewById(R.id.img_snap_list);
            tv_snap_upload_time = itemView.findViewById(R.id.tv_snap_upload_time);
        }
    }
    public String getUploadTimeString(Long timestampMillis) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a, dd MMM yy", Locale.getDefault());
            return sdf.format(new Date(timestampMillis));
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }
    public void setSelectionListener(SelectionListener listener){
        this.selectionListener = listener;
    }
    public void clearSelection() {
        for (SnapshotModel model : snapshotModelArrayList) {
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
        for (SnapshotModel call : snapshotModelArrayList) {
            if (!call.isSelected()) {
                call.setSelected(true);
            }
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedSnapshots().size());
        }
    }
}
