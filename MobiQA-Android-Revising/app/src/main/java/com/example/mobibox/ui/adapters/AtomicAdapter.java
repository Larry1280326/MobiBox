
package com.example.mobibox.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobibox.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AtomicAdapter extends RecyclerView.Adapter<AtomicAdapter.ViewHolder>
        implements ItemMoveCallback.ItemTouchHelperContract {

    private List<String> activities;
    private Context context;
    private String category;
    private boolean isCustom;
    private OnActivityClickListener listener;

    // constructor
    public AtomicAdapter(List<String> activities, Context context, String category, boolean isCustom,
            OnActivityClickListener listener) {
        this.activities = activities != null ? activities : new ArrayList<>();
        this.context = context;
        this.category = category;
        this.isCustom = isCustom;
        this.listener = listener;
    }

    // 创建视图
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_atomic_activity_simple, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(activities.get(position));
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    public void updateData(List<String> newData) {
        activities = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addItem(String activity) {
        if (activity != null && !activity.trim().isEmpty()) {
            activities.add(activity);
            notifyItemInserted(activities.size() - 1);
        }
    }

    public List<String> getActivities() {
        return new ArrayList<>(activities);
    }

    public String getActivityAt(int position) {
        return (position >= 0 && position < activities.size()) ? activities.get(position) : "";
    }

    public void editItem(int position, String newValue) {
        if (position >= 0 && position < activities.size() && newValue != null) {
            activities.set(position, newValue);
            notifyItemChanged(position);
        }
    }

    public void removeItem(int position) {
        if (position >= 0 && position < activities.size()) {
            activities.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, activities.size());
        }
    }

    // 实现 ItemTouchHelperContract 接口
    // AtomicAdapter.java
    // AtomicAdapter.java
    @Override
    public void onRowMoved(int fromPosition, int toPosition) {
        // 确保位置有效
        if (fromPosition >= 0 && fromPosition < activities.size() && toPosition >= 0
                && toPosition < activities.size()) {
            // 先移除，再添加到新位置，这是处理拖动排序更通用的方法
            String item = activities.remove(fromPosition);
            activities.add(toPosition, item);
            notifyItemMoved(fromPosition, toPosition);
        }
    }

    @Override
    public void onRowSwiped(int position) {
        removeItem(position);
    }

    public interface OnActivityClickListener {
        void onActivityClick(int position, String category, boolean isCustom);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        private android.os.Handler longPressHandler = new android.os.Handler();
        private Runnable longPressRunnable;
        private boolean isLongPressTriggered = false;
        private float initialX = 0;
        private float initialY = 0;
        private boolean hasMoved = false;
        private static final float MOVE_THRESHOLD = 10; // 移动阈值（像素）

        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textViewActivity);

            // 短按点击事件
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null && !isLongPressTriggered) {
                    listener.onActivityClick(position, category, isCustom);
                }
                isLongPressTriggered = false;
            });

            // 监听触摸事件，实现"长按不动3秒Delete，移动则拖拽"
            itemView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        // 记录初始位置
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        hasMoved = false;
                        isLongPressTriggered = false;

                        // 启动3秒后的Delete任务
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            longPressRunnable = () -> {
                                // 3秒后检查是否移动
                                if (!hasMoved) {
                                    isLongPressTriggered = true;
                                    // 没有移动，显示Delete对话框
                                    new android.app.AlertDialog.Builder(context)
                                            .setTitle("Delete Confirmation")
                                            .setMessage("Are you sure you want to delete this atomic activity?")
                                            .setPositiveButton("Delete", (dialog, which) -> {
                                                removeItem(position);
                                                android.widget.Toast
                                                        .makeText(context, "活动已Delete", android.widget.Toast.LENGTH_SHORT)
                                                        .show();
                                            })
                                            .setNegativeButton("Cancel", (dialog, which) -> {
                                                isLongPressTriggered = false;
                                            })
                                            .setOnCancelListener(dialog -> {
                                                isLongPressTriggered = false;
                                            })
                                            .show();
                                }
                            };
                            longPressHandler.postDelayed(longPressRunnable, 3000);
                        }
                        break;

                    case android.view.MotionEvent.ACTION_MOVE:
                        // 计算移动距离
                        float deltaX = Math.abs(event.getRawX() - initialX);
                        float deltaY = Math.abs(event.getRawY() - initialY);

                        // 如果移动超过阈值，标记为已移动并CancelDelete任务
                        if (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                            hasMoved = true;
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                        }
                        break;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // 松手或Cancel时，CancelDelete任务
                        if (longPressRunnable != null) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                        }
                        break;
                }
                return false; // 返回false让ItemTouchHelper处理拖拽
            });
        }
    }
}
