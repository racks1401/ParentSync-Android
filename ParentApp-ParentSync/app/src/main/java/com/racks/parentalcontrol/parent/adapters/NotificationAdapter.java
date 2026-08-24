package com.racks.parentalcontrol.parent.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.models.CallModel;
import com.racks.parentalcontrol.parent.models.NotificationModel;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.MyNotificationHolder>{

    private Context context;
    private ArrayList<NotificationModel> notificationModelArrayList;
    private int expandedPosition = -1;

    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }
    private SelectionListener selectionListener;

    public NotificationAdapter(Context context, ArrayList<NotificationModel> notificationModelArrayList) {
        this.context = context;
        this.notificationModelArrayList = notificationModelArrayList;
    }

    @NonNull
    @Override
    public MyNotificationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_notification, parent, false);
        return new MyNotificationHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyNotificationHolder holder, int position) {
        NotificationModel notificationModel = notificationModelArrayList.get(position);
        holder.tv_app_name.setText(notificationModel.getApp_name());
        holder.tv_notification_time.setText(getFormatedDateTime(notificationModel.getTimestamp()));
        holder.tv_notification_title.setText(notificationModel.getTitle());
        setTextOrHide(holder.tv_notification_sub_text, notificationModel.getSub_text());
        setTextOrHide(holder.tv_notification_summary_text, notificationModel.getSummary_text());

        String text = notificationModel.getText();
        String bigText = notificationModel.getBig_text();

        if (text != null && text.equals(bigText)) {
            holder.tv_notification_text.setText(text);
            holder.tv_notification_text.setVisibility(View.VISIBLE);
            holder.tv_notification_big_text.setVisibility(View.GONE);
        }
        else if (text != null && bigText != null) {
            holder.tv_notification_text.setText(text);
            holder.tv_notification_big_text.setText(bigText);
            holder.tv_notification_text.setVisibility(View.VISIBLE);
            holder.tv_notification_big_text.setVisibility(View.VISIBLE);
        }
        else if (text != null) {
            holder.tv_notification_text.setText(text);
            holder.tv_notification_text.setVisibility(View.VISIBLE);
            holder.tv_notification_big_text.setVisibility(View.GONE);
        }
        else if (bigText != null) {
            holder.tv_notification_big_text.setText(bigText);
            holder.tv_notification_big_text.setVisibility(View.VISIBLE);
            holder.tv_notification_text.setVisibility(View.GONE);
        }
        else {
            holder.tv_notification_text.setVisibility(View.GONE);
            holder.tv_notification_big_text.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode()) {
                toggleSelection(position);
            } else {
                int adapterPosition = holder.getAdapterPosition();
                int previousPosition = expandedPosition;

                if (expandedPosition == adapterPosition) {
                    expandedPosition = -1;

                } else {
                    expandedPosition = adapterPosition;
                }
                notifyItemChanged(previousPosition);
                notifyItemChanged(adapterPosition);
            }
        });

        if (position == expandedPosition) {
            TransitionManager.beginDelayedTransition((ViewGroup) holder.itemView);
            holder.tv_notification_text.setMaxLines(Integer.MAX_VALUE);
            holder.ll_expandable_view.setVisibility(View.VISIBLE);
        } else {
            holder.tv_notification_text.setMaxLines(2);
            holder.ll_expandable_view.setVisibility(View.GONE);
        }
        int color = notificationModel.isSelected()
                ? ContextCompat.getColor(context, R.color.selection_color)
                : ContextCompat.getColor(context, android.R.color.transparent);

        ViewCompat.setBackgroundTintList(holder.item_view, ColorStateList.valueOf(color));
    }

    @Override
    public int getItemCount() {
        return notificationModelArrayList.size();
    }

    public static class MyNotificationHolder extends RecyclerView.ViewHolder {
        TextView tv_app_name, tv_notification_time, tv_notification_text, tv_notification_title, tv_notification_big_text,
        tv_notification_summary_text, tv_notification_sub_text;
        LinearLayout ll_expandable_view;
        MaterialCardView item_view;
        public MyNotificationHolder(@NonNull View itemView) {
            super(itemView);
            tv_app_name = itemView.findViewById(R.id.tv_app_name);
            tv_notification_time = itemView.findViewById(R.id.tv_notification_time);
            tv_notification_title = itemView.findViewById(R.id.tv_notification_title);
            tv_notification_text = itemView.findViewById(R.id.tv_notification_text);
            tv_notification_big_text = itemView.findViewById(R.id.tv_notification_big_text);
            tv_notification_summary_text = itemView.findViewById(R.id.tv_notification_summary_text);
            tv_notification_sub_text = itemView.findViewById(R.id.tv_notification_sub_text);
            ll_expandable_view = itemView.findViewById(R.id.ll_expandable_view);
            item_view = itemView.findViewById(R.id.item_view);
        }
    }
    public String getFormatedDateTime(long dateMillis){
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(dateMillis));
    }

    private void setTextOrHide(TextView textView, String text) {
        if (text != null && !text.trim().isEmpty()) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(text.trim());
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    private boolean isSelectionMode() {
        for (NotificationModel model : notificationModelArrayList) {
            if (model.isSelected()) return true;
        }
        return false;
    }

    private void toggleSelection(int position) {
        NotificationModel model = notificationModelArrayList.get(position);
        model.setSelected(!model.isSelected());
        notifyItemChanged(position);

        if (selectionListener != null) {
            int count = getSelectedNotifications().size();
            selectionListener.onSelectionChanged(count);
        }
    }

    public ArrayList<NotificationModel> getSelectedNotifications() {
        ArrayList<NotificationModel> selected = new ArrayList<>();
        for (NotificationModel model : notificationModelArrayList) {
            if (model.isSelected()) selected.add(model);
        }
        return selected;
    }
    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void selectAll() {
        for (NotificationModel notificationModel : notificationModelArrayList) {
            notificationModel.setSelected(true);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedNotifications().size());
        }
    }

    public void clearSelection() {
        for (NotificationModel notificationModel : notificationModelArrayList) {
            if (notificationModel.isSelected()) {
                notificationModel.setSelected(false);
            }
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }
}
